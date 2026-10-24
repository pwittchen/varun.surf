package surf.varun.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.color.ColorProvider
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import surf.varun.core.designsystem.theme.DarkWindColors
import surf.varun.core.designsystem.theme.LightWindColors
import surf.varun.core.domain.WindClass
import java.time.Duration

/** Wind class colour for day and night, matching the app's palette. */
internal fun windColor(windClass: WindClass): ColorProvider = when (windClass) {
    WindClass.CALM -> ColorProvider(LightWindColors.calm, DarkWindColors.calm)
    WindClass.LIGHT -> ColorProvider(LightWindColors.light, DarkWindColors.light)
    WindClass.WEAK -> ColorProvider(LightWindColors.weak, DarkWindColors.weak)
    WindClass.MODERATE -> ColorProvider(LightWindColors.moderate, DarkWindColors.moderate)
    WindClass.STRONG -> ColorProvider(LightWindColors.strong, DarkWindColors.strong)
    WindClass.EXTREME -> ColorProvider(LightWindColors.extreme, DarkWindColors.extreme)
}

internal val staleColor = ColorProvider(LightWindColors.stale, DarkWindColors.stale)

/** A wind number; rideable classes are bold so the class isn't told by colour alone. */
@Composable
internal fun WindText(text: String, windClass: WindClass, size: TextUnit) {
    Text(
        text,
        style = TextStyle(
            color = windColor(windClass),
            fontSize = size,
            fontWeight = if (windClass.isRideable) FontWeight.Bold else FontWeight.Medium,
        ),
        maxLines = 1,
    )
}

@Composable
internal fun SecondaryText(text: String, size: TextUnit = 12.sp, color: ColorProvider? = null) {
    Text(
        text,
        style = TextStyle(color = color ?: GlanceTheme.colors.onSurfaceVariant, fontSize = size),
        maxLines = 1,
    )
}

@Composable
internal fun RefreshButton(context: Context) {
    Image(
        provider = ImageProvider(R.drawable.ic_widget_refresh),
        contentDescription = context.getString(R.string.widget_refresh),
        modifier = GlanceModifier.size(24.dp).clickable(actionRunCallback<RefreshCallback>()),
        colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
    )
}

internal fun ageText(context: Context, minutes: Long): String = when {
    minutes < 1 -> context.getString(R.string.widget_just_now)
    minutes < 60 -> context.resources.getQuantityString(R.plurals.widget_minutes_ago, minutes.toInt(), minutes.toInt())
    else -> {
        val hours = Duration.ofMinutes(minutes).toHours().toInt()
        context.resources.getQuantityString(R.plurals.widget_hours_ago, hours, hours)
    }
}
