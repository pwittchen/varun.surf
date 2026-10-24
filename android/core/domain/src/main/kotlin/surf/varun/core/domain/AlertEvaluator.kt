package surf.varun.core.domain

import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.WindAlert
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Decides whether a wind alert should notify. Pure: the worker feeds it the data
 * and the clock, and records [Decision.Fire] by updating `lastFiredAt`.
 */
object AlertEvaluator {
    /** A live alert notifies at most once per this period. */
    val LIVE_COOLDOWN: Duration = Duration.ofHours(3)

    sealed interface Decision {
        data class Fire(val match: Match) : Decision
        data class Suppressed(val reason: Reason, val match: Match) : Decision
        data object NoMatch : Decision
    }

    enum class Reason { DISABLED, MUTED, QUIET_HOURS, ALREADY_NOTIFIED }

    sealed interface Match {
        data class Live(val wind: Int, val gusts: Int, val direction: Direction?) : Match

        data class Window(
            val start: LocalDateTime,
            val end: LocalDateTime,
            val maxWind: Double,
            val maxGusts: Double,
            val direction: Direction?,
        ) : Match
    }

    fun matchesWind(alert: WindAlert, wind: Double, direction: Direction?): Boolean {
        if (wind < alert.minWind) return false
        if (alert.maxWind != null && wind > alert.maxWind!!) return false
        if (alert.directions.isNotEmpty() && (direction == null || direction !in alert.directions)) return false
        return true
    }

    fun evaluateLive(alert: WindAlert, live: LiveConditions?, now: ZonedDateTime): Decision {
        if (alert.trigger != AlertTrigger.LIVE || live == null) return Decision.NoMatch
        if (!matchesWind(alert, live.wind.toDouble(), live.direction)) return Decision.NoMatch
        val match = Match.Live(live.wind, live.gusts, live.direction)
        suppression(alert, now)?.let { return Decision.Suppressed(it, match) }
        val last = alert.lastFiredAt
        if (last != null && Duration.between(last, now.toInstant()) < LIVE_COOLDOWN) {
            return Decision.Suppressed(Reason.ALREADY_NOTIFIED, match)
        }
        return Decision.Fire(match)
    }

    /**
     * First window in the next [WindAlert.horizonDays] days of daytime hours (spot-local)
     * where the rule holds for at least [WindAlert.minConsecutiveHours] consecutive hours.
     */
    fun findWindow(alert: WindAlert, hourly: List<ForecastEntry>, spotNow: LocalDateTime): Match.Window? {
        val horizon = spotNow.plusDays(alert.horizonDays.toLong())
        val hours = hourly
            .filter { it.time != null && it.time!! >= spotNow.withMinute(0).withSecond(0).withNano(0) && it.time!! <= horizon }
            .filter(ForecastDays::isDaytime)
            .sortedBy { it.time }

        val needed = alert.minConsecutiveHours.coerceAtLeast(1)
        var run = mutableListOf<ForecastEntry>()
        fun windowOf(entries: List<ForecastEntry>) = Match.Window(
            start = entries.first().time!!,
            end = entries.last().time!!,
            maxWind = entries.maxOf { it.wind },
            maxGusts = entries.maxOf { it.gusts },
            direction = entries.groupingBy { it.direction }.eachCount().maxByOrNull { it.value }?.key,
        )
        for (entry in hours) {
            val matches = matchesWind(alert, entry.wind, entry.direction)
            val contiguous = run.isEmpty() || Duration.between(run.last().time, entry.time) == Duration.ofHours(1)
            if (matches && contiguous) {
                run.add(entry)
            } else {
                if (run.size >= needed) return windowOf(run)
                run = if (matches) mutableListOf(entry) else mutableListOf()
            }
        }
        return if (run.size >= needed) windowOf(run) else null
    }

    fun evaluateForecast(
        alert: WindAlert,
        hourly: List<ForecastEntry>,
        spotZone: ZoneId,
        now: ZonedDateTime,
    ): Decision {
        if (alert.trigger != AlertTrigger.FORECAST) return Decision.NoMatch
        val window = findWindow(alert, hourly, now.withZoneSameInstant(spotZone).toLocalDateTime())
            ?: return Decision.NoMatch
        suppression(alert, now)?.let { return Decision.Suppressed(it, window) }
        val last = alert.lastFiredAt
        if (last != null && last.atZone(now.zone).toLocalDate() == now.toLocalDate()) {
            return Decision.Suppressed(Reason.ALREADY_NOTIFIED, window)
        }
        return Decision.Fire(window)
    }

    /** Quiet hours are the user's own (device zone), and may wrap midnight (22 -> 7). */
    fun inQuietHours(alert: WindAlert, now: ZonedDateTime): Boolean {
        val start = alert.quietStartHour ?: return false
        val end = alert.quietEndHour ?: return false
        if (start == end) return false
        val hour = now.hour
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    private fun suppression(alert: WindAlert, now: ZonedDateTime): Reason? = when {
        !alert.enabled -> Reason.DISABLED
        alert.mutedUntil != null && now.toInstant() < alert.mutedUntil -> Reason.MUTED
        inQuietHours(alert, now) -> Reason.QUIET_HOURS
        else -> null
    }
}
