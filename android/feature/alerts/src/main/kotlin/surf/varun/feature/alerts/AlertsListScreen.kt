package surf.varun.feature.alerts

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import surf.varun.core.designsystem.component.Banner
import surf.varun.core.designsystem.component.EmptyState
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.model.WindAlert
import surf.varun.core.navigation.AlertEditorRoute
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.ui.Format
import surf.varun.core.ui.rememberNow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsListScreen(viewModel: AlertsListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.alerts_deleted)
    val undoText = stringResource(R.string.alerts_undo)

    // Workers follow the alerts: started for the first one, stopped with the last
    val schedule = state.alerts.map { it.enabled to it.trigger }
    LaunchedEffect(schedule, state.loading) {
        if (!state.loading) AlertsWork.schedule(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alerts_title)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.navigate(AlertEditorRoute()) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.alerts_create)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loading && state.alerts.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.alerts_empty_title),
                    message = stringResource(R.string.alerts_empty_message),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (state.alerts.isNotEmpty() && !AlertNotifier.canNotify(context)) {
                        item {
                            Banner(
                                text = stringResource(R.string.alerts_notifications_off),
                                icon = Icons.Rounded.NotificationsOff,
                                trailing = {
                                    TextButton(onClick = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }) { Text(stringResource(R.string.alerts_open_settings)) }
                                },
                            )
                        }
                    }
                    items(state.alerts, key = { it.id }) { alert ->
                        AlertRow(
                            alert = alert,
                            summary = AlertMessages.ruleSummary(context, alert, state.unit),
                            onClick = { navigator.navigate(AlertEditorRoute(alertId = alert.id)) },
                            onEnabled = { viewModel.setEnabled(alert, it) },
                            onDelete = {
                                viewModel.delete(alert)
                                scope.launch {
                                    val result = snackbar.showSnackbar(deletedText, actionLabel = undoText, withDismissAction = true)
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(alert)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    alert: WindAlert,
    summary: String,
    onClick: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    val muted = alert.mutedUntil?.takeIf { it.isAfter(now.toInstant()) }
    val enabledDescription = stringResource(R.string.alerts_enabled_description, alert.spotName)
    ListItem(
        colors = transparentListItemColors(),
        headlineContent = { Text(alert.spotName) },
        supportingContent = {
            Text(
                listOfNotNull(
                    summary,
                    muted?.let { stringResource(R.string.alerts_muted_until, Format.time(context, it)) },
                ).joinToString("\n"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Switch(
                    checked = alert.enabled,
                    onCheckedChange = onEnabled,
                    modifier = Modifier.semantics { contentDescription = enabledDescription },
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.alerts_delete))
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
