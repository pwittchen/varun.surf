package surf.varun.feature.alerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import surf.varun.core.designsystem.component.WindArrow
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.domain.Units
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Direction
import surf.varun.core.model.Spot
import surf.varun.core.model.WindUnit
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.ui.Format
import surf.varun.core.ui.countryName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertEditorScreen(viewModel: AlertEditorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    var showDenied by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) navigator.back() else showDenied = true
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is EditorEvent.Saved -> {
                    AlertsWork.schedule(context)
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    when {
                        // Asked when the first alert is created, never on launch (spec 5.7)
                        needsPermission && event.firstAlert -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        needsPermission -> showDenied = true
                        else -> navigator.back()
                    }
                }
            }
        }
    }

    if (showDenied) {
        AlertDialog(
            onDismissRequest = { showDenied = false; navigator.back() },
            title = { Text(stringResource(R.string.editor_permission_title)) },
            text = { Text(stringResource(R.string.editor_permission_message)) },
            confirmButton = {
                TextButton(onClick = { showDenied = false; navigator.back() }) { Text(stringResource(R.string.editor_ok)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.loading) { Text(stringResource(R.string.editor_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpotSection(state, results, viewModel)
            state.error?.let {
                Text(
                    stringResource(if (it == EditorError.NO_SPOT) R.string.editor_error_spot else R.string.editor_error_range),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            WindSection(state, viewModel)
            DirectionSection(state.directions, viewModel::toggleDirection, viewModel::clearDirections)
            TriggerSection(state, viewModel)
            QuietHoursSection(state, viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SpotSection(state: AlertEditorState, results: List<Spot>, viewModel: AlertEditorViewModel) {
    SectionTitle(stringResource(R.string.editor_spot))
    val spot = state.spot
    if (spot != null) {
        ListItem(
            colors = transparentListItemColors(),
            headlineContent = { Text(spot.name) },
            supportingContent = { if (spot.country.isNotEmpty()) Text(countryName(spot.country)) },
            trailingContent = { TextButton(onClick = viewModel::clearSpot) { Text(stringResource(R.string.editor_change_spot)) } },
        )
        return
    }
    OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::setQuery,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        placeholder = { Text(stringResource(R.string.editor_spot_search)) },
    )
    results.forEach { result ->
        ListItem(
            colors = transparentListItemColors(),
            headlineContent = { Text(result.name) },
            supportingContent = { Text(countryName(result.country)) },
            modifier = Modifier.clickable { viewModel.selectSpot(result) },
        )
    }
}

@Composable
private fun WindSection(state: AlertEditorState, viewModel: AlertEditorViewModel) {
    val context = LocalContext.current
    val unit = state.unit
    val min = Units.wind(AlertEditorState.MIN_KNOTS.toDouble(), unit).toFloat()
    val max = Units.wind(AlertEditorState.MAX_KNOTS.toDouble(), unit).toFloat()
    // Whole steps in every unit but m/s, whose values are fractional
    val steps = if (unit == WindUnit.METERS_PER_SECOND) 0 else (max - min).toInt() - 1

    Text(stringResource(R.string.editor_min_wind, Format.windWithUnit(context, state.minWind.toDouble(), unit)))
    Slider(
        value = Units.wind(state.minWind.toDouble(), unit).toFloat().coerceIn(min, max),
        onValueChange = viewModel::setMinWind,
        valueRange = min..max,
        steps = steps,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.editor_max_wind_toggle), modifier = Modifier.weight(1f))
        Switch(checked = state.maxWind != null, onCheckedChange = viewModel::setMaxEnabled)
    }
    state.maxWind?.let { maxWind ->
        Text(stringResource(R.string.editor_max_wind, Format.windWithUnit(context, maxWind.toDouble(), unit)))
        Slider(
            value = Units.wind(maxWind.toDouble(), unit).toFloat().coerceIn(min, max),
            onValueChange = viewModel::setMaxWind,
            valueRange = min..max,
            steps = steps,
        )
    }
}

/** An 8-way compass: N at the top, "Any" in the middle clears the selection. */
@Composable
private fun DirectionSection(selected: Set<Direction>, onToggle: (Direction) -> Unit, onClear: () -> Unit) {
    SectionTitle(stringResource(R.string.editor_directions))
    Text(
        stringResource(R.string.editor_directions_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val grid = listOf(
        listOf(Direction.NW, Direction.N, Direction.NE),
        listOf(Direction.W, null, Direction.E),
        listOf(Direction.SW, Direction.S, Direction.SE),
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        grid.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { direction ->
                    Box(Modifier.width(88.dp), contentAlignment = Alignment.Center) {
                        if (direction == null) {
                            FilterChip(
                                selected = selected.isEmpty(),
                                onClick = onClear,
                                label = { Text(stringResource(R.string.editor_any)) },
                            )
                        } else {
                            FilterChip(
                                selected = direction in selected,
                                onClick = { onToggle(direction) },
                                label = { Text(direction.name) },
                                leadingIcon = { WindArrow(direction, size = 16.dp, animate = false) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggerSection(state: AlertEditorState, viewModel: AlertEditorViewModel) {
    SectionTitle(stringResource(R.string.editor_trigger))
    val liveAvailable = state.spot?.hasLiveStation != false
    val options = listOf(AlertTrigger.FORECAST to R.string.editor_trigger_forecast, AlertTrigger.LIVE to R.string.editor_trigger_live)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (trigger, label) ->
            SegmentedButton(
                selected = state.trigger == trigger,
                onClick = { viewModel.setTrigger(trigger) },
                enabled = trigger == AlertTrigger.FORECAST || liveAvailable,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
    val hint = when {
        !liveAvailable -> R.string.editor_no_live_station
        state.trigger == AlertTrigger.LIVE -> R.string.editor_trigger_live_hint
        else -> R.string.editor_trigger_forecast_hint
    }
    Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.trigger == AlertTrigger.FORECAST) {
        Stepper(
            label = pluralStringResource(R.plurals.editor_horizon_days, state.horizonDays, state.horizonDays),
            onDecrease = { viewModel.setHorizonDays(state.horizonDays - 1) },
            onIncrease = { viewModel.setHorizonDays(state.horizonDays + 1) },
        )
        Stepper(
            label = pluralStringResource(R.plurals.editor_consecutive_hours, state.minConsecutiveHours, state.minConsecutiveHours),
            onDecrease = { viewModel.setMinConsecutiveHours(state.minConsecutiveHours - 1) },
            onIncrease = { viewModel.setMinConsecutiveHours(state.minConsecutiveHours + 1) },
        )
    }
}

@Composable
private fun QuietHoursSection(state: AlertEditorState, viewModel: AlertEditorViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(stringResource(R.string.editor_quiet_hours))
        Spacer(Modifier.weight(1f))
        Switch(checked = state.quietHours, onCheckedChange = viewModel::setQuietHours)
    }
    if (state.quietHours) {
        Stepper(
            label = "${stringResource(R.string.editor_quiet_from)} ${"%02d:00".format(state.quietStartHour)}",
            onDecrease = { viewModel.setQuietStart(state.quietStartHour - 1) },
            onIncrease = { viewModel.setQuietStart(state.quietStartHour + 1) },
        )
        Stepper(
            label = "${stringResource(R.string.editor_quiet_to)} ${"%02d:00".format(state.quietEndHour)}",
            onDecrease = { viewModel.setQuietEnd(state.quietEndHour - 1) },
            onIncrease = { viewModel.setQuietEnd(state.quietEndHour + 1) },
        )
    }
}

@Composable
private fun Stepper(label: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onDecrease) {
            Icon(Icons.Rounded.Remove, stringResource(R.string.editor_decrease), Modifier.size(18.dp))
        }
        FilledTonalIconButton(onClick = onIncrease) {
            Icon(Icons.Rounded.Add, stringResource(R.string.editor_increase), Modifier.size(18.dp))
        }
    }
}
