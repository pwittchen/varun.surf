package surf.varun.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import surf.varun.core.designsystem.component.SectionHeader
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.CardDensity
import surf.varun.core.model.ParticleQuality
import surf.varun.core.model.StartTab
import surf.varun.core.model.TableDensity
import surf.varun.core.model.TemperatureUnit
import surf.varun.core.model.ThemeMode
import surf.varun.core.model.TimelineReach
import surf.varun.core.model.WindUnit
import surf.varun.core.navigation.LocalNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state.settings
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.update { it.copy(useLocation = granted) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(R.string.section_appearance)) }
            item {
                ChoiceSetting(
                    title = R.string.theme,
                    options = ThemeMode.entries,
                    selected = s.theme,
                    label = { it.label() },
                    onSelect = { mode -> viewModel.update { it.copy(theme = mode) } },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchSetting(R.string.dynamic_color, R.string.dynamic_color_summary, s.dynamicColor) { on ->
                        viewModel.update { it.copy(dynamicColor = on) }
                    }
                }
            }
            item {
                ChoiceSetting(R.string.language, AppLanguage.entries, state.language, { it.label() }, viewModel::setLanguage)
            }
            item {
                ChoiceSetting(R.string.density, CardDensity.entries, s.density, { it.label() }) { d -> viewModel.update { it.copy(density = d) } }
            }
            item {
                ChoiceSetting(R.string.table_density, TableDensity.entries, s.tableDensity, { it.label() }) { d ->
                    viewModel.update { it.copy(tableDensity = d) }
                }
            }
            item {
                ChoiceSetting(R.string.start_tab, StartTab.entries, s.startTab, { it.label() }) { t -> viewModel.update { it.copy(startTab = t) } }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.section_units)) }
            item {
                ChoiceSetting(R.string.wind_unit, WindUnit.entries, s.windUnit, { it.label() }) { u -> viewModel.update { it.copy(windUnit = u) } }
            }
            item {
                ChoiceSetting(R.string.temperature_unit, TemperatureUnit.entries, s.temperatureUnit, { it.label() }) { u ->
                    viewModel.update { it.copy(temperatureUnit = u) }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.section_forecast)) }
            item {
                SwitchSetting(R.string.remember_model, R.string.remember_model_summary, s.rememberModelGlobally) { on ->
                    viewModel.update { it.copy(rememberModelGlobally = on) }
                }
            }
            item {
                SwitchSetting(R.string.fade_off_direction, R.string.fade_off_direction_summary, s.fadeOffDirection) { on ->
                    viewModel.update { it.copy(fadeOffDirection = on) }
                }
            }
            item {
                SwitchSetting(R.string.show_kite_size, R.string.show_kite_size_summary, s.showKiteSize) { on ->
                    viewModel.update { it.copy(showKiteSize = on) }
                }
            }
            item {
                ChoiceSetting(R.string.timeline_reach, TimelineReach.entries, s.timelineReach, { it.label() }) { r ->
                    viewModel.update { it.copy(timelineReach = r) }
                }
            }
            item {
                ChoiceSetting(R.string.particles, ParticleQuality.entries, s.particleQuality, { it.label() }) { q ->
                    viewModel.update { it.copy(particleQuality = q) }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.section_privacy)) }
            item {
                SwitchSetting(R.string.use_location, R.string.use_location_summary, s.useLocation) { on ->
                    if (on) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    else viewModel.update { it.copy(useLocation = false) }
                }
            }
            item {
                ClickSetting(R.string.notifications, R.string.notifications_summary) { openNotificationSettings(context) }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.section_data)) }
            item { ClickSetting(R.string.clear_cache, R.string.clear_cache_summary) { confirmClear = true } }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_cache_confirm)) },
            text = { Text(stringResource(R.string.clear_cache_summary)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearCache { Toast.makeText(context, R.string.clear_cache_done, Toast.LENGTH_SHORT).show() }
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SwitchSetting(title: Int, summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = transparentListItemColors(),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) }),
    )
}

@Composable
private fun ClickSetting(title: Int, summary: Int, onClick: () -> Unit) {
    ListItem(
        colors = transparentListItemColors(),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A setting with a handful of options: shows the current one, picks in a dialog. */
@Composable
private fun <T> ChoiceSetting(title: Int, options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        colors = transparentListItemColors(),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(label(selected)), color = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = {
                // Scrolls: a phone held sideways has no height for four or five options
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = option == selected, role = Role.RadioButton) {
                                    open = false
                                    onSelect(option)
                                },
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Text(stringResource(label(option)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun AppLanguage.label() = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_en
    AppLanguage.POLISH -> R.string.language_pl
}

private fun CardDensity.label() = when (this) {
    CardDensity.COMFORTABLE -> R.string.density_comfortable
    CardDensity.COMPACT -> R.string.density_compact
}

private fun TableDensity.label() = when (this) {
    TableDensity.COMFORTABLE -> R.string.density_comfortable
    TableDensity.COMPACT -> R.string.density_compact
}

private fun StartTab.label() = when (this) {
    StartTab.AUTO -> R.string.start_auto
    StartTab.SPOTS -> R.string.start_spots
    StartTab.FAVOURITES -> R.string.start_favourites
    StartTab.MAP -> R.string.start_map
}

private fun WindUnit.label() = when (this) {
    WindUnit.KNOTS -> R.string.wind_knots
    WindUnit.METERS_PER_SECOND -> R.string.wind_ms
    WindUnit.KILOMETERS_PER_HOUR -> R.string.wind_kmh
    WindUnit.BEAUFORT -> R.string.wind_beaufort
}

private fun TemperatureUnit.label() = when (this) {
    TemperatureUnit.CELSIUS -> R.string.temperature_c
    TemperatureUnit.FAHRENHEIT -> R.string.temperature_f
}

private fun TimelineReach.label() = when (this) {
    TimelineReach.FIVE_DAYS -> R.string.timeline_five_days
    TimelineReach.FULL_RUN -> R.string.timeline_full
}

private fun ParticleQuality.label() = when (this) {
    ParticleQuality.AUTO -> R.string.particles_auto
    ParticleQuality.LOW -> R.string.particles_low
    ParticleQuality.HIGH -> R.string.particles_high
    ParticleQuality.OFF -> R.string.particles_off
}
