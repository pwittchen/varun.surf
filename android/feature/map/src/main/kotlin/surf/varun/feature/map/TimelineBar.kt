package surf.varun.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import surf.varun.core.ui.Format
import kotlin.math.roundToInt

/**
 * The hourly forecast timeline under the map (spec 5.4): step 0 is now, then one
 * step per hour of the grid; day ticks named by their weekday under the slider,
 * play/pause, a jump back to now, and a haptic tick per hour with a stronger one
 * at midnight (spec 2.5.12).
 */
@Composable
fun TimelineBar(
    steps: TimelineSteps,
    step: Int,
    playing: Boolean,
    unavailable: Boolean,
    onStep: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNow: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val label = steps.timeAt(step)?.let { Format.dayAndHour(context, it) } ?: stringResource(R.string.timeline_now)

    fun go(target: Int) {
        val next = steps.clamp(target)
        if (next == step) return
        haptics.performHapticFeedback(
            if (steps.isMidnight(next)) HapticFeedbackType.SegmentTick else HapticFeedbackType.SegmentFrequentTick,
        )
        onStep(next)
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (unavailable) {
                Text(
                    stringResource(R.string.timeline_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, contentDescription = null) }
                return@Row
            }
            IconButton(onClick = onTogglePlay, enabled = steps.lastStep > 0) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.timeline_pause else R.string.timeline_play),
                )
            }
            IconButton(onClick = { go(step - 1) }, enabled = step > 0) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.timeline_prev_hour))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { go(step + 1) }, enabled = step < steps.lastStep) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.timeline_next_hour))
            }
            IconButton(onClick = onNow, enabled = step > 0) {
                Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.timeline_go_now))
            }
        }
        if (!unavailable && steps.lastStep > 0) {
            Slider(
                value = step.toFloat(),
                onValueChange = { go(it.roundToInt()) },
                valueRange = 0f..steps.lastStep.toFloat(),
                modifier = Modifier.fillMaxWidth().semantics { stateDescription = label },
            )
            DayTicks(steps, Modifier.fillMaxWidth().height(DAY_TICKS_HEIGHT).padding(horizontal = 10.dp))
        }
    }
}

/**
 * A tick where each day starts, with the day's name beside it, so the track reads as
 * days rather than an unmarked bar. A name with no room before the next tick (or the
 * end) is left out rather than drawn over its neighbour.
 */
@Composable
private fun DayTicks(steps: TimelineSteps, modifier: Modifier) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val days = remember(steps, context) {
        steps.dayStarts.map { s -> s to Format.weekday(context, steps.timeAt(s)!!.dayOfWeek) }
    }
    Canvas(modifier) {
        val last = steps.lastStep.coerceAtLeast(1)
        val tick = 6.dp.toPx()
        val gap = 4.dp.toPx()
        days.forEachIndexed { i, (s, name) ->
            val x = size.width * s / last
            drawLine(color, Offset(x, 0f), Offset(x, tick), strokeWidth = 2.dp.toPx())
            val next = days.getOrNull(i + 1)?.let { size.width * it.first / last } ?: size.width
            val label = measurer.measure(name, style)
            if (x + gap + label.size.width <= next - gap) drawText(label, topLeft = Offset(x + gap, 0f))
        }
    }
}

/** The day ticks and the weekday names beside them. */
private val DAY_TICKS_HEIGHT = 16.dp
