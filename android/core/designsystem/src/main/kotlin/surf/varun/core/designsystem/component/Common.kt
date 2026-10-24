package surf.varun.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** Full-area message: no favourites, no results, offline without cache. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** A thin full-width banner (offline, sweep progress, errors). */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(color = container, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

/** Small rounded label, e.g. "3 min ago", tinted when stale. */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, icon: ImageVector? = null) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(12.dp))
                Spacer(Modifier.size(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
        modifier = modifier,
    )
}
