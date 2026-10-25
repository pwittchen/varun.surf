package surf.varun.feature.about

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import surf.varun.core.designsystem.component.SectionHeader
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.navigation.LicencesRoute
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.ui.Format
import surf.varun.core.ui.Intents
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: AboutViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                item { Fact(stringResource(R.string.app_version), appVersion) }

                item { SectionHeader(stringResource(R.string.section_status)) }
                item { StatusRow(state) }
                state.status?.let { status ->
                    item { Fact(stringResource(R.string.backend_version), status.version ?: "?") }
                    status.uptime?.let { item { Fact(stringResource(R.string.uptime), it) } }
                    status.startTime?.let { start ->
                        runCatching { Instant.parse(start) }.getOrNull()?.let { item { Fact(stringResource(R.string.started), Format.instant(context, it)) } }
                    }
                    item { Fact(stringResource(R.string.spots_count), status.spotsCount?.toString() ?: "-") }
                    item { Fact(stringResource(R.string.countries_count), status.countriesCount?.toString() ?: "-") }
                    item { Fact(stringResource(R.string.live_stations_count), status.liveStations?.toString() ?: "-") }
                }
                state.progress?.let { progress ->
                    item {
                        Fact(
                            stringResource(R.string.forecast_sweep),
                            when {
                                progress.inProgress -> stringResource(R.string.forecast_sweep_running, progress.completed, progress.total)
                                progress.finishedAt != null -> stringResource(
                                    R.string.forecast_sweep_done,
                                    Format.age(context, Duration.between(progress.finishedAt, Instant.now())),
                                )
                                else -> "-"
                            },
                        )
                    }
                }

                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { SectionHeader(stringResource(R.string.section_sources)) }
                item { SubHeader(stringResource(R.string.sources_forecasts)) }
                items(DataSources.forecasts) { LinkRow(it) }
                item { SubHeader(stringResource(R.string.sources_live)) }
                items(DataSources.liveStations) { LinkRow(it) }
                item { SubHeader(stringResource(R.string.sources_spots)) }
                items(DataSources.spots) { LinkRow(it) }
                item {
                    Text(
                        stringResource(R.string.map_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (state.sponsors.isNotEmpty()) {
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    item { SectionHeader(stringResource(R.string.section_sponsors)) }
                    items(state.sponsors) { sponsor -> LinkRow(Link(sponsor.name, sponsor.link ?: Intents.WEB_BASE)) }
                }

                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { SectionHeader(stringResource(R.string.section_contact)) }
                item { Text(stringResource(R.string.author), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                item {
                    ActionRow(Icons.Rounded.Email, stringResource(R.string.contact_email)) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:hello@varun.surf".toUri()))
                        } catch (_: ActivityNotFoundException) {
                            // Shown as text anyway
                        }
                    }
                }
                item { ActionRow(Icons.Rounded.Language, stringResource(R.string.website)) { Intents.openUrl(context, Intents.WEB_BASE) } }
                item {
                    ActionRow(Icons.Rounded.AddLocationAlt, stringResource(R.string.suggest_spot)) {
                        Intents.openUrl(context, "https://pwittchen.notion.site/28a649d2871780368191c6ce5a64029e")
                    }
                }
                item { ActionRow(Icons.Rounded.Code, stringResource(R.string.github)) { Intents.openUrl(context, "https://github.com/pwittchen/varun.surf") } }

                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { SectionHeader(stringResource(R.string.section_privacy)) }
                item {
                    Text(
                        stringResource(R.string.privacy_text),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    ActionRow(Icons.Rounded.Policy, stringResource(R.string.privacy_policy)) {
                        Intents.openUrl(context, "${Intents.WEB_BASE}/privacy")
                    }
                }
                item {
                    ListItem(
                        colors = transparentListItemColors(),
                        headlineContent = { Text(stringResource(R.string.licences)) },
                        supportingContent = { Text(stringResource(R.string.licences_summary)) },
                        leadingContent = { Icon(Icons.Rounded.Gavel, null) },
                        modifier = Modifier.clickable { navigator.navigate(LicencesRoute) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(state: AboutUiState) {
    val (icon, text, tint) = when {
        state.statusError -> Triple(Icons.Rounded.CloudOff, stringResource(R.string.status_unreachable), MaterialTheme.colorScheme.error)
        state.status?.status.equals("UP", ignoreCase = true) ->
            Triple(Icons.Rounded.CheckCircle, stringResource(R.string.status_up), MaterialTheme.colorScheme.primary)
        state.status != null -> Triple(Icons.Rounded.Error, stringResource(R.string.status_down), MaterialTheme.colorScheme.error)
        else -> return
    }
    ListItem(colors = transparentListItemColors(), headlineContent = { Text(text) }, leadingContent = { Icon(icon, null, tint = tint) })
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
internal fun LinkRow(link: Link) {
    val context = LocalContext.current
    ListItem(
        colors = transparentListItemColors(),
        headlineContent = { Text(link.name) },
        supportingContent = { Text(link.detail, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
        modifier = Modifier.clickable { Intents.openUrl(context, link.url) },
    )
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    ListItem(colors = transparentListItemColors(), headlineContent = { Text(text) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.clickable(onClick = onClick))
}
