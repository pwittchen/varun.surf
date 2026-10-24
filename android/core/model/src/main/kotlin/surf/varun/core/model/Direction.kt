package surf.varun.core.model

/**
 * The eight compass points the backend reports wind in. The order is the one
 * `/api/v1/wind` indexes into (`WindTimeline.DIRECTIONS` on the server), so
 * [ordinal] is the timeline's direction index.
 *
 * A direction is where the wind blows **from**.
 */
enum class Direction(val degrees: Int) {
    N(0), NE(45), E(90), SE(135), S(180), SW(225), W(270), NW(315);

    /** Bit used to store a set of directions in one Int (alerts). */
    val bit: Int get() = 1 shl ordinal

    companion object {
        fun parse(value: String?): Direction? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
        }

        fun fromIndex(index: Int?): Direction? = index?.let { entries.getOrNull(it) }

        /** Nearest compass point to [degrees], with ±22.5° tolerance like the backend. */
        fun fromDegrees(degrees: Double): Direction {
            val normalized = ((degrees % 360) + 360) % 360
            return entries[((normalized + 22.5) / 45).toInt() % 8]
        }

        fun fromMask(mask: Int): Set<Direction> = entries.filterTo(mutableSetOf()) { mask and it.bit != 0 }

        fun toMask(directions: Set<Direction>): Int = directions.fold(0) { acc, d -> acc or d.bit }
    }
}
