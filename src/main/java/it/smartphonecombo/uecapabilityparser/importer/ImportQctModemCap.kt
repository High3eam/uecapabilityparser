package it.smartphonecombo.uecapabilityparser.importer

import it.smartphonecombo.uecapabilityparser.extension.firstOrNull
import it.smartphonecombo.uecapabilityparser.extension.mutableListWithCapacity
import it.smartphonecombo.uecapabilityparser.io.InputSource
import it.smartphonecombo.uecapabilityparser.model.BwClass
import it.smartphonecombo.uecapabilityparser.model.Capabilities
import it.smartphonecombo.uecapabilityparser.model.EmptyMimo
import it.smartphonecombo.uecapabilityparser.model.combo.ComboLte
import it.smartphonecombo.uecapabilityparser.model.component.ComponentLte
import it.smartphonecombo.uecapabilityparser.model.modulation.ModulationOrder
import it.smartphonecombo.uecapabilityparser.model.modulation.toModulation
import it.smartphonecombo.uecapabilityparser.model.toMimo

/** A parser for LTE Combinations as reported by Qct Modem Capabilities */
object ImportQctModemCap : ImportCapabilities {

    /**
     * This parser take as [input] a [InputSource] containing LTE Combinations as reported by Qct
     * Modem Capabilities.
     *
     * The output is a [Capabilities] with the list of parsed LTE combos stored in
     * [lteCombos][Capabilities.lteCombos].
     *
     * It can parse multiple messages in the same input.
     */
    override fun parse(input: InputSource): Capabilities {
        val capabilities = Capabilities()
        val listCombo = mutableListOf<ComboLte>()

        input.useLines { seq ->
            try {
                val lines = seq.iterator()
                while (lines.hasNext()) {
                    val source = getValue(lines, "Source")
                    val type = getValue(lines, "Type")
                    val numCombos = getValue(lines, "Combos")?.toIntOrNull() ?: 0
                    val combosHeader = lines.firstOrNull { it.contains("""^\s+#\s+""".toRegex()) }

                    if (combosHeader == null) {
                        continue
                    }

                    if (source.equals("RRC", true) && type?.contains("NR", true) == true) {
                        // NR RRC CA combos not supported
                        continue
                    }

                    val sourceStr = "${source}-${type}".uppercase()
                    capabilities.addMetadata("source", sourceStr)
                    capabilities.addMetadata("numCombos", numCombos)

                    val indexDl = combosHeader.indexOf("DL Bands", ignoreCase = true)
                    val indexUl = combosHeader.indexOf("UL Bands", ignoreCase = true)

                    // This is used for DLCA > 5
                    val indexBands = combosHeader.indexOf("Bands", ignoreCase = true)
                    val twoRowFormat = indexBands > -1 && indexDl < 0

                    if (!twoRowFormat && (indexDl < 0 || indexUl < 0)) {
                        continue
                    }

                    repeat(numCombos) {
                        val combo =
                            if (twoRowFormat) {
                                parseComboTwoRow(lines.next(), lines.next(), indexBands)
                            } else {
                                parseCombo(lines.next(), indexDl, indexUl)
                            }
                        combo?.let { listCombo.add(it) }
                    }
                }
            } catch (ignored: NoSuchElementException) {
                // Do nothing
            }
        }

        capabilities.lteCombos = listCombo

        return capabilities
    }

    /**
     * Converts the given comboString to a [ComboLte].
     *
     * Returns null if parsing fails
     */
    private fun parseCombo(comboString: String, indexDl: Int, indexUl: Int): ComboLte? {
        try {
            val dlComponents = parseComponents(comboString.substring(indexDl, indexUl), true)

            val ulComponents = parseComponents(comboString.substring(indexUl), false)

            return ComboLte(dlComponents, ulComponents)
        } catch (ignored: Exception) {
            return null
        }
    }

    /**
     * Converts the given comboString to a [ComboLte].
     *
     * Returns null if parsing fails
     */
    private fun parseComboTwoRow(
        comboStringDl: String,
        comboStringUl: String,
        index: Int,
    ): ComboLte? {
        try {
            val dlComponents = parseComponents(comboStringDl.substring(index), true)

            val ulComponents = parseComponents(comboStringUl.substring(index), false)

            return ComboLte(dlComponents, ulComponents)
        } catch (ignored: Exception) {
            return null
        }
    }

    /** Converts the given componentsString to a List of [ComponentLte]. */
    private fun parseComponents(componentsString: String, isDl: Boolean): List<ComponentLte> {
        val components = mutableListWithCapacity<ComponentLte>(6)
        for (componentStr in componentsString.split('-', ' ')) {
            val component = parseComponent(componentStr, isDl)
            if (component != null) {
                components.add(component)
            }
        }
        return components
    }

    /**
     * Regex used to extract the various parts of a component.
     *
     * Mixed mimo is represented with the highest value as normal digit and the others as subscript
     * separated by space (MMSP).
     *
     * Modulation is represented as superscript digits.
     *
     * Example: 40D4 ₄ ₂²⁵⁶
     *
     * Note: in some versions bwClass is lowercase.
     */
    private val componentRegex =
        """(\d{1,3})([A-Fa-f])([124]?(?:\p{Zs}[₁₂₄]){0,4})([⁰¹²⁴⁵⁶]{0,4})""".toRegex()

    /**
     * Converts the given componentString to a [ComponentLte].
     *
     * Returns null if parsing fails.
     */
    private fun parseComponent(componentString: String, isDl: Boolean): ComponentLte? {
        val result = componentRegex.find(componentString) ?: return null

        val (_, bandRegex, bwClassRegex, mimoRegex, modRegex) = result.groupValues

        val baseBand = bandRegex.toInt()
        val bwClass = BwClass.valueOf(bwClassRegex)
        val mimoStr = mimoRegex.subscriptToDigit().filterNot(Char::isWhitespace)
        val mimo = mimoStr.toIntOrNull()?.toMimo() ?: EmptyMimo

        return if (isDl) {
            ComponentLte(baseBand, classDL = bwClass, mimoDL = mimo)
        } else {
            val modUL = ModulationOrder.of(modRegex.superscriptToDigit()).toModulation()
            ComponentLte(baseBand, classUL = bwClass, mimoUL = mimo, modUL = modUL)
        }
    }

    /**
     * Search for the first string beginning with given key. Then extract the value. This works for
     * strings like "key : value".
     */
    private fun getValue(iterator: Iterator<String>, key: String): String? {
        val string = iterator.firstOrNull { it.startsWith(key, true) } ?: return null
        return string.split(":").last().trim()
    }

    /** Converts all the subscript in the given string to digit */
    private fun String.subscriptToDigit(): String {
        val listChar = map { char ->
            if (char in '₀'..'₉') {
                char - '₀'.code + '0'.code
            } else {
                char
            }
        }
        return String(listChar.toCharArray())
    }

    /** Converts all the superscript in the given string to digit */
    private fun String.superscriptToDigit(): String {
        val listChar = map { char ->
            when (char) {
                '¹' -> '1'
                '²' -> '2'
                '³' -> '3'
                '⁰',
                in '⁴'..'⁹' -> char - '⁰'.code + '0'.code
                else -> char
            }
        }
        return String(listChar.toCharArray())
    }
}
