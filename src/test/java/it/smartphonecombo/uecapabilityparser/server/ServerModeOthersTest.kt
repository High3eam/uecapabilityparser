package it.smartphonecombo.uecapabilityparser.server

import io.javalin.http.HttpStatus
import io.javalin.testtools.JavalinTest
import it.smartphonecombo.uecapabilityparser.UtilityForTests.scatAvailable
import it.smartphonecombo.uecapabilityparser.model.LogType
import it.smartphonecombo.uecapabilityparser.query.SearchableField
import it.smartphonecombo.uecapabilityparser.util.Config
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ServerModeOthersTest {

    private val parserVersion = Config.getOrDefault("project.version", "")
    private val openapi =
        {}.javaClass.getResourceAsStream("/swagger/openapi.json")?.reader()?.readText() ?: ""

    private val endpoints =
        listOf(
            "/swagger",
            "/openapi",
            "/swagger/openapi.json",
            "custom.js",
            "custom.css",
            "/parse/multiPart",
            "/csv",
            "/store/list",
            "/store/getItem",
            "/store/getMultiItem",
            "/store/getOutput",
            "/store/getMultiOutput",
            "/store/getInput",
            "/store/list/filtered",
            "/status",
        )

    private val scatTypes = arrayOf("HDF", "SDM", "DLF", "QMDL")
    private val logTypes =
        listOf(
                "H",
                "W",
                "N",
                "C",
                "CNR",
                "E",
                "Q",
                "QLTE",
                "QNR",
                "M",
                "O",
                "QC",
                "T",
                "A",
                "RF",
                "SHLTE",
                "SHNR",
                "P",
                "NSG",
                "DLF",
                "QMDL",
                "HDF",
                "SDM",
            )
            .filter { scatAvailable || it !in scatTypes }
            .map(LogType::of)

    private val searchableFields = SearchableField.getAllSearchableFields()

    @AfterEach
    fun tearDown() {
        Config.clear()
    }

    @Test
    fun testOpenApi() {
        val endpoint = arrayOf("/openapi", "/openapi/").random()
        getTest(endpoint, Json.parseToJsonElement(openapi))
    }

    @Test
    fun testStoreSwaggerOpenApi() {
        getTest("/swagger/openapi.json", Json.parseToJsonElement(openapi))
    }

    @Test
    fun testStatusStoreOff() {
        val endpointsNoStore =
            endpoints.filterNot { it.startsWith("/store") && !it.endsWith("status") }
        val status =
            ServerStatus(parserVersion, endpointsNoStore, logTypes, 256000000, searchableFields)
        getTest("/status", Json.encodeToJsonElement(status))
    }

    @Test
    fun testCustomCssOff() {
        getTestText("/custom.css", "")
    }

    @Test
    fun testCustomJsOff() {
        getTestText("/custom.js", "")
    }

    @Test
    fun testCustomCssOn() {
        Config["customCss"] = "src/test/resources/server/inputForOthers/custom.css"
        getTestText("/custom.css", ".max-w-7xl { max-width: 136rem !important; }\n")
    }

    @Test
    fun testCustomJsOn() {
        Config["customJs"] = "src/test/resources/server/inputForOthers/custom.js"
        getTestText("/custom.js", "alert(\"hello all\")\n")
    }

    private fun getTest(url: String, oracle: JsonElement) {
        JavalinTest.test(JavalinApp().newServer()) { _, client ->
            val response = client.get(url)
            Assertions.assertEquals(HttpStatus.OK.code, response.code)
            val actualText = response.body?.string() ?: ""
            val actual = Json.parseToJsonElement(actualText)
            Assertions.assertEquals(oracle.jsonObject, actual.jsonObject)
        }
    }

    private fun getTestText(url: String, oracle: String) {
        JavalinTest.test(JavalinApp().newServer()) { _, client ->
            val response = client.get(url)
            Assertions.assertEquals(HttpStatus.OK.code, response.code)
            val actualText = response.body?.string() ?: ""
            Assertions.assertEquals(oracle, actualText)
        }
    }
}
