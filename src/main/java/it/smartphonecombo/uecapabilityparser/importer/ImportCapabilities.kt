package it.smartphonecombo.uecapabilityparser.importer

import it.smartphonecombo.uecapabilityparser.Config
import it.smartphonecombo.uecapabilityparser.bean.Capabilities

/** The Interface ImportCA. */
interface ImportCapabilities {
    val debug
        get() = Config.getOrDefault("debug", "false").toBoolean()

    /**
     * Convert to java class.
     *
     * @param caBandCombosString the ca band combos string
     * @return the combo list
     */
    fun parse(caBandCombosString: String): Capabilities
}
