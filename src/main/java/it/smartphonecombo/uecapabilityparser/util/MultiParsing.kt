package it.smartphonecombo.uecapabilityparser.util

import it.smartphonecombo.uecapabilityparser.extension.asArrayOrNull
import it.smartphonecombo.uecapabilityparser.extension.commonPrefix
import it.smartphonecombo.uecapabilityparser.extension.custom
import it.smartphonecombo.uecapabilityparser.extension.getString
import it.smartphonecombo.uecapabilityparser.extension.getStringList
import it.smartphonecombo.uecapabilityparser.extension.mutableListWithCapacity
import it.smartphonecombo.uecapabilityparser.model.MultiCapabilities
import it.smartphonecombo.uecapabilityparser.model.index.IndexLine
import it.smartphonecombo.uecapabilityparser.model.index.LibraryIndex
import it.smartphonecombo.uecapabilityparser.model.index.MultiIndexLine
import java.time.Instant
import java.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class MultiParsing(
    private val inputsList: List<List<ByteArray>>,
    private val typeList: List<String>,
    private val subTypesList: List<List<String>>,
    private val descriptionList: List<String> = emptyList(),
    private val jsonFormat: Json = Json,
    private val description: String = "",
    private var id: String = UUID.randomUUID().toString()
) {
    val parsingList = parseCapabilities()

    fun getMultiCapabilities(): MultiCapabilities {
        val capabilities = parsingList.map { it.capabilities }
        return MultiCapabilities(capabilities, description, id)
    }

    private fun parseCapabilities(): List<Parsing> {
        val parsedCapabilities = mutableListOf<Parsing>()
        val subTypeIterator = subTypesList.iterator()

        for (i in inputsList.indices) {
            val inputs = inputsList[i]
            val type = typeList[i]
            var inputArray = ByteArray(0)
            var inputENDCArray: ByteArray? = null
            var inputNRArray: ByteArray? = null
            var defaultNr = false

            if (type != "H") {
                inputArray = inputs.fold(inputArray) { acc, it -> acc + it }
            } else {
                val subTypes = subTypeIterator.next()
                for (j in inputs.indices) {
                    val subType = subTypes[j]
                    val input = inputs[j]
                    when (subType) {
                        "LTE" -> inputArray = input
                        "ENDC" -> inputENDCArray = input
                        "NR" -> inputNRArray = input
                    }
                }

                if (inputNRArray?.isNotEmpty() == true && inputArray.isEmpty()) {
                    inputArray = inputNRArray
                    defaultNr = true
                }
            }
            val description = descriptionList.getOrElse(i) { "" }

            val parsing =
                Parsing(
                    inputArray,
                    inputNRArray,
                    inputENDCArray,
                    defaultNr,
                    type,
                    description,
                    jsonFormat
                )

            parsedCapabilities.add(parsing)
        }
        return parsedCapabilities
    }

    fun store(libraryIndex: LibraryIndex, path: String, compression: Boolean): MultiIndexLine {
        val multiDir = "$path/multi"
        val outputPath = "$multiDir/$id.json"

        val indexLines = parsingList.map { it.store(libraryIndex, path, compression) }
        val indexLineIds = indexLines.map(IndexLine::id)
        val multiIndexLine =
            MultiIndexLine(
                id,
                timestamp = indexLines.lastOrNull()?.timestamp ?: Instant.now().toEpochMilli(),
                description,
                indexLineIds,
                compression
            )

        val encodedString = Json.custom().encodeToString(multiIndexLine)
        IO.outputFile(encodedString.toByteArray(), outputPath, compression)
        libraryIndex.addMultiLine(multiIndexLine)

        return multiIndexLine
    }

    companion object {
        fun fromJsonRequest(request: JsonElement): MultiParsing? {
            val base64decoder = Base64.getDecoder()

            val requestArray = request.asArrayOrNull() ?: return null
            val inputsList: MutableList<List<ByteArray>> =
                mutableListWithCapacity(requestArray.size)
            val typeList: MutableList<String> = mutableListWithCapacity(requestArray.size)
            val subTypesList: MutableList<List<String>> = mutableListWithCapacity(requestArray.size)
            val descriptionList: MutableList<String> = mutableListWithCapacity(requestArray.size)

            requestArray.forEach { req ->
                val inputs =
                    req.getStringList("inputs")?.map { base64 -> base64decoder.decode(base64) }
                val type = req.getString("type")
                val subTypes = req.getStringList("subTypes")
                val description = req.getString("description")

                if (inputs == null || type == null) {
                    return@forEach
                }

                inputsList.add(inputs)
                typeList.add(type)
                subTypesList.add(subTypes ?: emptyList())
                descriptionList.add(description?.trim() ?: "")
            }

            val uniqueDesc = descriptionList.distinct()
            val commonPrefix = uniqueDesc.commonPrefix(true).dropLastWhile { !it.isWhitespace() }
            val description =
                if (commonPrefix.length < 3) {
                    uniqueDesc.joinToString(" ")
                } else {
                    val trunc = uniqueDesc.joinToString(" ") { it.substring(commonPrefix.length) }
                    commonPrefix + trunc
                }

            return MultiParsing(
                inputsList,
                typeList,
                subTypesList,
                descriptionList,
                description = description
            )
        }
    }
}