package it.smartphonecombo.uecapabilityparser.model.band

import it.smartphonecombo.uecapabilityparser.extension.Band
import it.smartphonecombo.uecapabilityparser.model.EmptyMimo
import it.smartphonecombo.uecapabilityparser.model.Mimo
import it.smartphonecombo.uecapabilityparser.model.PowerClass
import it.smartphonecombo.uecapabilityparser.model.bandwidth.BwsNr
import it.smartphonecombo.uecapabilityparser.model.modulation.EmptyModulation
import it.smartphonecombo.uecapabilityparser.model.modulation.Modulation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BandNrDetails(
    @SerialName("band") override var band: Band,
    @SerialName("mimoDl") override var mimoDL: Mimo = EmptyMimo,
    @SerialName("mimoUl") override var mimoUL: Mimo = EmptyMimo,
    @SerialName("modulationDl") override var modDL: Modulation = EmptyModulation,
    @SerialName("modulationUl") override var modUL: Modulation = EmptyModulation,
    @SerialName("maxUplinkDutyCycle") var maxUplinkDutyCycle: Int = 0,
    @SerialName("powerClass") override var powerClass: PowerClass = PowerClass.NONE,
    @SerialName("bandwidths") var bandwidths: List<BwsNr> = emptyList(),
    @SerialName("rateMatchingLteCrs") var rateMatchingLteCrs: Boolean = false,
) : IBandDetails {

    fun bwsToString(): String {
        val dlString =
            bandwidths
                .filter { it.bwsDL.isNotEmpty() }
                .joinToString(
                    prefix = "BwDL:[",
                    postfix = "]",
                    transform = { "${it.scs}kHz: ${it.bwsDL.joinToString()}" },
                    separator = "; ",
                )
        val ulString =
            bandwidths
                .filter { it.bwsUL.isNotEmpty() }
                .joinToString(
                    prefix = "BwUL:[",
                    postfix = "]",
                    transform = { "${it.scs}kHz: ${it.bwsUL.joinToString()}" },
                    separator = "; ",
                )
        return "n$band $dlString $ulString"
    }

    fun bw90MHzSupported(): Boolean {
        return bandwidths.any { bwsNr -> bwsNr.bwsDL.contains(90) || bwsNr.bwsUL.contains(90) }
    }

    val isFR2: Boolean
        get() = band > 256
}
