package it.smartphonecombo.uecapabilityparser.model

import java.util.WeakHashMap

sealed interface Mimo : Comparable<Mimo> {
    fun toCompactStr(): String
    fun average(): Double
    override fun compareTo(other: Mimo): Int = average().compareTo(other.average())

    companion object {
        private val cacheInt = WeakHashMap<Int, Mimo>()
        private val cacheIntArray = WeakHashMap<List<Int>, Mimo>()

        fun from(int: Int): Mimo {
            val cachedResult = cacheInt[int]
            if (cachedResult != null) {
                return cachedResult
            }

            val result =
                if (int == 0) {
                    EmptyMimo
                } else if (int > 10) {
                    from(int.toString().map(Char::digitToInt))
                } else {
                    SingleMimo(int)
                }

            cacheInt[int] = result
            return result
        }

        fun from(list: List<Int>): Mimo {
            val cachedResult = cacheIntArray[list]
            if (cachedResult != null) {
                return cachedResult
            }

            val result =
                if (list.isEmpty()) {
                    EmptyMimo
                } else if (list.size == 1 || list.distinct().count() == 1) {
                    SingleMimo(list.first())
                } else {
                    MixedMimo(list.sortedDescending())
                }

            cacheIntArray[list] = result
            return result
        }
    }
}

object EmptyMimo : Mimo {
    override fun toCompactStr(): String = ""
    override fun toString(): String = ""
    override fun average(): Double = 0.0
}

private data class SingleMimo(private val mimo: Int) : Mimo {
    override fun toCompactStr(): String = mimo.toString()
    override fun toString(): String = mimo.toString()
    override fun average(): Double = mimo.toDouble()
}

private data class MixedMimo(private val mimoList: List<Int>) : Mimo {
    override fun toCompactStr(): String = mimoList.joinToString("")
    override fun toString(): String = mimoList.joinToString(", ")
    override fun average(): Double = mimoList.average()
}

fun Int.toMimo(): Mimo = Mimo.from(this)

/**
 * Return:
 * - 1 if [string] contains "one"
 * - 2 if [string] contains "two"
 * - 4 if [string] contains "four"
 * - 8 if [string] contains "eight"
 * - 0 otherwise
 */
internal fun Int.Companion.fromLiteral(string: String?): Int {
    return when {
        string == null -> 0
        "one" in string -> 1
        "two" in string -> 2
        "four" in string -> 4
        "eight" in string -> 8
        else -> 0
    }
}
