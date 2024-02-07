package it.smartphonecombo.uecapabilityparser.server

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder
import io.javalin.config.SizeUnit
import io.javalin.http.ContentType
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import io.javalin.http.servlet.throwContentTooLargeIfContentTooLarge
import io.javalin.http.staticfiles.Location
import io.javalin.json.JsonMapper
import it.smartphonecombo.uecapabilityparser.extension.badRequest
import it.smartphonecombo.uecapabilityparser.extension.custom
import it.smartphonecombo.uecapabilityparser.extension.decodeFromInputSource
import it.smartphonecombo.uecapabilityparser.extension.internalError
import it.smartphonecombo.uecapabilityparser.extension.toInputSource
import it.smartphonecombo.uecapabilityparser.io.IOUtils
import it.smartphonecombo.uecapabilityparser.io.NullInputSource
import it.smartphonecombo.uecapabilityparser.model.Capabilities
import it.smartphonecombo.uecapabilityparser.model.index.IndexLine
import it.smartphonecombo.uecapabilityparser.model.index.LibraryIndex
import it.smartphonecombo.uecapabilityparser.util.Config
import it.smartphonecombo.uecapabilityparser.util.Parsing
import java.io.File
import java.io.InputStream
import java.lang.reflect.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class JavalinApp {
    private val jsonMapper =
        object : JsonMapper {
            override fun <T : Any> fromJsonString(json: String, targetType: Type): T {
                @Suppress("UNCHECKED_CAST")
                val deserializer = serializer(targetType) as KSerializer<T>
                return Json.custom().decodeFromString(deserializer, json)
            }

            override fun <T : Any> fromJsonStream(json: InputStream, targetType: Type): T {
                @Suppress("UNCHECKED_CAST")
                val deserializer = serializer(targetType) as KSerializer<T>
                return Json.custom().decodeFromStream(deserializer, json)
            }

            override fun toJsonString(obj: Any, type: Type): String {
                val serializer = serializer(obj.javaClass)
                return Json.custom().encodeToString(serializer, obj)
            }
        }
    private val hasSubmodules = {}.javaClass.getResourceAsStream("/web") != null
    private val html404 = {}.javaClass.getResourceAsStream("/web/404.html")?.use { it.readBytes() }
    private val endpoints = mutableListOf<String>()
    private val maxRequestSize = Config["maxRequestSize"]?.toLong() ?: (256 * 1000 * 1000)
    val app: Javalin =
        Javalin.create { config ->
            config.compression.gzipOnly(4)
            config.http.prefer405over404 = true

            // align all request size limits
            config.http.maxRequestSize = maxRequestSize
            config.jetty.multipartConfig.maxFileSize(maxRequestSize, SizeUnit.BYTES)
            config.jetty.multipartConfig.maxTotalRequestSize(maxRequestSize, SizeUnit.MB)
            config.jetty.multipartConfig.maxInMemoryFileSize(20, SizeUnit.MB)

            config.routing.treatMultipleSlashesAsSingleSlash = true
            config.jsonMapper(jsonMapper)
            config.plugins.enableCors { cors -> cors.add { it.anyHost() } }
            if (hasSubmodules) {
                config.staticFiles.add("/web", Location.CLASSPATH)
                config.staticFiles.add { staticFiles ->
                    staticFiles.hostedPath = "/swagger"
                    staticFiles.directory = "/swagger"
                    staticFiles.location = Location.CLASSPATH
                }
            }
        }

    init {
        val store = Config["store"]
        val compression = Config["compression"] == "true"
        val maxOutputCache = Config.getOrDefault("cache", "0").toInt().takeIf { it >= 0 }
        var index: LibraryIndex =
            store?.let { LibraryIndex.buildIndex(it, maxOutputCache) }
                ?: LibraryIndex(mutableListOf())

        val reparseStrategy = Config.getOrDefault("reparse", "off")
        if (store != null && reparseStrategy != "off") {
            CoroutineScope(Dispatchers.IO).launch {
                reparseLibrary(reparseStrategy, store, index, compression)
                // Rebuild index
                index = LibraryIndex.buildIndex(store, maxOutputCache)
            }
        }

        app.exception(Exception::class.java) { e, ctx ->
            e.printStackTrace()
            if (e is IllegalArgumentException || e is NullPointerException) {
                ctx.badRequest()
            } else {
                ctx.internalError()
            }
        }

        app.error(HttpStatus.NOT_FOUND) { ctx ->
            if (html404 != null) {
                ctx.contentType(ContentType.HTML)
                ctx.result(html404)
            }
        }
        app.routes {
            ApiBuilder.before { ctx -> ctx.throwContentTooLargeIfContentTooLarge() }

            if (hasSubmodules) {
                endpoints.add("/swagger")
                // Add / if missing
                ApiBuilder.before("/swagger") { ctx ->
                    if (!ctx.path().endsWith("/")) {
                        ctx.redirect("/swagger/")
                    }
                }
                apiBuilderGet("/openapi", "/swagger/openapi.json") { Routes.getOpenApi(it) }
            }

            // Add custom js and custom css
            addStaticGet("custom.js", Config["customJs"], ContentType.TEXT_JS)
            addStaticGet("custom.css", Config["customCss"], ContentType.TEXT_CSS)

            apiBuilderPost("/parse") { Routes.parse(it, store, index, compression) }
            apiBuilderPost("/parse/multiPart") {
                Routes.parseMultiPart(it, store, index, compression)
            }

            apiBuilderPost("/csv") { Routes.csv(it) }

            if (store != null) {
                apiBuilderGet("/store/list") { Routes.storeList(it, index) }
                apiBuilderGet("/store/getItem") { Routes.storeGetItem(it, index) }
                apiBuilderGet("/store/getMultiItem") { Routes.storeGetMultiItem(it, index) }
                apiBuilderGet("/store/getOutput") { Routes.storeGetOutput(it, index, store) }
                apiBuilderGet("/store/getMultiOutput") {
                    Routes.storeGetMultiOutput(it, index, store)
                }
                apiBuilderGet("/store/getInput") { Routes.storeGetInput(it, index, store) }
                apiBuilderPost("/store/list/filtered") {
                    Routes.storeListFiltered(it, index, store)
                }
            }

            apiBuilderGet("/status") { Routes.status(it, maxRequestSize, endpoints) }
        }
    }

    private suspend fun reparseLibrary(
        strategy: String,
        store: String,
        index: LibraryIndex,
        compression: Boolean
    ) {
        val parserVersion = Config.getOrDefault("project.version", "")
        val auto = strategy !== "force"
        val threadCount = minOf(Runtime.getRuntime().availableProcessors(), 2)
        val dispatcher = Dispatchers.IO.limitedParallelism(threadCount)

        withContext(dispatcher) {
            IOUtils.createDirectories("$store/backup/output/")
            IOUtils.createDirectories("$store/backup/input/")
            index
                .getAll()
                .filterNot { auto && it.parserVersion == parserVersion }
                .map { async { reparseItem(it, store, compression) } }
                .awaitAll()
        }
    }

    private fun reparseItem(indexLine: IndexLine, store: String, compression: Boolean) {
        try {
            val compressed = indexLine.compressed
            val capPath = "/output/${indexLine.id}.json"
            val capText =
                IOUtils.inputSourceAndMove("$store$capPath", "$store/backup$capPath", compressed)
                    ?: NullInputSource

            val capabilities = Json.custom().decodeFromInputSource<Capabilities>(capText)
            val inputMap =
                indexLine.inputs.mapNotNull {
                    IOUtils.inputSourceAndMove(
                        "$store/input/$it",
                        "$store/backup/input/$it",
                        compressed
                    )
                }

            val request =
                RequestParse.buildRequest(
                    *inputMap.toTypedArray(),
                    type = capabilities.logType,
                    description = indexLine.description,
                    defaultNR =
                        indexLine.defaultNR ||
                            capabilities.lteBands.isEmpty() && capabilities.nrBands.isNotEmpty()
                )

            Parsing.fromRequest(request)?.let {
                // Reset capabilities id and timestamp
                it.capabilities.id = capabilities.id
                it.capabilities.timestamp = capabilities.timestamp
                it.store(null, store, compression)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun apiBuilderGet(vararg paths: String, handler: Handler) {
        for (path in paths) {
            ApiBuilder.get(path, handler)
            endpoints.add(path)
        }
    }

    private fun addStaticGet(webPath: String, filePath: String?, contentType: ContentType) {
        val source =
            if (filePath.isNullOrEmpty()) {
                NullInputSource
            } else {
                File(filePath).toInputSource()
            }
        apiBuilderGet(webPath) { ctx ->
            ctx.result(source.inputStream())
            ctx.contentType(contentType)
        }
    }

    private fun apiBuilderPost(vararg paths: String, handler: Handler) {
        for (path in paths) {
            ApiBuilder.post(path, handler)
            endpoints.add(path)
        }
    }
}
