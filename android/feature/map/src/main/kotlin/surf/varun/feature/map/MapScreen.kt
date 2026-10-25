package surf.varun.feature.map

import surf.varun.core.ui.MapStyles
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.PowerManager
import android.view.View
import android.widget.ProgressBar
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.maps.Style
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.model.Coordinates
import surf.varun.core.model.Direction
import surf.varun.core.model.ParticleQuality
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.navigation.MapRoute
import surf.varun.core.navigation.SpotRoute
import surf.varun.core.ui.Intents
import surf.varun.core.ui.LocalReduceMotion
import surf.varun.core.ui.countryName
import surf.varun.core.ui.rememberScreenSettled

private val PEEK_HEIGHT = 156.dp
private val SIDE_SHEET_BREAKPOINT = 840.dp
private const val PLAY_STEP_MS = 650L

/** Close enough to see the spots round the user, not so close that none are left in view. */
private const val MY_LOCATION_ZOOM = 8.0

/** Longest the cover waits for the map's first complete frame. */
private const val MAP_REVEAL_TIMEOUT_MS = 3_000L

/** How long the cover takes to fade off a drawn map. */
private const val MAP_REVEAL_MS = 250


/** Europe, where most spots are, when there is nothing better to show. */
private val DEFAULT_CAMERA = SavedCamera(lat = 48.0, lon = 10.0, zoom = 3.3)

private val cameraSaver = listSaver<SavedCamera, Double>(
    save = { listOf(it.lat, it.lon, it.zoom) },
    restore = { SavedCamera(it[0], it[1], it[2]) },
)

private val globeCameraSaver = listSaver<GlobeCamera, Double>(
    save = { listOf(it.lat, it.lon, it.leafletZoom) },
    restore = { GlobeCamera(it[0], it[1], it[2]) },
)

