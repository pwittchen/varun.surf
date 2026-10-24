package surf.varun.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
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
import surf.varun.core.ui.Format

/** The favourites, strongest wind first (spec 6.8). */
class FavouritesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.favourites(context)
        provideContent {
            val refreshedAt = currentState<Preferences>()[WidgetKeys.REFRESHED_AT]
            val data by produceState(initial, refreshedAt) {
                if (refreshedAt != null) value = WidgetData.favourites(context)
            }
            GlanceTheme { FavouritesContent(data) }
        }
    }
}

class FavouritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavouritesWidget()

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
private fun FavouritesContent(data: FavouritesWidgetData) {
    val context = LocalContext.current
    val unit = Format.windUnitLabel(context, data.windUnit)
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.widget_favourites_title),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(WidgetData.spotIntent(context, null))),
            )
            RefreshButton(context)
        }
        if (data.spots.isEmpty()) {
            SecondaryText(context.getString(R.string.widget_no_favourites), 13.sp)
            return@Column
        }
        LazyColumn {
            items(data.spots, itemId = { it.wgId.toLong() }) { model ->
                val reading = model.reading
                Row(
                    GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable(actionStartActivity(WidgetData.spotIntent(context, model.wgId))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(
                            model.name,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                            maxLines = 1,
                        )
                        val status = when {
                            reading == null -> context.getString(R.string.widget_no_data)
                            reading.isLive -> model.ageMinutes?.let { ageText(context, it) } ?: context.getString(R.string.widget_live)
                            else -> context.getString(R.string.widget_forecast)
                        }
                        SecondaryText(status, 11.sp, if (model.stale) staleColor else null)
                    }
                    if (reading != null) {
                        WindText(Format.wind(reading.wind, data.windUnit), model.windClass, 20.sp)
                        SecondaryText(" / ${Format.wind(reading.gusts, data.windUnit)} $unit", 12.sp)
                        Spacer(GlanceModifier.width(6.dp))
                        WindText(WidgetModels.arrow(reading.direction), model.windClass, 18.sp)
                    }
                }
            }
        }
    }
}
