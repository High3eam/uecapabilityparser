package it.smartphonecombo.uecapabilityparser.model.index

import it.smartphonecombo.uecapabilityparser.extension.custom
import it.smartphonecombo.uecapabilityparser.extension.decodeFromInputSource
import it.smartphonecombo.uecapabilityparser.extension.forAsync
import it.smartphonecombo.uecapabilityparser.extension.mapAsync
import it.smartphonecombo.uecapabilityparser.io.Custom
import it.smartphonecombo.uecapabilityparser.io.IOUtils
import it.smartphonecombo.uecapabilityparser.io.IOUtils.createDirectories
import it.smartphonecombo.uecapabilityparser.io.IOUtils.echoSafe
import it.smartphonecombo.uecapabilityparser.model.Capabilities
import it.smartphonecombo.uecapabilityparser.query.Query
import it.smartphonecombo.uecapabilityparser.util.LruCache
import it.smartphonecombo.uecapabilityparser.util.optimize
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
@SerialName("LibraryIndex")
data class LibraryIndexImmutable(
    @Required val items: List<IndexLine>,
    val multiItems: List<MultiIndexLine> = emptyList(),
) {
    val jsonString by lazy { Json.custom().encodeToString(this) }
}

class LibraryIndex(outputCacheSize: Int?) {
    val reparsingIndex: ReparsingIndex = ReparsingIndex()
    private val items: MutableMap<String, IndexLine> = mutableMapOf()
    private val multiItems: MutableMap<String, MultiIndexLine> = mutableMapOf()
    private val outputCache = LruCache<String, Capabilities>(outputCacheSize)
    private var cachedImmutable: LibraryIndexImmutable? = null
    private val lock = Any()

    fun putLine(line: IndexLine) {
        synchronized(lock) {
            items[line.id] = line
            outputCache.remove(line.id)
            cachedImmutable = null
        }
    }

    fun putMultiLine(line: MultiIndexLine) {
        synchronized(lock) {
            multiItems[line.id] = line
            cachedImmutable = null
        }
    }

    fun find(id: String): IndexLine? = items[id]

    fun findMulti(id: String): MultiIndexLine? = multiItems[id]

    fun findByInput(id: String): IndexLine? {
        return getAll().find { item -> item.inputs.any { it == id } }
    }

    /** return an immutable list of all single-capability indexes */
    fun getAll() = toImmutableIndex().items

    /** return an immutable list of all multi-capability indexes */
    fun getMultiIndexes() = toImmutableIndex().multiItems

    fun getOutput(id: String, libraryPath: String, cacheIfFull: Boolean = true): Capabilities? {
        val cached = outputCache[id]
        if (cached != null) return cached

        val indexLine = find(id) ?: return null
        val compressed = indexLine.compressed
        val filePath = "$libraryPath/output/$id.json"
        val text = IOUtils.getInputSource(filePath, compressed) ?: return null
        val res = Json.custom().decodeFromInputSource<Capabilities>(text)
        if (outputCache.put(id, res, !cacheIfFull)) res.optimize()
        return res
    }

    fun filterByQuery(query: Query, libraryPath: String): LibraryIndexImmutable {
        // immutable copies of items and multi items
        val immutable = toImmutableIndex()
        val multiItemsList = immutable.multiItems
        val itemsList = immutable.items

        val validIdsDeferred =
            CoroutineScope(Dispatchers.Custom).async {
                itemsList.mapAsync { cap ->
                    val res = getOutput(cap.id, libraryPath, false)?.let { query.evaluateQuery(it) }
                    if (res == true) cap.id else null
                }
            }

        val validIds = runBlocking { validIdsDeferred.await().filterNotNull().toSet() }

        val multiItemsFiltered =
            multiItemsList.filter { it.indexLineIds.any { id -> validIds.contains(id) } }
        val itemsFiltered = itemsList.filter { validIds.contains(it.id) }

        return LibraryIndexImmutable(itemsFiltered, multiItemsFiltered)
    }

    fun toImmutableIndex(): LibraryIndexImmutable {
        // check if we've a cached valid value
        cachedImmutable?.let {
            return it
        }

        synchronized(lock) {
            val items = items.values.toTypedArray()
            val multiItems = multiItems.values.toTypedArray()

            // stable sorting
            items.sortWith(compareBy<IndexLine>({ it.timestamp }, { it.id }).reversed())
            multiItems.sortWith(compareBy<MultiIndexLine>({ it.timestamp }, { it.id }).reversed())

            val newImmutableIndex = LibraryIndexImmutable(items.toList(), multiItems.toList())
            cachedImmutable = newImmutableIndex

            return newImmutableIndex
        }
    }

    suspend fun populateIndexAsync(path: String) {
        val outputDir = "$path/output"
        val inputDir = "$path/input"
        val multiDir = "$path/multi"
        val reparsingIndexFile = "$path/reparsing.json"

        // Create directories if they don't exist
        arrayOf(outputDir, inputDir, multiDir).forEach { createDirectories(it) }
        // Read all files
        val inputFiles = File(inputDir).listFiles()?.sorted() ?: emptyList()
        val outputFiles = File(outputDir).listFiles()?.toList() ?: emptyList()
        val multiFiles = File(multiDir).listFiles()?.toList() ?: emptyList()

        val jobs = mutableListOf<Job>()

        withContext(Dispatchers.Custom) {
            val outputJobs =
                outputFiles.forAsync {
                    try {
                        val newIndex = IndexLine.fromFile(it, inputFiles)
                        putLine(newIndex)
                    } catch (ex: Exception) {
                        echoSafe("Error reading $it: $ex", true)
                    }
                }
            jobs.addAll(outputJobs)

            val multiJobs =
                multiFiles.forAsync {
                    try {
                        val newMultiIndex = MultiIndexLine.fromFile(it)
                        putMultiLine(newMultiIndex)
                    } catch (ex: Exception) {
                        echoSafe("Error reading $it: $ex", true)
                    }
                }
            jobs.addAll(multiJobs)
        }

        // read reparsingIndex
        ReparsingIndex.fromFile(reparsingIndexFile).getAll().let { reparsingIndex.putAll(it) }

        joinAll(*jobs.toTypedArray())
    }
}
