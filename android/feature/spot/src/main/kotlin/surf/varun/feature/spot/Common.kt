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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A titled section of the spot screen. Flat on the page rather than a card, so the
 * chips, buttons and tables inside never sit in a box within a box.
 */
@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            action?.invoke()
        }
        content()
    }
}

/** How a section shows a series of readings: the forecast and the live history both offer both. */
internal enum class DataView { TABLE, CHART }

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
