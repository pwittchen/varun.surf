package surf.varun.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import surf.varun.core.data.util.DataError
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.designsystem.component.Banner
import surf.varun.core.model.ForecastProgress

/**
 * Offline / refresh-failed banner (spec 6.2). Shown only when something is cached
 * underneath - with no cache the screen shows a full empty state instead.
 */
@Composable
fun SyncBanner(status: SyncStatus, online: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val error = status.lastError
    val visible = !online || (error != null && !status.refreshing)
    AnimatedVisibility(visible, modifier) {
        val offline = !online || error is DataError.Offline
        Banner(
            text = stringResource(if (offline) R.string.offline_banner else R.string.refresh_failed_banner),
            icon = if (offline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurface,
            trailing = if (online) {
                { TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } }
            } else {
                null
            },
        )
    }
}

/** "Loading forecasts: 412 / 827 spots" while the server's sweep runs (spec 2.1). */
@Composable
fun ForecastProgressBanner(progress: ForecastProgress?, modifier: Modifier = Modifier) {
    AnimatedVisibility(progress != null && progress.inProgress && progress.total > 0, modifier) {
        val p = progress ?: return@AnimatedVisibility
        Column(Modifier.fillMaxWidth()) {
            Banner(
                text = stringResource(R.string.forecast_progress, p.completed, p.total),
                icon = Icons.Rounded.Refresh,
            )
            LinearProgressIndicator(
                progress = { (p.completed.toFloat() / p.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
