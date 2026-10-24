package surf.varun.feature.alerts

import android.content.Context
import surf.varun.core.domain.AlertEvaluator
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.WindAlert
import surf.varun.core.model.WindUnit
import surf.varun.core.ui.Format

/** User-facing texts of alerts: rule summaries and notification bodies, in the user's unit. */
object AlertMessages {
    fun ruleSummary(context: Context, alert: WindAlert, unit: WindUnit): String {
        val unitLabel = Format.windUnitLabel(context, unit)
        val wind = if (alert.maxWind != null) {
            context.getString(
                R.string.alert_rule_range,
                Format.wind(alert.minWind.toDouble(), unit),
                Format.wind(alert.maxWind!!.toDouble(), unit),
                unitLabel,
            )
        } else {
            context.getString(R.string.alert_rule_min, Format.wind(alert.minWind.toDouble(), unit), unitLabel)
        }
        val directions = if (alert.directions.isEmpty()) {
            context.getString(R.string.alert_any_direction)
        } else {
            alert.directions.sortedBy { it.ordinal }.joinToString(" ") { it.name }
        }
        val trigger = when (alert.trigger) {
            AlertTrigger.LIVE -> context.getString(R.string.alert_trigger_live)
            AlertTrigger.FORECAST -> context.resources.getQuantityString(
                R.plurals.alert_trigger_forecast_days,
                alert.horizonDays,
                alert.horizonDays,
            )
        }
        return listOf(wind, directions, trigger).joinToString(" · ")
    }

    fun title(context: Context, hit: AlertHit): String = when (hit.match) {
        is AlertEvaluator.Match.Live -> context.getString(R.string.alert_live_title, hit.spotName)
        is AlertEvaluator.Match.Window -> context.getString(R.string.alert_forecast_title, hit.spotName)
    }

    /** "Jastarnia is firing - 21 kts SW (gusts 26)" / "Wind window Sat 13:00-17:00 at Jastarnia, up to 22 kts". */
    fun body(context: Context, hit: AlertHit, unit: WindUnit): String {
        val unitLabel = Format.windUnitLabel(context, unit)
        return when (val match = hit.match) {
            is AlertEvaluator.Match.Live -> context.getString(
                R.string.alert_live_body,
                hit.spotName,
                Format.wind(match.wind.toDouble(), unit),
                unitLabel,
                match.direction?.name.orEmpty(),
                Format.wind(match.gusts.toDouble(), unit),
            ).replace("  ", " ")
            is AlertEvaluator.Match.Window -> context.getString(
                R.string.alert_forecast_body,
                Format.weekday(context, match.start.dayOfWeek),
                Format.hour(context, match.start),
                Format.hour(context, match.end.plusHours(1)),
                hit.spotName,
                Format.wind(match.maxWind, unit),
                unitLabel,
            )
        }
    }
}
