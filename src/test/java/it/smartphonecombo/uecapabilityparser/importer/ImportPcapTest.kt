package it.smartphonecombo.uecapabilityparser.importer

import it.smartphonecombo.uecapabilityparser.extension.toInputSource
import it.smartphonecombo.uecapabilityparser.importer.multi.ImportPcap
import it.smartphonecombo.uecapabilityparser.model.Capabilities
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ImportPcapTest {
    private val path = "src/test/resources/pcap/"

    // Only utra
    @Test
    fun testPduUtra() {
        testPcap(
            "$path/input/exportedPduUtra.pcap",
            "$path/oracle/exportedPduUtra-capabilities.json"
        )
    }

    // One eutra
    @Test
    fun testPduEutra() {
        testPcap(
            "$path/input/exportedPduEutra.pcap",
            "$path/oracle/exportedPduEutra-capabilities.json"
        )
    }

    // different filters, one duplicate
    @Test
    fun testPduEutraDifferentFilters() {
        testPcap(
            "$path/input/exportedPduEutraDifferentFilters.pcap",
            "$path/oracle/exportedPduEutraDifferentFilters-capabilities.json"
        )
    }

    // nr rrc + lte rrc
    @Test
    fun testPDUNrRrcLteRrc() {
        testPcap(
            "$path/input/exportedPduNrRrcLteRrc.pcap",
            "$path/oracle/exportedPduNrRrcLteRrc-capabilities.json"
        )
    }

    // different MRDC capabilities all split
    @Test
    fun testPduMrdcFullSplit() {
        testPcap(
            "$path/input/exportedPduMrdcFullSplit.pcap",
            "$path/oracle/exportedPduMrdcFullSplit-capabilities.json"
        )
    }

    // different capabilities mixed split
    @Test
    fun testPduMrdcMixedSplit() {
        testPcap(
            "$path/input/exportedPduMrdcMixedSplit.pcap",
            "$path/oracle/exportedPduMrdcMixedSplit-capabilities.json"
        )
    }

    // SIM 1: 127.0.0.1, SIM 2: 127.0.0.2
    @Test
    fun testGsmTapDualSim() {
        testPcap("$path/input/gsmTapDualSim.pcap", "$path/oracle/gsmTapDualSim-capabilities.json")
    }

    // SIM 1: 127.0.0.1, SIM 2: 127.0.0.1
    @Test
    fun testGsmTapDualSimSameIp() {
        testPcap(
            "$path/input/gsmTapDualSimSameIP.pcap",
            "$path/oracle/gsmTapDualSimSameIP-capabilities.json"
        )
    }

    // many capabilities
    @Test
    fun testGsmTapComplex() {
        testPcap("$path/input/gsmTapComplex.pcap", "$path/oracle/gsmTapComplex-capabilities.json")
    }

    // eutra + nr/eutra-nr, no 0xB826
    @Test
    fun testGsmTapMrdcSemiSplit() {
        testPcap(
            "$path/input/gsmTapMrdcSemiSplit.pcap",
            "$path/oracle/gsmTapMrdcSemiSplit-capabilities.json"
        )
    }

    // shannon
    @Test
    fun testGsmTapShannon() {
        testPcap("$path/input/gsmTapShannon.pcap", "$path/oracle/gsmTapShannon-capabilities.json")
    }

    // 0xB826 + spurious data
    @Test
    fun testGsmTapSpuriousData() {
        testPcap(
            "$path/input/gsmTapSpuriousData.pcap",
            "$path/oracle/gsmTapSpuriousData-capabilities.json"
        )
    }

    // NGAP radio cap + S1AP radio cap + spurious data
    @Test
    fun testSCTP() {
        testPcap("$path/input/sctpS1apNgap.pcap", "$path/oracle/sctpS1apNgap-capabilities.json")
    }

    // GSMTAPv3 Draft/proposal
    @Test
    fun testGsmTapV3() {
        testPcap("$path/input/gsmTapV3.pcap", "$path/oracle/gsmTapV3.json")
    }

    // GSMTAPv3 Draft/proposal  NR ue capability segmented
    @Test
    fun testGsmTapV3NrRrcSegmented() {
        testPcap(
            "$path/input/gsmTapV3NrRrcSegmented.pcap",
            "$path/oracle/gsmTapV3NrRrcSegmented.json"
        )
    }

    // GSMTAPv2 MRDC ue capability segmented
    @Test
    fun testGsmTapMrdcSegmented() {
        testPcap("$path/input/gsmTapMrdcSegmented.pcap", "$path/oracle/gsmTapMrdcSegmented.json")
    }

    // Exported PDU MRDC ue capability segmented
    @Test
    fun testPduSegmented() {
        testPcap(
            "$path/input/exportedPduMrdcSegmented.pcap",
            "$path/oracle/exportedPduMrdcSegmented.json"
        )
    }

    private fun testPcap(path: String, oracle: String) {
        val multi = ImportPcap.parse(File(path).toInputSource())

        val actual = multi?.parsingList?.map { it.capabilities }!!

        val expected = Json.decodeFromString<List<Capabilities>>(File(oracle).readText())

        // Check size
        Assertions.assertEquals(expected.size, actual.size)

        // override dynamic properties
        for (i in expected.indices) {
            val capA = actual[i]
            val capE = expected[i]

            capE.setMetadata("processingTime", capA.getStringMetadata("processingTime") ?: "")
        }

        Assertions.assertEquals(expected, actual)
    }
}
