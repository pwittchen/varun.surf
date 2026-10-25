package surf.varun.feature.spot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import surf.varun.core.designsystem.component.InfoChip
import surf.varun.core.designsystem.component.WindArrow
import surf.varun.core.designsystem.component.WindCell
import surf.varun.core.designsystem.component.WindNumber
import surf.varun.core.designsystem.theme.Numbers
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.designsystem.theme.forClass
import surf.varun.core.domain.Conditions
import surf.varun.core.domain.WindClass
import surf.varun.core.model.Direction
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Spot
import surf.varun.core.ui.Format
import surf.varun.core.ui.formatTemperature
import surf.varun.core.ui.formatWind
import surf.varun.core.ui.windUnitLabel
import java.time.ZonedDateTime

/** Section 1: the live reading and its last 12 hours (spec 5.3.1). Only for spots with a station. */
@Composable
internal fun NowSection(spot: Spot, now: ZonedDateTime) {
    val live = spot.live ?: return
    val context = LocalContext.current
    val zone = remember(spot.wgId) { Conditions.zoneOf(spot) }
    val stale = Conditions.isStale(live, zone, now)
    val age = Conditions.ageOf(live, zone, now)?.let { Format.age(context, it) } ?: live.rawDate
    val windClass = WindClass.ofAverage(live.wind.toDouble(), live.gusts.toDouble())
    var view by rememberSaveable { mutableStateOf(DataView.CHART) }

    SectionCard(stringResource(R.string.section_now)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WindNumber(formatWind(live.wind.toDouble()), live.wind.toDouble(), style = Numbers.medium, windClass = windClass)
                    Text(
                        " / ${formatWind(live.gusts.toDouble())} ${windUnitLabel()}",
                        style = Numbers.medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.gusting, formatWind(live.gusts.toDouble())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WindArrow(live.direction, size = 24.dp, color = VarunTheme.wind.forClass(windClass))
                Text(live.direction?.name ?: "-", style = MaterialTheme.typography.labelLarge)
            }
            live.temp?.let {
                Text(formatTemperature(it.toDouble()), style = Numbers.medium)
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoChip(
            text = if (stale) stringResource(R.string.stale_reading, age) else stringResource(R.string.reading_age, age),
            tint = if (stale) VarunTheme.wind.stale else MaterialTheme.colorScheme.primary,
            icon = if (stale) Icons.Rounded.Warning else Icons.Rounded.Sensors,
        )
        Spacer(Modifier.height(16.dp))
        val enoughHistory = spot.liveHistory.size >= MIN_HISTORY
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (enoughHistory) DataViewSwitch(view, onChange = { view = it })
        }
        Spacer(Modifier.height(8.dp))
        if (enoughHistory) {
            when (view) {
                DataView.CHART -> HistoryChart(spot.liveHistory)
                DataView.TABLE -> HistoryTable(spot.liveHistory)
            }
        } else {
            Text(
                stringResource(R.string.history_too_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The web needs five readings before it draws live data; so does the app. */
private const val MIN_HISTORY = 5

/** Wind line over a wind-to-gust band, direction ticks along the bottom (oldest left). */
@Composable
private fun HistoryChart(history: List<LiveConditions>) {
    val context = LocalContext.current
    val readings = remember(history) { history.sortedBy { it.observedAt } }
    val windColor = MaterialTheme.colorScheme.primary
    val bandColor = windColor.copy(alpha = 0.18f)
    val gustColor = VarunTheme.wind.strong
    val gridColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val unit = windUnitLabel()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val first = readings.first().observedAt
    val last = readings.last().observedAt
    val firstLabel = first?.let { Format.hour(context, it) } ?: readings.first().rawDate
    val lastLabel = last?.let { Format.hour(context, it) } ?: readings.last().rawDate
    // Some stations report no gusts (Turawa sends 0): the gust line would hug the
    // floor and the scale would be cut below the wind, so it is drawn only when
    // reported, and a gust never sits under its own wind
    val hasGusts = readings.any { it.gusts > 0 }
    fun gustOf(r: LiveConditions) = maxOf(r.gusts, r.wind)
    val top = readings.maxOf { gustOf(it) }
    val description = stringResource(
        R.string.history_description,
        firstLabel,
        lastLabel,
        "${formatWind(readings.minOf { it.wind }.toDouble())}-${formatWind(top.toDouble())} $unit",
    )
    val maxValue = maxOf(top, 10) * 1.15f
    val gridSteps = listOf(10, 20, 30, 40).filter { it < maxValue }
    val gridLabels = gridSteps.map { formatWind(it.toDouble()) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = description },
    ) {
        val bottomPad = 18.dp.toPx()
        val chartHeight = size.height - bottomPad
        val count = readings.size
        fun x(i: Int) = if (count == 1) 0f else i * size.width / (count - 1)
        fun y(v: Int) = chartHeight - v / maxValue * chartHeight

        gridSteps.forEachIndexed { index, step ->
            val gy = y(step)
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawText(measurer, gridLabels[index], Offset(2f, gy - 14.sp.toPx()), labelStyle)
        }

        if (hasGusts) {
            val band = Path().apply {
                moveTo(x(0), y(readings[0].wind))
                readings.forEachIndexed { i, r -> lineTo(x(i), y(r.wind)) }
                for (i in count - 1 downTo 0) lineTo(x(i), y(gustOf(readings[i])))
                close()
            }
            drawPath(band, bandColor)

            val gusts = Path().apply { readings.forEachIndexed { i, r -> if (i == 0) moveTo(x(i), y(gustOf(r))) else lineTo(x(i), y(gustOf(r))) } }
            drawPath(gusts, gustColor.copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx()))
        }
        val wind = Path().apply { readings.forEachIndexed { i, r -> if (i == 0) moveTo(x(i), y(r.wind)) else lineTo(x(i), y(r.wind)) } }
        drawPath(wind, windColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // About a dozen direction ticks, evenly spread
        val step = maxOf(1, count / 12)
        for (i in 0 until count step step) {
            readings[i].direction?.let { drawArrow(it, Offset(x(i).coerceIn(6.dp.toPx(), size.width - 6.dp.toPx()), size.height - 8.dp.toPx()), 10.dp.toPx(), labelColor) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(firstLabel, style = MaterialTheme.typography.labelSmall, color = labelColor)
        Spacer(Modifier.weight(1f))
        Text(lastLabel, style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

private enum class HistoryRow { TIME, WIND, GUSTS, DIRECTION, TEMP }

/**
 * The same readings as the chart, one column each, newest on the left - where the
 * web's live table starts too - so the latest reading is in view without scrolling.
 */
@Composable
private fun HistoryTable(history: List<LiveConditions>) {
    val context = LocalContext.current
    val readings = remember(history) { history.sortedByDescending { it.observedAt } }
    val rows = remember(readings) {
        HistoryRow.entries.filter { it != HistoryRow.TEMP || readings.any { r -> r.temp != null } }
    }
    val rowHeight = scaled(32.dp)
    val columnWidth = scaled(52.dp)
    val text = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row {
        Column(Modifier.width(scaled(72.dp))) {
            rows.forEach { row ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(row.label()), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                }
            }
        }
        LazyRow(Modifier.weight(1f)) {
            items(readings) { reading ->
                Column(Modifier.width(columnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    rows.forEach { row ->
                        Box(Modifier.height(rowHeight), contentAlignment = Alignment.Center) {
                            when (row) {
                                HistoryRow.TIME -> Text(
                                    reading.observedAt?.let { Format.clock(context, it) } ?: reading.rawDate,
                                    style = Numbers.tiny,
                                    color = muted,
                                    maxLines = 1,
                                )
                                HistoryRow.WIND -> WindCell(formatWind(reading.wind.toDouble()), reading.wind.toDouble())
                                HistoryRow.GUSTS -> WindCell(formatWind(reading.gusts.toDouble()), reading.gusts.toDouble())
                                HistoryRow.DIRECTION -> WindArrow(
                                    reading.direction,
                                    size = 18.dp,
                                    color = VarunTheme.wind.forClass(WindClass.of(reading.wind.toDouble())),
                                    animate = false,
                                )
                                HistoryRow.TEMP -> Text(
                                    reading.temp?.let { formatTemperature(it.toDouble()) } ?: "-",
                                    style = Numbers.tiny,
                                    color = if (reading.temp != null) text else muted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun HistoryRow.label(): Int = when (this) {
    HistoryRow.TIME -> R.string.time
    HistoryRow.WIND -> R.string.wind
    HistoryRow.GUSTS -> R.string.gusts
    HistoryRow.DIRECTION -> R.string.direction
    HistoryRow.TEMP -> R.string.temperature
}

/** Small arrow pointing where the wind blows to, centred on [center]. */
internal fun DrawScope.drawArrow(direction: Direction, center: Offset, size: Float, color: Color) {
    translate(center.x - size / 2, center.y - size / 2) {
        rotate(direction.degrees.toFloat(), pivot = Offset(size / 2, size / 2)) {
            val path = Path().apply {
                moveTo(size / 2, size)
                lineTo(size * 0.85f, size * 0.45f)
                lineTo(size * 0.6f, size * 0.5f)
                lineTo(size * 0.6f, 0f)
                lineTo(size * 0.4f, 0f)
                lineTo(size * 0.4f, size * 0.5f)
                lineTo(size * 0.15f, size * 0.45f)
                close()
            }
            drawPath(path, color)
        }
    }
}
