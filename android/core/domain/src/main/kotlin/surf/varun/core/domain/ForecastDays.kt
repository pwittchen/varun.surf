package surf.varun.core.domain

import surf.varun.core.model.ForecastEntry
import java.time.LocalDate

/**
 * Grouping and filtering of hourly forecasts the way the web's spot page does it.
 */
object ForecastDays {
    /** Daytime hours shown in the hourly views (`spot.js`: 06:00 to 21:00 inclusive). */
    const val FIRST_DAY_HOUR = 6
    const val LAST_DAY_HOUR = 21

    /** A day is windy when any of its daytime hours has at least this much wind (`hasWindyConditions`). */
    const val WINDY_KNOTS = 12.0

    data class Day(val date: LocalDate, val hours: List<ForecastEntry>) {
        val isWindy: Boolean get() = hours.any { it.wind >= WINDY_KNOTS }
        val maxWind: Double get() = hours.maxOfOrNull { it.wind } ?: 0.0
        val maxGusts: Double get() = hours.maxOfOrNull { it.gusts } ?: 0.0
    }

    fun isDaytime(entry: ForecastEntry): Boolean {
        val hour = entry.time?.hour ?: return false
        return hour in FIRST_DAY_HOUR..LAST_DAY_HOUR
    }

    /**
     * Daytime hours grouped by day, in order. With [windyOnly] the days without a
     * windy hour are dropped, which is the web's "Windy days" toggle.
     */
    fun daytimeDays(hourly: List<ForecastEntry>, windyOnly: Boolean = false): List<Day> =
        hourly.filter(::isDaytime)
            .groupBy { it.time!!.toLocalDate() }
            .map { (date, hours) -> Day(date, hours) }
            .sortedBy { it.date }
            .filter { !windyOnly || it.isWindy }
}
