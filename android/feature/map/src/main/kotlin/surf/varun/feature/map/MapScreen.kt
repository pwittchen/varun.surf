package surf.varun.feature.map

import surf.varun.core.ui.MapStyles
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.maps.Style
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.model.Direction
import surf.varun.core.model.ParticleQuality
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.navigation.MapRoute
import surf.varun.core.navigation.SearchRoute
import surf.varun.core.navigation.SpotRoute
import surf.varun.core.ui.Intents
import surf.varun.core.ui.LocalReduceMotion
import surf.varun.core.ui.countryName

private val PEEK_HEIGHT = 156.dp
private val SIDE_SHEET_BREAKPOINT = 840.dp
private const val PLAY_STEP_MS = 650L

/** Europe, where most spots are, when there is nothing better to show. */
private val DEFAULT_CAMERA = SavedCamera(lat = 48.0, lon = 10.0, zoom = 3.3)

private val cameraSaver = listSaver<SavedCamera, Double>(
    save = { listOf(it.lat, it.lon, it.zoom) },
    restore = { SavedCamera(it[0], it[1], it[2]) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(route: MapRoute, viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = LocalReduceMotion.current
    val windColors = VarunTheme.wind

    var controller by remember { mutableStateOf<MapController?>(null) }
    var camera by rememberSaveable(stateSaver = cameraSaver) { mutableStateOf(DEFAULT_CAMERA) }
    var moving by remember { mutableStateOf(false) }
    var cameraVersion by remember { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var showCountries by rememberSaveable { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var focused by rememberSaveable { mutableStateOf(false) }
    val powerSave = rememberPowerSaveMode()

    // Refresh only while visible (spec 6.1)
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() } }

    // Satellite by default, the normal map on request; neither follows the dark theme
    val satellite = state.settings.mapSatellite
    LaunchedEffect(controller, satellite) {
        val map = controller ?: return@LaunchedEffect
        val builder = if (satellite) Style.Builder().fromJson(MapStyles.satelliteJson) else Style.Builder().fromUri(MapStyles.STREET)
        map.setStyle(builder, windColors, darkBasemap = satellite) { cameraVersion++ }
    }
    LaunchedEffect(controller, state.features) { controller?.setFeatures(state.features) }
    LaunchedEffect(controller, state.settings.mapMarkers) { controller?.setMarkersVisible(state.settings.mapMarkers) }

    // A MapRoute(focusWgId) centres and opens that spot once
    LaunchedEffect(controller, route.focusWgId, state.allSpots) {
        val id = route.focusWgId ?: return@LaunchedEffect
        val map = controller ?: return@LaunchedEffect
        if (focused) return@LaunchedEffect
        val spot = state.allSpots.firstOrNull { it.wgId == id } ?: return@LaunchedEffect
        spot.coordinates?.let { map.centreOn(it) }
        viewModel.select(id)
        focused = true
    }

    // Play through the forecast hours, stopping at the end
    LaunchedEffect(playing) {
        while (playing) {
            delay(PLAY_STEP_MS)
            val current = viewModel.uiState.value
            if (current.step >= current.steps.lastStep) {
                playing = false
            } else {
                viewModel.setStep(current.step + 1)
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { centreOnMe(viewModel, controller, snackbar, context) }
    }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState, snackbarHostState = snackbar)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= SIDE_SHEET_BREAKPOINT
        val selected = state.selected

        LaunchedEffect(selected?.wgId, wide) {
            if (selected != null && !wide) sheetState.expand()
        }
        LaunchedEffect(sheetState.currentValue) {
            // Collapsing the sheet back to the timeline closes the spot
            if (sheetState.currentValue == SheetValue.PartiallyExpanded && !wide) viewModel.select(null)
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = PEEK_HEIGHT,
            snackbarHost = { SnackbarHost(snackbar) },
            sheetContent = {
                TimelineBar(
                    steps = state.steps,
                    step = state.step,
                    playing = playing,
                    unavailable = state.timelineFailed,
                    onStep = viewModel::setStep,
                    onTogglePlay = {
                        if (!playing && state.step >= state.steps.lastStep) viewModel.setStep(0)
                        playing = !playing
                    },
                    onRetry = { viewModel.loadTimeline(force = true) },
                )
                if (selected != null && !wide) {
                    SpotPanel(
                        spot = selected,
                        reading = state.selectedReading,
                        step = state.step,
                        favourite = selected.wgId in state.favourites,
                        now = state.now,
                        onOpen = { navigator.navigate(SpotRoute(selected.wgId)) },
                        onToggleFavourite = { viewModel.toggleFavourite(selected.wgId) },
                        onNavigate = { Intents.navigate(context, selected) },
                        onClose = {
                            viewModel.select(null)
                            scope.launch { sheetState.partialExpand() }
                        },
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                MapLibreView(
                    initialCamera = camera,
                    onReady = { controller = it },
                    onCameraMoveStarted = { moving = true },
                    onCameraIdle = {
                        camera = it
                        moving = false
                        cameraVersion++
                    },
                    onTap = { point -> viewModel.select(controller?.handleTap(point)) },
                    attributionMarginBottomPx = with(density) { (PEEK_HEIGHT + 8.dp).roundToPx() },
                    modifier = Modifier.fillMaxSize(),
                )

                if (state.settings.mapWindField) {
                    val samples = remember(state.features) {
                        state.features.mapNotNull { f ->
                            f.wind?.let { WindSample(f.lat, f.lon, it.toDouble(), Direction.fromIndex(f.dirIndex)) }
                        }
                    }
                    val quality = state.settings.particleQuality
                    val mode = when {
                        quality == ParticleQuality.OFF -> ParticleMode.OFF
                        // Battery saver turns the particles off unless the user forced them (spec 6.4)
                        powerSave && quality != ParticleQuality.HIGH -> ParticleMode.OFF
                        reduceMotion -> ParticleMode.STATIC
                        else -> ParticleMode.ANIMATED
                    }
                    val scale = when (quality) {
                        ParticleQuality.LOW -> 0.4f
                        ParticleQuality.HIGH -> 1f
                        else -> 0.75f
                    }
                    WindFieldOverlay(
                        controller = controller,
                        samples = samples,
                        cameraVersion = cameraVersion,
                        moving = moving,
                        particleMode = mode,
                        particleScale = scale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                TopChrome(
                    state = state,
                    onSearch = { navigator.navigate(SearchRoute) },
                    onCountry = { showCountries = true },
                    onFavourites = viewModel::toggleFavouritesOnly,
                    onLive = viewModel::toggleLiveOnly,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = if (wide && selected != null) 376.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(onClick = {
                        if (viewModel.hasLocationPermission()) {
                            scope.launch { centreOnMe(viewModel, controller, snackbar, context) }
                        } else {
                            // Asked here, at the moment it is needed - never on launch
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }) { Icon(Icons.Rounded.MyLocation, stringResource(R.string.map_my_location)) }
                    Box {
                        SmallFloatingActionButton(onClick = { showLayers = true }) {
                            Icon(Icons.Rounded.Layers, stringResource(R.string.map_layers))
                        }
                        LayersMenu(
                            expanded = showLayers,
                            windField = state.settings.mapWindField,
                            markers = state.settings.mapMarkers,
                            satellite = satellite,
                            onDismiss = { showLayers = false },
                            onWindField = viewModel::setWindField,
                            onMarkers = viewModel::setMarkers,
                            onSatellite = viewModel::setSatellite,
                        )
                    }
                    val noFavourites = stringResource(R.string.map_no_favourites)
                    SmallFloatingActionButton(onClick = {
                        val points = state.allSpots.filter { it.wgId in state.favourites }.mapNotNull { it.coordinates }
                        if (points.isEmpty()) {
                            scope.launch { snackbar.showSnackbar(noFavourites) }
                        } else {
                            controller?.fit(points, with(density) { 64.dp.roundToPx() })
                        }
                    }) { Icon(Icons.Rounded.Star, stringResource(R.string.map_fit_favourites)) }
                }

                if (wide && selected != null) {
                    // Expanded width: a side sheet instead of the bottom one (spec 9.6)
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(360.dp)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .padding(bottom = PEEK_HEIGHT),
                    ) {
                        SpotPanel(
                            spot = selected,
                            reading = state.selectedReading,
                            step = state.step,
                            favourite = selected.wgId in state.favourites,
                            now = state.now,
                            onOpen = { navigator.navigate(SpotRoute(selected.wgId)) },
                            onToggleFavourite = { viewModel.toggleFavourite(selected.wgId) },
                            onNavigate = { Intents.navigate(context, selected) },
                            onClose = { viewModel.select(null) },
                        )
                    }
                }
            }
        }
    }

    if (showCountries) {
        CountrySheet(
            spots = state.allSpots,
            selected = state.filters.country,
            onSelect = {
                viewModel.setCountry(it)
                showCountries = false
                val points = state.allSpots.filter { spot -> it == null || spot.country == it }.mapNotNull { spot -> spot.coordinates }
                if (it != null) controller?.fit(points, with(density) { 64.dp.roundToPx() })
            },
            onDismiss = { showCountries = false },
        )
    }
}

private suspend fun centreOnMe(
    viewModel: MapViewModel,
    controller: MapController?,
    snackbar: SnackbarHostState,
    context: Context,
) {
    val location = viewModel.currentLocation()
    if (location == null) {
        snackbar.showSnackbar(context.getString(R.string.map_location_unavailable))
    } else {
        controller?.centreOn(location, zoom = 8.0)
    }
}

/** Search field (opens the search screen) and the filter chips. */
@Composable
private fun TopChrome(
    state: MapUiState,
    onSearch: () -> Unit,
    onCountry: () -> Unit,
    onFavourites: () -> Unit,
    onLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onSearch),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.map_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val chipColors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
            FilterChip(
                selected = state.filters.country != null,
                onClick = onCountry,
                label = {
                    Text(
                        state.filters.country?.let { countryName(it) } ?: stringResource(R.string.map_filter_country_all),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Public, null, Modifier.size(FilterChipDefaults.IconSize)) },
                colors = chipColors,
            )
            FilterChip(
                selected = state.filters.favouritesOnly,
                onClick = onFavourites,
                label = { Text(stringResource(R.string.map_filter_favourites)) },
                leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(FilterChipDefaults.IconSize)) },
                colors = chipColors,
            )
            FilterChip(
                selected = state.filters.liveOnly,
                onClick = onLive,
                label = { Text(stringResource(R.string.map_filter_live)) },
                leadingIcon = { Icon(Icons.Rounded.Sensors, null, Modifier.size(FilterChipDefaults.IconSize)) },
                colors = chipColors,
            )
        }
        if (state.settings.mapWindField) {
            Text(
                stringResource(R.string.map_wind_field_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable(enabled = false) {},
            )
        }
    }
}

@Composable
private fun LayersMenu(
    expanded: Boolean,
    windField: Boolean,
    markers: Boolean,
    satellite: Boolean,
    onDismiss: () -> Unit,
    onWindField: (Boolean) -> Unit,
    onMarkers: (Boolean) -> Unit,
    onSatellite: (Boolean) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        CheckItem(stringResource(R.string.map_wind_field), windField) { onWindField(!windField) }
        CheckItem(stringResource(R.string.map_spots_layer), markers) { onMarkers(!markers) }
        CheckItem(stringResource(R.string.map_satellite), satellite) { onSatellite(!satellite) }
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = { if (checked) Icon(Icons.Rounded.Check, contentDescription = null) },
    )
}

/** Battery saver state, kept current while the map is on screen. */
@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var saving by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                saving = power?.isPowerSaveMode == true
            }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}