/** A MapLibre zoom `z` is Leaflet zoom `z + 1`, which is what the globe's zoom is kept in. */
private const val GLOBE_ZOOM_OFFSET = 1.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(route: MapRoute, viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
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
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var focused by rememberSaveable { mutableStateOf(false) }
    val powerSave = rememberPowerSaveMode()

    // The globe takes over from the map while it is on; the map's camera is handed
    // across both ways, so switching keeps looking at the same place
    val globe = state.settings.mapGlobe
    val globeCamera = rememberSaveable(saver = globeCameraSaver) {
        GlobeCamera(camera.lat, camera.lon, camera.zoom + GLOBE_ZOOM_OFFSET).apply { introPending = true }
    }
    SideEffect { globeCamera.reduceMotion = reduceMotion }
    LaunchedEffect(globe) { if (globe) controller = null }

    // MapLibre draws into a SurfaceView: costly to create, and it ignores the screen's
    // fade, so it would pop in mid-transition and stutter it. It is made once the screen
    // has settled and stays under a cover until it has drawn its first frame
    val settled = rememberScreenSettled()
    var mapCreated by remember { mutableStateOf(false) }
    var mapDrawn by remember { mutableStateOf(false) }
    LaunchedEffect(settled) { if (settled) mapCreated = true }
    LaunchedEffect(globe) { if (globe) mapDrawn = false }
    LaunchedEffect(mapCreated, globe) {
        // Tiles that never finish (offline, a slow server) must not hold the map back for good
        if (mapCreated && !globe) {
            delay(MAP_REVEAL_TIMEOUT_MS)
            mapDrawn = true
        }
    }

    fun centreOn(coordinates: Coordinates, zoom: Double = MapController.FOCUS_ZOOM) {
        if (globe) {
            globeCamera.introPending = false
            globeCamera.centreOn(coordinates.lat, coordinates.lon, zoom + GLOBE_ZOOM_OFFSET, System.nanoTime() / 1_000_000)
        } else {
            controller?.centreOn(coordinates, zoom)
        }
    }

    fun fit(points: List<Coordinates>) {
        if (globe) {
            globeCamera.introPending = false
            globeCamera.fit(
                points.map { it.lat to it.lon },
                GlobeCamera.FILTER_FIT_PADDING,
                GlobeCamera.FILTER_FIT_MAX_ZOOM,
                System.nanoTime() / 1_000_000,
            )
        } else {
            controller?.fit(points, with(density) { 64.dp.roundToPx() })
        }
    }

    fun toggleGlobe() {
        if (globe) {
            camera = SavedCamera(globeCamera.lat, globeCamera.lon, (globeCamera.leafletZoom - GLOBE_ZOOM_OFFSET).coerceAtLeast(0.0))
        } else {
            globeCamera.jumpTo(camera.lat, camera.lon, camera.zoom + GLOBE_ZOOM_OFFSET)
            globeCamera.introPending = true
            // The map view is about to go: nothing may reach its controller any more
            controller = null
        }
        viewModel.setGlobe(!globe)
    }

    // Refresh only while visible (spec 6.1)
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() } }

    // Satellite by default, the normal map on request; neither follows the dark theme
    val satellite = state.settings.mapSatellite
    LaunchedEffect(controller, satellite) {
        val map = controller ?: return@LaunchedEffect
        val builder = if (satellite) Style.Builder().fromJson(MapStyles.satelliteJson) else Style.Builder().fromUri(MapStyles.STREET)
        map.setStyle(builder, windColors) { cameraVersion++ }
    }
    LaunchedEffect(controller, state.features) { controller?.setFeatures(state.features) }
    LaunchedEffect(controller, state.settings.mapMarkers) { controller?.setMarkersVisible(state.settings.mapMarkers) }
    LaunchedEffect(controller, myLocation.position) { controller?.setMyLocation(myLocation.position) }

    // Found: the map is unblocked and flies to the user; not found: unblocked with a message
    val locationUnavailable = stringResource(R.string.map_location_unavailable)
    val onLocated by rememberUpdatedState<(LocateResult) -> Unit> { result ->
        when (result) {
            is LocateResult.Found -> centreOn(result.position, zoom = MY_LOCATION_ZOOM)
            LocateResult.Failed -> scope.launch { snackbar.showSnackbar(locationUnavailable) }
        }
    }
    LaunchedEffect(viewModel) { viewModel.locateResults.collect { onLocated(it) } }
    // A search still running when the map is left is dropped, never landed later on a map the user moved
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancelLocate() }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelLocate() } }
    // Back gets the user out of a search that takes too long
    BackHandler(enabled = myLocation.locating) { viewModel.cancelLocate() }

    // A MapRoute(focusWgId) centres and opens that spot once
    LaunchedEffect(controller, globe, state.loading, route.focusWgId, state.allSpots) {
        val id = route.focusWgId ?: return@LaunchedEffect
        if (state.loading || (!globe && controller == null)) return@LaunchedEffect
        if (focused) return@LaunchedEffect
        val spot = state.allSpots.firstOrNull { it.wgId == id } ?: return@LaunchedEffect
        spot.coordinates?.let { centreOn(it) }
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
        if (granted) viewModel.locate()
    }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState, snackbarHostState = snackbar)

    // Hidden, the timeline leaves no peeking sheet: a spot opened on a phone still slides it up
    val timeline = state.settings.mapTimeline
    val peek = if (timeline) PEEK_HEIGHT else 0.dp
    LaunchedEffect(timeline) { if (!timeline) playing = false }

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
            sheetPeekHeight = peek,
            snackbarHost = { SnackbarHost(snackbar) },
            sheetContent = {
                if (timeline) TimelineBar(
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

                if (state.loading) {
                    // Settings not read yet: neither the map nor the globe until it's known which one
                } else if (globe) {
                    GlobeView(
                        camera = globeCamera,
                        features = state.features,
                        samples = samples,
                        selectedWgId = selected?.wgId,
                        myLocation = myLocation.position,
                        windVisible = state.settings.mapWindField,
                        markersVisible = state.settings.mapMarkers,
                        particleMode = mode,
                        particleScale = scale,
                        // Battery saver and reduced motion keep the globe still
                        spin = state.settings.mapGlobeSpin && !powerSave && !reduceMotion,
                        bottomInset = peek,
                        onSelect = viewModel::select,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (mapCreated) {
                    MapLibreView(
                        initialCamera = camera,
                        onReady = { controller = it },
                        onFirstRender = { mapDrawn = true },
                        // Leaving: gone in the frame the transition starts, not after it
                        surfaceVisible = settled,
                        onCameraMoveStarted = { moving = true },
                        onCameraIdle = {
                            camera = it
                            moving = false
                            cameraVersion++
                        },
                        onTap = { point -> viewModel.select(controller?.handleTap(point)) },
                        attributionMarginBottomPx = with(density) { (peek + 8.dp).roundToPx() },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Leaves with the map's surface: over nothing, its cut-outs round the markers show as black discs
                    if (state.settings.mapWindField && settled) {
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
                }

                if (!globe) {
                    // Over the map and its wind field; the controls stay usable above it
                    MapCover(
                        covered = state.loading || !mapDrawn,
                        satellite = state.settings.mapSatellite,
                        bottomInset = peek,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (state.settings.mapWindField) {
                    Text(
                        stringResource(R.string.map_wind_field_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
                    )
                }

                val noFavourites = stringResource(R.string.map_no_favourites)
                MapControls(
                    state = state,
                    showLayers = showLayers,
                    globe = globe,
                    onSearch = { showSearch = true },
                    onCountry = { showCountries = true },
                    onFavourites = {
                        val points = state.allSpots.filter { it.wgId in state.favourites }.mapNotNull { it.coordinates }
                        when {
                            state.filters.favouritesOnly -> viewModel.toggleFavouritesOnly()
                            points.isEmpty() -> scope.launch { snackbar.showSnackbar(noFavourites) }
                            else -> {
                                viewModel.toggleFavouritesOnly()
                                fit(points)
                            }
                        }
                    },
                    onLive = viewModel::toggleLiveOnly,
                    onGlobe = ::toggleGlobe,
                    onLayers = { showLayers = it },
                    onMyLocation = {
                        if (viewModel.hasLocationPermission()) {
                            viewModel.locate()
                        } else {
                            // Asked here, at the moment it is needed - never on launch
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    onZoom = {
                        if (globe) globeCamera.zoomBy(it, System.nanoTime() / 1_000_000) else controller?.zoomBy(it)
                    },
                    onResetView = {
                        if (globe) {
                            globeCamera.introPending = false
                            globeCamera.reset(System.nanoTime() / 1_000_000)
                        } else {
                            controller?.centreOn(Coordinates(DEFAULT_CAMERA.lat, DEFAULT_CAMERA.lon), DEFAULT_CAMERA.zoom)
                        }
                    },
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = if (wide && selected != null) 376.dp else 12.dp),
                )

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
                            .padding(bottom = peek),
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

        // Over everything, the timeline sheet and the controls included: nothing moves the map
        // while the user is being found, so the result never lands on a view they changed
        LocatingOverlay(visible = myLocation.locating, modifier = Modifier.fillMaxSize())
    }

    if (showSearch) {
        SpotSearchSheet(
            spots = state.spots,
            onPick = { spot ->
                showSearch = false
                // Deep enough that the spot stands on its own rather than in a
                // cluster, but never zoomed back out from a closer look
                spot.coordinates?.let { centreOn(it, zoom = maxOf(camera.zoom, MapController.FOCUS_ZOOM)) }
                viewModel.select(spot.wgId)
            },
            onDismiss = { showSearch = false },
        )
    }

    if (showCountries) {
        CountrySheet(
            spots = state.allSpots,
            selected = state.filters.country,
            onSelect = {
                viewModel.setCountry(it)
                showCountries = false
                val points = state.allSpots.filter { spot -> it == null || spot.country == it }.mapNotNull { spot -> spot.coordinates }
                if (it != null) fit(points)
            },
            onDismiss = { showCountries = false },
        )
    }
}

/** A dimmed screen that swallows every touch while the user's location is being found. */
@Composable
private fun LocatingOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val message = stringResource(R.string.map_locating)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = message
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(message, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Every map control in one column on the right: filters (search, country,
 * favourites, live stations), then the globe switch, layers and location, then
 * zoom and the reset to the opening view. Filters that are on (and the globe)
 * are filled with the accent colour.
 */
@Composable
private fun MapControls(
    state: MapUiState,
    showLayers: Boolean,
    globe: Boolean,
    onSearch: () -> Unit,
    onCountry: () -> Unit,
    onFavourites: () -> Unit,
    onLive: () -> Unit,
    onGlobe: () -> Unit,
    onLayers: (Boolean) -> Unit,
    onMyLocation: () -> Unit,
    onZoom: (Double) -> Unit,
    onResetView: () -> Unit,
    viewModel: MapViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlButton(Icons.Rounded.Search, stringResource(R.string.map_search), onSearch)
        ControlButton(
            Icons.Rounded.Flag,
            state.filters.country?.let { countryName(it) } ?: stringResource(R.string.map_filter_country_all),
            onCountry,
            active = state.filters.country != null,
        )
        ControlButton(
            if (state.filters.favouritesOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            stringResource(R.string.map_filter_favourites),
            onFavourites,
            active = state.filters.favouritesOnly,
        )
        ControlButton(Icons.Rounded.Sensors, stringResource(R.string.map_filter_live), onLive, active = state.filters.liveOnly)

        Spacer(Modifier.height(8.dp))
        ControlButton(Icons.Rounded.Public, stringResource(R.string.map_globe), onGlobe, active = globe)
        Box {
            ControlButton(Icons.Rounded.Layers, stringResource(R.string.map_layers), { onLayers(true) })
            LayersMenu(
                expanded = showLayers,
                globe = globe,
                windField = state.settings.mapWindField,
                markers = state.settings.mapMarkers,
                satellite = state.settings.mapSatellite,
                spin = state.settings.mapGlobeSpin,
                timeline = state.settings.mapTimeline,
                onDismiss = { onLayers(false) },
                onWindField = viewModel::setWindField,
                onMarkers = viewModel::setMarkers,
                onSatellite = viewModel::setSatellite,
                onSpin = viewModel::setGlobeSpin,
                onTimeline = viewModel::setTimeline,
            )
        }
        ControlButton(Icons.Rounded.MyLocation, stringResource(R.string.map_my_location), onMyLocation)

        Spacer(Modifier.height(8.dp))
        ControlButton(Icons.Rounded.Add, stringResource(R.string.map_zoom_in), { onZoom(1.0) })
        ControlButton(Icons.Rounded.Remove, stringResource(R.string.map_zoom_out), { onZoom(-1.0) })
        ControlButton(Icons.Rounded.Home, stringResource(R.string.map_reset_view), onResetView)
    }
}

/** A small map button; [active] marks a filter that is on, for TalkBack as well as visually. */
@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit, active: Boolean? = null) {
    val on = active == true
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = if (active != null) Modifier.semantics { selected = on } else Modifier,
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** Layers; the globe has no basemap to switch, so it offers its idle spin instead of satellite. */
@Composable
private fun LayersMenu(
    expanded: Boolean,
    globe: Boolean,
    windField: Boolean,
    markers: Boolean,
    satellite: Boolean,
    spin: Boolean,
    timeline: Boolean,
    onDismiss: () -> Unit,
    onWindField: (Boolean) -> Unit,
    onMarkers: (Boolean) -> Unit,
    onSatellite: (Boolean) -> Unit,
    onSpin: (Boolean) -> Unit,
    onTimeline: (Boolean) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        CheckItem(stringResource(R.string.map_wind_field), windField) { onWindField(!windField) }
        CheckItem(stringResource(R.string.map_spots_layer), markers) { onMarkers(!markers) }
        CheckItem(stringResource(R.string.map_timeline_layer), timeline) { onTimeline(!timeline) }
        if (globe) {
            CheckItem(stringResource(R.string.map_globe_spin), spin) { onSpin(!spin) }
        } else {
            CheckItem(stringResource(R.string.map_satellite), satellite) { onSatellite(!satellite) }
        }
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

/**
 * Stands in for the map while its GL surface is made and draws its first frame.
 * Coloured like the basemap underneath, so lifting it reads as the map sharpening, not
 * swapping. Leaving needs no cover: the surface is hidden then instead.
 */
@Composable
private fun MapCover(covered: Boolean, satellite: Boolean, bottomInset: Dp, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(covered) {
        if (covered) alpha.snapTo(1f) else alpha.animateTo(0f, tween(MAP_REVEAL_MS))
    }
    if (alpha.value == 0f) return
    val tint = if (satellite) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .background(if (satellite) SATELLITE_COVER else STREET_COVER),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = bottomInset),
        ) {
            // The platform spinner, not Compose's: its animated vector runs on the render
            // thread and keeps turning while the main thread is busy building the map
            AndroidView(
                factory = { context ->
                    ProgressBar(context).apply {
                        isIndeterminate = true
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                },
                update = { it.indeterminateTintList = ColorStateList.valueOf(tint.toArgb()) },
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.map_loading),
                style = MaterialTheme.typography.labelLarge,
                color = if (satellite) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Deep ocean, what most of the satellite basemap is at the zooms the map opens on. */
private val SATELLITE_COVER = Color(0xFF0D2A3C)

/** The street basemap's land colour. */
private val STREET_COVER = Color(0xFFF2EFE9)
