package surf.varun.feature.alerts

import surf.varun.core.domain.AlertEvaluator
import surf.varun.core.domain.Conditions
import surf.varun.core.domain.SpotTimeZones
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.Spot
import surf.varun.core.model.WindAlert
import java.time.ZoneId
import java.time.ZonedDateTime

/** A notification the worker should post for [alert]. */
data class AlertHit(
    val alert: WindAlert,
    val match: AlertEvaluator.Match,
    val spotName: String,
)

/**
 * The worker's decisions, kept pure: alerts plus cached data in, notifications out.
 * Only [AlertEvaluator.Decision.Fire] becomes a hit; suppressed ones stay quiet.
 */
object AlertChecker {
    fun live(alerts: List<WindAlert>, spots: Map<Int, Spot>, now: ZonedDateTime): List<AlertHit> =
        alerts.filter { it.enabled && it.trigger == AlertTrigger.LIVE }.mapNotNull { alert ->
            val spot = spots[alert.wgId] ?: return@mapNotNull null
            val decision = AlertEvaluator.evaluateLive(alert, spot.live, now)
            (decision as? AlertEvaluator.Decision.Fire)?.let { AlertHit(alert, it.match, spot.name) }
        }

    fun forecast(
        alerts: List<WindAlert>,
        hours: Map<Int, List<ForecastEntry>>,
        spots: Map<Int, Spot>,
        now: ZonedDateTime,
    ): List<AlertHit> =
        alerts.filter { it.enabled && it.trigger == AlertTrigger.FORECAST }.mapNotNull { alert ->
            val hourly = hours[alert.wgId] ?: return@mapNotNull null
            val spot = spots[alert.wgId]
            val decision = AlertEvaluator.evaluateForecast(alert, hourly, zoneOf(spot, now.zone), now)
            (decision as? AlertEvaluator.Decision.Fire)?.let { AlertHit(alert, it.match, spot?.name ?: alert.spotName) }
        }

    private fun zoneOf(spot: Spot?, fallback: ZoneId): ZoneId =
        spot?.let(Conditions::zoneOf) ?: SpotTimeZones.zoneFor(null, null, fallback)
}
