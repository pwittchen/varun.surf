package surf.varun.core.model

import java.time.LocalDateTime

/**
 * Hourly wind for every spot on one shared grid (`/api/v1/wind`). Index `i` of a
 * series is hour [hours]`[i]`; null means the forecast says nothing for that hour.
 */
data class WindTimeline(
    val hours: List<LocalDateTime?>,
    val rawHours: List<String>,
    val spots: Map<Int, SpotWindSeries>,
) {
    val size: Int get() = hours.size

    companion object {
        val EMPTY = WindTimeline(emptyList(), emptyList(), emptyMap())
    }
}

data class SpotWindSeries(
    val wind: List<Int?>,
    val gusts: List<Int?>,
    val direction: List<Direction?>,
)
