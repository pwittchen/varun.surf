package surf.varun.core.domain

import surf.varun.core.model.DayLabel
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Spot
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * What the wind is doing at a spot "now": the live station reading when there is
 * one, otherwise the forecast entry that best stands in for now.
 * Mirrors `getWindConditions` in the web's `weather.js`.
 */
data class WindReading(
    val wind: Double,
    val gusts: Double,
    val direction: Direction?,
    val temp: Double?,
    val isLive: Boolean,
    /** Spot-local time of the forecast entry used, null for live readings and daily entries. */
    val forecastTime: LocalDateTime? = null,
    /** Server date string of the forecast entry used (also set for daily entries). */
    val forecastLabel: String? = null,
) {
    val average: Double get() = (wind + gusts) / 2
    val windClass: WindClass get() = WindClass.ofAverage(wind, gusts)
}

object Conditions {
    /** Live readings this old or older are stale (yellow on the web, `date.js`: 60 minutes). */
    val STALE_AFTER: Duration = Duration.ofMinutes(60)

    fun zoneOf(spot: Spot): ZoneId = SpotTimeZones.zoneFor(spot.country, spot.coordinates)

    fun now(spot: Spot, at: ZonedDateTime): LocalDateTime = at.withZoneSameInstant(zoneOf(spot)).toLocalDateTime()

    fun current(spot: Spot, at: ZonedDateTime): WindReading? {
        spot.live?.let { return it.toReading() }
        val forecast = forecastForNow(spot, now(spot, at)) ?: return null
        return WindReading(
            wind = forecast.wind,
            gusts = forecast.gusts,
            direction = forecast.direction,
            temp = forecast.temp,
            isLive = false,
            forecastTime = forecast.time,
            forecastLabel = forecast.rawDate,
        )
    }

    /** Hourly entry closest to [now], else today's daily entry, else the first daily one. */
    fun forecastForNow(spot: Spot, now: LocalDateTime): ForecastEntry? {
        closestHour(spot.hourly, now)?.let { return it }
        return spot.daily.firstOrNull { it.day == DayLabel.Today } ?: spot.daily.firstOrNull()
    }

    fun closestHour(hours: List<ForecastEntry>, now: LocalDateTime): ForecastEntry? =
        hours.filter { it.time != null }.minByOrNull { Duration.between(now, it.time).abs() }

    /** The web's "firing now" sort key: average of wind and gusts, -∞ without data so it sinks. */
    fun firingScore(spot: Spot, at: ZonedDateTime): Double =
        current(spot, at)?.average ?: Double.NEGATIVE_INFINITY

    fun isStale(live: LiveConditions, spotZone: ZoneId, at: ZonedDateTime): Boolean {
        val observed = live.observedAt ?: return true
        val age = Duration.between(observed.atZone(spotZone), at)
        return age >= STALE_AFTER
    }

    fun ageOf(live: LiveConditions, spotZone: ZoneId, at: ZonedDateTime): Duration? =
        live.observedAt?.let { Duration.between(it.atZone(spotZone), at).coerceAtLeast(Duration.ZERO) }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this

    private fun LiveConditions.toReading() = WindReading(
        wind = wind.toDouble(),
        gusts = gusts.toDouble(),
        direction = direction,
        temp = temp?.toDouble(),
        isLive = true,
    )
}
