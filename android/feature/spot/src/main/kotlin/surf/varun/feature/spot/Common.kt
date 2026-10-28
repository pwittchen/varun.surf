package surf.varun.feature.spot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import surf.varun.core.designsystem.component.WindCell
import surf.varun.core.designsystem.component.WindNumber
import surf.varun.core.designsystem.theme.Numbers
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.designsystem.theme.fillForClass
import surf.varun.core.domain.WindClass
import surf.varun.core.model.TableDensity
import surf.varun.core.ui.LocalUserSettings

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

/**
 * Sizes of the hourly forecast and live history tables, from the table density setting.
 * Compact packs them like Windguru: short rows, narrow columns, smaller numbers and
 * wind cells filled edge to edge, so the classes run as unbroken colour bands.
 */
internal class TableMetrics(
    val compact: Boolean,
    val rowHeight: Dp,
    val headerHeight: Dp,
    val labelWidth: Dp,
    val hourWidth: Dp,
    val readingWidth: Dp,
    val arrowSize: Dp,
    val text: TextStyle,
)

@Composable
internal fun tableMetrics(): TableMetrics =
    if (LocalUserSettings.current.tableDensity == TableDensity.COMPACT) {
        TableMetrics(
            compact = true,
            rowHeight = scaled(22.dp),
            headerHeight = scaled(18.dp),
            labelWidth = scaled(64.dp),
            hourWidth = scaled(30.dp),
            readingWidth = scaled(40.dp),
            arrowSize = 14.dp,
            text = Numbers.tiny.copy(fontSize = 11.sp, lineHeight = 14.sp),
        )
    } else {
        TableMetrics(
            compact = false,
            rowHeight = scaled(32.dp),
            headerHeight = scaled(22.dp),
            labelWidth = scaled(72.dp),
            hourWidth = scaled(44.dp),
            readingWidth = scaled(52.dp),
            arrowSize = 18.dp,
            text = Numbers.tiny,
        )
    }

/** A wind value in a table: a tinted pill, or in a compact table the whole cell tinted. */
@Composable
internal fun TableWindCell(text: String, knots: Double, metrics: TableMetrics) {
    if (metrics.compact) {
        val windClass = WindClass.of(knots)
        Box(
            Modifier.fillMaxSize().background(VarunTheme.wind.fillForClass(windClass)),
            contentAlignment = Alignment.Center,
        ) {
            WindNumber(text, knots, style = metrics.text, windClass = windClass)
        }
    } else {
        WindCell(text, knots)
    }
}
