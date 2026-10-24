package surf.varun.core.model

import java.time.LocalDateTime

/**
 * One forecast row: an hour of the hourly forecast, or a day of the daily one.
 *
 * Hourly rows carry [time] (spot-local wall-clock time). Daily rows carry [day]
 * instead - the server labels them "Today", "Tomorrow", "Day 3"... in English.
 */
data class ForecastEntry(
    val rawDate: String,
    val time: LocalDateTime? = null,
    val day: DayLabel? = null,
    val wind: Double,
    val gusts: Double,
    val direction: Direction?,
    val temp: Double? = null,
    val precipitation: Double? = null,
    val cloudCoverPercent: Double? = null,
    val pressureHpa: Double? = null,
    val wave: Double? = null,
    val wavePeriod: Double? = null,
    val waveDirection: Direction? = null,
)

/** Day label of a daily forecast row, mapped from the server's English token. */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Tomorrow : DayLabel
    data class Offset(val days: Int) : DayLabel
    data class Weekday(val dayOfWeek: java.time.DayOfWeek) : DayLabel
    data class Other(val text: String) : DayLabel
}
