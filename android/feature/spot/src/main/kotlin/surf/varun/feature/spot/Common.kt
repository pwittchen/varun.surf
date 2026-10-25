package surf.varun.feature.spot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A titled section of the spot screen. Flat on the page rather than a card, so the
 * chips, buttons and tables inside never sit in a box within a box. With
 * [inlineHeading] the content draws the heading itself ([SectionHeading]), on a row
 * it shares with something else.
 */
@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    inlineHeading: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (!inlineHeading) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionHeading(title, Modifier.weight(1f))
                action?.invoke()
            }
        }
        content()
    }
}

@Composable
internal fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/** How a section shows a series of readings: the forecast and the live history both offer both. */
internal enum class DataView { TABLE, CHART }

/** Marks a [FillRow] child that keeps its natural width instead of taking a share of the spare width. */
internal fun Modifier.fixedInFillRow(): Modifier = layoutId(FixedInFillRow)

private object FixedInFillRow

/**
 * A single row whose children keep their natural width and share whatever is left
 * equally (except those marked [fixedInFillRow]), so together they always span the
 * full width. When the row is too narrow the first child gives way, so it should be
 * the one able to ellipsize.
 */
@Composable
internal fun FillRow(modifier: Modifier = Modifier, gap: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val extra = constraints.maxWidth - gapPx * (measurables.size - 1).coerceAtLeast(0) - natural.sum()
        val growing = measurables.indices.filter { measurables[it].layoutId != FixedInFillRow }
        val widths = if (extra >= 0 && growing.isNotEmpty()) {
            natural.mapIndexed { i, w ->
                when (i) {
                    !in growing -> w
                    growing.last() -> w + extra / growing.size + extra % growing.size
                    else -> w + extra / growing.size
                }
            }
        } else if (extra >= 0) {
            natural
        } else {
            natural.mapIndexed { i, w -> if (i == 0) (w + extra).coerceAtLeast(0) else w }
        }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints(minWidth = widths[i], maxWidth = widths[i], maxHeight = constraints.maxHeight))
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gapPx
            }
        }
    }
}

/** Icon-only table/chart switch, the same height and rounded-square corners as the chips it sits next to. */
@Composable
internal fun DataViewSwitch(view: DataView, onChange: (DataView) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.height(32.dp)) {
        DataView.entries.forEachIndexed { index, option ->
            val label = stringResource(if (option == DataView.TABLE) R.string.view_table else R.string.view_chart)
            SegmentedButton(
                selected = view == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, DataView.entries.size, MaterialTheme.shapes.small),
                modifier = Modifier.width(44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                icon = {},
                label = {
                    Icon(
                        if (option == DataView.TABLE) Icons.Rounded.TableRows else Icons.AutoMirrored.Rounded.ShowChart,
                        contentDescription = label,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                    )
                },
            )
        }
    }
}
