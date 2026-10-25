package surf.varun.feature.spot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import surf.varun.core.designsystem.component.WindArrow
import surf.varun.core.designsystem.component.WindCell
import surf.varun.core.designsystem.component.WindNumber
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.designsystem.theme.Numbers
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.designsystem.theme.forClass
import surf.varun.core.domain.Conditions
import surf.varun.core.domain.WindClass
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.ui.Format
import surf.varun.core.ui.formatTemperature
import surf.varun.core.ui.formatWind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Section 2: model, windy-days filter, day chips and the hourly table or chart (spec 5.3.2). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ForecastSection(state: SpotUiState, actions: SpotActions) {
    val spot = state.spot ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var view by rememberSaveable { mutableStateOf(DataView.TABLE) }
    var modelSheet by remember { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

    val hours = remember(state.days) { state.days.flatMap { it.hours } }
    val tableState = rememberLazyListState()
    val chartScroll = rememberScrollState()
    val density = LocalDensity.current
    val chartHourWidth = chartHourWidth()

    fun scrollTo(index: Int) {
        if (index < 0) return
        scope.launch {
            when (view) {
                DataView.TABLE -> tableState.animateScrollToItem(index)
                DataView.CHART -> chartScroll.animateScrollTo(with(density) { (chartHourWidth * index).roundToPx() })
            }
        }
    }

    // A focus time (from an alert) selects its day and scrolls to its hour once the data is there
    LaunchedEffect(state.focusTime, hours) {
        val focus = state.focusTime ?: return@LaunchedEffect
        if (hours.isEmpty()) return@LaunchedEffect
        selectedDay = focus.toLocalDate().toString()
        val index = hours.indexOfFirst { it.time!! >= focus.withMinute(0) }.takeIf { it >= 0 } ?: hours.lastIndex
        tableState.scrollToItem(index)
        actions.focusConsumed()
    }

    val modelLabel = stringResource(R.string.model)
    val modelName = spot.availableModels.firstOrNull { it.key == state.model }?.name ?: state.model.uppercase()
    val spotToday = remember(spot.wgId) { Conditions.now(spot, ZonedDateTime.now()).toLocalDate() }

    SectionCard(stringResource(R.string.section_forecast)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { modelSheet = true },
                label = { Text(modelName) },
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.semantics { contentDescription = "$modelLabel: $modelName" },
            )
            FilterChip(
                selected = state.windyOnly,
                onClick = { actions.setWindyOnly(!state.windyOnly) },
                label = { Text(stringResource(R.string.windy_days)) },
                leadingIcon = if (state.windyOnly) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
            DataViewSwitch(view, onChange = { view = it })
        }

        if (spot.daily.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(spot.daily) { index, day ->
                    // Daily rows are Today, Tomorrow, Day 3... - one per day from the spot's today
                    val date = spotToday.plusDays(index.toLong())
                    val windClass = WindClass.ofAverage(day.wind, day.gusts)
                    FilterChip(
                        selected = selectedDay == date.toString(),
                        onClick = {
                            selectedDay = date.toString()
                            scrollTo(hours.indexOfFirst { it.time!!.toLocalDate() >= date })
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(Format.dayLabel(context, day.day, day.rawDate))
                                WindNumber(formatWind(day.wind), day.wind, style = Numbers.small, windClass = windClass)
                                WindArrow(day.direction, size = 14.dp, color = VarunTheme.wind.forClass(windClass), animate = false)
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        when {
            state.noWindyDays -> Text(
                stringResource(R.string.no_windy_days),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).wrapContentWidth(),
            )
            hours.isEmpty() -> Text(
                stringResource(R.string.no_hourly_forecast),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            view == DataView.TABLE -> HourlyTable(hours, spot.hasWaveData, tableState)
            else -> HourlyChart(hours, chartScroll, chartHourWidth)
        }
    }

    if (modelSheet) {
        ModalBottomSheet(onDismissRequest = { modelSheet = false }) {
            Text(
                stringResource(R.string.model_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val models = spot.availableModels.ifEmpty { listOf(ForecastModelOption(state.model, modelName)) }
            models.forEach { model ->
                ListItem(
                    colors = transparentListItemColors(),
                    headlineContent = { Text(model.name) },
                    leadingContent = { RadioButton(selected = model.key == state.model, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = model.key == state.model,
                        role = Role.RadioButton,
                        onClick = {
                            actions.selectModel(model.key)
                            modelSheet = false
                        },
                    ),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// --- table ------------------------------------------------------------------------

private enum class TableRow { TIME, WIND, GUSTS, DIRECTION, TEMP, RAIN, CLOUD, PRESSURE, WAVE, WAVE_PERIOD, WAVE_DIRECTION }

@Composable
internal fun scaled(value: Dp): Dp = value * LocalDensity.current.fontScale.coerceAtLeast(1f)

@Composable
private fun chartHourWidth(): Dp = 28.dp

/**
 * Windguru-style table: variables as rows, hours as columns, the label column
 * pinned while the hours scroll, a day header and separator where each day starts.
 */
@Composable
private fun HourlyTable(hours: List<ForecastEntry>, waves: Boolean, state: LazyListState) {
    val rows = remember(waves) {
        TableRow.entries.filter { waves || it !in setOf(TableRow.WAVE, TableRow.WAVE_PERIOD, TableRow.WAVE_DIRECTION) }
    }
    val rowHeight = scaled(32.dp)
    val headerHeight = scaled(22.dp)
    val columnWidth = scaled(44.dp)
    val separator = MaterialTheme.colorScheme.outline

    Row {
        Column(Modifier.width(scaled(72.dp))) {
            Spacer(Modifier.height(headerHeight))
            rows.forEach { row ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(
                        stringResource(row.label()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        LazyRow(state = state, modifier = Modifier.weight(1f)) {
            itemsIndexed(hours, key = { _, h -> h.time.toString() }) { index, hour ->
                val time = hour.time!!
                val firstOfDay = index == 0 || hours[index - 1].time!!.toLocalDate() != time.toLocalDate()
                Column(
                    Modifier
                        .width(columnWidth)
                        .drawBehind {
                            if (firstOfDay && index > 0) {
                                drawLine(separator, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(headerHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (firstOfDay) DayHeader(time.toLocalDate())
                    }
                    rows.forEach { row ->
                        Box(Modifier.height(rowHeight), contentAlignment = Alignment.Center) { TableCell(row, hour) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val context = LocalContext.current
    Text(
        Format.dayHeader(context, date),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        softWrap = false,
        maxLines = 1,
        // Wider than one column on purpose: columns don't clip, the label runs over the next hours
        modifier = Modifier.padding(start = 4.dp).wrapContentWidth(Alignment.Start, unbounded = true),
    )
}

private fun TableRow.label(): Int = when (this) {
    TableRow.TIME -> R.string.time
    TableRow.WIND -> R.string.wind
    TableRow.GUSTS -> R.string.gusts
    TableRow.DIRECTION -> R.string.direction
    TableRow.TEMP -> R.string.temperature
    TableRow.RAIN -> R.string.rain
    TableRow.CLOUD -> R.string.clouds
    TableRow.PRESSURE -> R.string.pressure
    TableRow.WAVE -> R.string.wave
    TableRow.WAVE_PERIOD -> R.string.wave_period
    TableRow.WAVE_DIRECTION -> R.string.wave_direction
}

@Composable
private fun TableCell(row: TableRow, hour: ForecastEntry) {
    val wind = VarunTheme.wind
    val text = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Only wind is coloured: every other row stays neutral so the wind classes stand out
    when (row) {
        TableRow.TIME -> Text("%02d".format(hour.time!!.hour), style = Numbers.tiny, color = muted)
        TableRow.WIND -> WindCell(formatWind(hour.wind), hour.wind)
        TableRow.GUSTS -> WindCell(formatWind(hour.gusts), hour.gusts)
        TableRow.DIRECTION -> WindArrow(hour.direction, size = 18.dp, color = wind.forClass(WindClass.of(hour.wind)), animate = false)
        TableRow.TEMP -> hour.temp?.let {
            Text(formatTemperature(it), style = Numbers.tiny, color = text)
        } ?: Dash()
        TableRow.RAIN -> hour.precipitation?.let {
            Text(if (it == 0.0) "0" else "%.1f".format(it), style = Numbers.tiny, color = if (it > 0) wind.rain else muted)
        } ?: Dash()
        TableRow.CLOUD -> hour.cloudCoverPercent?.let { Text("${it.roundToInt()}%", style = Numbers.tiny, color = muted) } ?: Dash()
        TableRow.PRESSURE -> hour.pressureHpa?.let { Text("${it.roundToInt()}", style = Numbers.tiny, color = muted) } ?: Dash()
        TableRow.WAVE -> hour.wave?.let { Text("%.1f".format(it), style = Numbers.tiny, color = text) } ?: Dash()
        TableRow.WAVE_PERIOD -> hour.wavePeriod?.let { Text("${it.roundToInt()}s", style = Numbers.tiny, color = muted) } ?: Dash()
        TableRow.WAVE_DIRECTION -> if (hour.waveDirection != null) {
            WindArrow(hour.waveDirection, size = 16.dp, color = muted, animate = false)
        } else {
            Dash()
        }
    }
}

@Composable
private fun Dash() = Text("-", style = Numbers.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)

// --- chart ------------------------------------------------------------------------

/** Wind and gust lines over the hours, direction arrows along the top, rain bars along the bottom. */
@Composable
private fun HourlyChart(hours: List<ForecastEntry>, scroll: ScrollState, hourWidth: Dp) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val windColors = VarunTheme.wind
    val windLine = MaterialTheme.colorScheme.primary
    val gustLine = windColors.strong
    val grid = MaterialTheme.colorScheme.outline
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = label)
    val dayStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
    val maxValue = (maxOf(hours.maxOf { it.gusts }, 15.0) * 1.1).toFloat()
    val maxRain = maxOf(hours.maxOf { it.precipitation ?: 0.0 }, 2.0).toFloat()
    val dayLabels = remember(hours) {
        hours.mapIndexedNotNull { i, h ->
            val time: LocalDateTime = h.time!!
            if (i == 0 || hours[i - 1].time!!.toLocalDate() != time.toLocalDate()) i to Format.dayHeader(context, time.toLocalDate()) else null
        }
    }
    val description = stringResource(R.string.chart_description)

    Box(Modifier.horizontalScroll(scroll)) {
        Canvas(
            Modifier
                .width(hourWidth * hours.size)
                .height(240.dp)
                .semantics { contentDescription = description },
        ) {
            val step = hourWidth.toPx()
            val arrowsTop = 18.dp.toPx()
            val chartTop = arrowsTop + 22.dp.toPx()
            val rainHeight = 36.dp.toPx()
            val labelsHeight = 16.dp.toPx()
            val chartBottom = size.height - rainHeight - labelsHeight
            val chartHeight = chartBottom - chartTop
            fun x(i: Int) = i * step + step / 2
            fun y(v: Double) = (chartBottom - v / maxValue * chartHeight).toFloat()

            listOf(10.0, 20.0, 30.0, 40.0).filter { it < maxValue }.forEach { v ->
                drawLine(grid, Offset(0f, y(v)), Offset(size.width, y(v)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            dayLabels.forEach { (i, text) ->
                val dx = i * step
                if (i > 0) drawLine(grid, Offset(dx, 0f), Offset(dx, size.height), 1.dp.toPx())
                drawText(measurer, text, Offset(dx + 4.dp.toPx(), 0f), dayStyle)
            }
            hours.forEachIndexed { i, h ->
                h.direction?.let { drawArrow(it, Offset(x(i), arrowsTop + 10.dp.toPx()), 12.dp.toPx(), windColors.forClass(WindClass.of(h.wind))) }
                val rain = (h.precipitation ?: 0.0).toFloat()
                if (rain > 0f) {
                    val barHeight = rain / maxRain * (rainHeight - 4.dp.toPx())
                    drawRect(
                        windColors.rain.copy(alpha = 0.7f),
                        topLeft = Offset(x(i) - step * 0.3f, chartBottom + rainHeight - barHeight),
                        size = Size(step * 0.6f, barHeight),
                    )
                }
                if (h.time!!.hour % 3 == 0) {
                    drawText(measurer, "%02d".format(h.time!!.hour), Offset(x(i) - 6.dp.toPx(), size.height - labelsHeight), labelStyle)
                }
            }
            val gusts = Path().apply { hours.forEachIndexed { i, h -> if (i == 0) moveTo(x(i), y(h.gusts)) else lineTo(x(i), y(h.gusts)) } }
            drawPath(gusts, gustLine, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
            val wind = Path().apply { hours.forEachIndexed { i, h -> if (i == 0) moveTo(x(i), y(h.wind)) else lineTo(x(i), y(h.wind)) } }
            drawPath(wind, windLine, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            hours.forEachIndexed { i, h ->
                drawCircle(windColors.forClass(WindClass.of(h.wind)), 3.dp.toPx(), Offset(x(i), y(h.wind)))
            }
        }
    }
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot(windLine, stringResource(R.string.wind))
        LegendDot(gustLine, stringResource(R.string.gusts))
        LegendDot(windColors.rain, stringResource(R.string.rain))
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

