package surf.varun.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import surf.varun.core.domain.WindClass
import surf.varun.core.ui.Format

/**
 * One spot's wind (spec 6.8): 2×1 wind number and arrow; from 3×1 the name, gusts,
 * temperature, the reading's age and the next 12 hours of forecast, packed tight
 * enough to fit a single row.
 */
class SpotWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialId = getAppWidgetState<Preferences>(context, id)[WidgetKeys.SPOT_ID]
        val initial = WidgetData.spot(context, initialId)
        provideContent {
            val prefs = currentState<Preferences>()
            val configured = prefs[WidgetKeys.SPOT_ID]
            val refreshedAt = prefs[WidgetKeys.REFRESHED_AT]
            val data by produceState(initial, configured, refreshedAt) {
                if (configured != initialId || refreshedAt != null) value = WidgetData.spot(context, configured)
            }
            GlanceTheme { SpotWidgetContent(data) }
        }
    }

    internal companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val FULL = DpSize(180.dp, 80.dp)
    }
}

class SpotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpotWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetWork.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetWork.schedule(context)
    }
}

@Composable
private fun SpotWidgetContent(data: SpotWidgetData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val model = data.model
    val unit = Format.windUnitLabel(context, data.windUnit)
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionStartActivity(WidgetData.spotIntent(context, model?.wgId))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model == null) {
            SecondaryText(context.getString(R.string.widget_no_spot))
            return@Column
        }
        val reading = model.reading
        val full = size.width >= SpotWidget.FULL.width && size.height >= SpotWidget.FULL.height

        if (!full) {
            // 2×1: just the number, the arrow and the name
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                if (reading != null) {
                    WindText(Format.wind(reading.wind, data.windUnit), model.windClass, 24.sp)
                    Spacer(GlanceModifier.width(4.dp))
                    WindText(WidgetModels.arrow(reading.direction), model.windClass, 20.sp)
                    Spacer(GlanceModifier.width(6.dp))
                }
                Text(
                    model.name,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                model.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            RefreshButton(context, 20.dp)
        }
        if (reading == null) {
            SecondaryText(context.getString(R.string.widget_no_data))
            return@Column
        }
        // Wind and gusts share one size, so the whole reading fits a single-row widget
        Row(verticalAlignment = Alignment.CenterVertically) {
            WindText(Format.wind(reading.wind, data.windUnit), model.windClass, 16.sp)
            Spacer(GlanceModifier.width(4.dp))
            SecondaryText("/ ${Format.wind(reading.gusts, data.windUnit)} $unit", 16.sp)
            Spacer(GlanceModifier.width(8.dp))
            WindText(WidgetModels.arrow(reading.direction), model.windClass, 16.sp)
            Spacer(GlanceModifier.width(2.dp))
            SecondaryText(reading.direction?.name.orEmpty(), 14.sp)
        }
        Row {
            val parts = listOfNotNull(
                reading.temp?.let { Format.temperature(context, it, data.temperatureUnit) },
                if (reading.isLive) {
                    model.ageMinutes?.let { ageText(context, it) } ?: context.getString(R.string.widget_live)
                } else {
                    context.getString(R.string.widget_forecast)
                },
            )
            SecondaryText(parts.joinToString(" · "), color = if (model.stale) staleColor else null)
        }
        if (model.strip.isNotEmpty()) {
            Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                model.strip.forEach { hour ->
                    val windClass = WindClass.of(hour.wind)
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        SecondaryText(hour.time!!.hour.toString().padStart(2, '0'), 10.sp)
                        // The arrow rides next to the number: two lines instead of three
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WindText(Format.wind(hour.wind, data.windUnit), windClass, 12.sp)
                            WindText(WidgetModels.arrow(hour.direction), windClass, 10.sp)
                        }
                    }
                }
            }
        }
    }
}
