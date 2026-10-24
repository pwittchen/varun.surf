package surf.varun.feature.alerts

import surf.varun.core.model.Direction

/**
 * Directions named in a spot's free-text "best wind" ("W, SW, NW", "Meltemi N, NE",
 * "N-NE", "SW/W", "E, ENE"), used to prefill an alert's direction sectors.
 * Anything it can't read gives an empty set, which means "any direction".
 */
object BestWind {
    // Upper-case compass codes as whole words, so "Levante" or "trade" never match
    private val CODE = Regex("""(?<![A-Za-z])([NSEW]{1,3})(?![A-Za-z])""")
    private val RANGE = Regex("""(?<![A-Za-z])([NSEW]{1,3})\s*[-–]\s*([NSEW]{1,3})(?![A-Za-z])""")

    private val SIXTEEN = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    fun parse(text: String?): Set<Direction> {
        if (text.isNullOrBlank()) return emptySet()
        val result = mutableSetOf<Direction>()
        RANGE.findAll(text).forEach { match ->
            val from = degrees(match.groupValues[1])
            val to = degrees(match.groupValues[2])
            if (from != null && to != null) result += arc(from, to)
        }
        CODE.findAll(text).forEach { match -> result += directionsOf(match.groupValues[1]) }
        return result
    }

    private fun degrees(code: String): Double? = SIXTEEN.indexOf(code).takeIf { it >= 0 }?.let { it * 22.5 }

    /** An 8-point code maps to itself; a 16-point one ("ENE") to both neighbours. */
    private fun directionsOf(code: String): Set<Direction> {
        Direction.parse(code)?.let { return setOf(it) }
        val deg = degrees(code) ?: return emptySet()
        return setOf(Direction.fromDegrees(deg - 22.5), Direction.fromDegrees(deg + 22.4))
    }

    /** The shorter arc between two bearings, in 45° steps. */
    private fun arc(from: Double, to: Double): Set<Direction> {
        var span = ((to - from) % 360 + 360) % 360
        var start = from
        if (span > 180) {
            start = to
            span = 360 - span
        }
        val result = mutableSetOf<Direction>()
        var deg = 0.0
        while (deg <= span + 0.01) {
            result += Direction.fromDegrees(start + deg)
            deg += 22.5
        }
        return result
    }
}
