package surf.varun.feature.spot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Kitesurfing
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import surf.varun.core.designsystem.component.EmptyState
import surf.varun.core.domain.Conditions
import surf.varun.core.navigation.AlertEditorRoute
import surf.varun.core.navigation.CalculatorRoute
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.ui.FavouriteButton
import surf.varun.core.ui.Intents
import surf.varun.core.ui.LocalFloatingNavigationGutter
import surf.varun.core.ui.LocalUserSettings
import surf.varun.core.ui.R as CoreUiR
import surf.varun.core.ui.SyncBanner
import surf.varun.core.ui.isCompactHeight
import surf.varun.core.ui.isPolish
import surf.varun.core.ui.rememberNow
import surf.varun.core.ui.shareConditions
import surf.varun.core.ui.sharedElement

@Composable
fun SpotScreen(viewModel: SpotViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // 60 s refresh while visible, nothing in the background (spec 6.1)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.autoRefresh() }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SpotEffect.Message -> snackbar.showSnackbar(resources.getString(effect.text))
            }
        }
    }

    val language = if (isPolish()) "pl" else "en"
    SpotContent(
        state = state,
        snackbar = snackbar,
        actions = SpotActions(
            refresh = viewModel::refresh,
            toggleFavourite = viewModel::toggleFavourite,
            selectModel = viewModel::selectModel,
            setWindyOnly = viewModel::setWindyOnly,
            requestAnalysis = viewModel::requestAnalysis,
            confirm = { viewModel.confirm(language) },
            dismissConfirm = viewModel::dismissConfirm,
            removeEverywhere = viewModel::removeEverywhere,
            focusConsumed = viewModel::focusConsumed,
            calculatorWind = viewModel::calculatorWind,
        ),
    )
}

/** Callbacks of the screen, bundled so the content composable stays readable. */
class SpotActions(
    val refresh: () -> Unit,
    val toggleFavourite: () -> Unit,
    val selectModel: (String) -> Unit,
    val setWindyOnly: (Boolean) -> Unit,
    val requestAnalysis: () -> Unit,
    val confirm: () -> Unit,
    val dismissConfirm: () -> Unit,
    val removeEverywhere: () -> Unit,
    val focusConsumed: () -> Unit,
    val calculatorWind: () -> Double?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotContent(state: SpotUiState, snackbar: SnackbarHostState, actions: SpotActions) {
    val navigator = LocalNavigator.current
    // One header state per orientation: turned upright, the large header starts open
    // rather than half-collapsed by however far the sideways bar had scrolled. Sideways the
    // one-line bar stays put: there is nothing to collapse
    val compact = isCompactHeight()
    val scrollBehavior = key(compact) {
        if (compact) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val spot = state.spot

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SpotTopBar(state, scrollBehavior, actions) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            spot == null && state.notFound -> NotFound(state, actions, Modifier.padding(padding))
            spot == null && state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            spot == null -> {
                val offline = !state.online || state.sync.lastError is surf.varun.core.data.util.DataError.Offline
                EmptyState(
                    icon = if (offline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
                    title = stringResource(if (offline) R.string.offline_title else R.string.error_title),
                    message = stringResource(if (offline) R.string.offline_message else R.string.error_message),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    action = { Button(onClick = actions.refresh) { Text(stringResource(CoreUiR.string.retry)) } },
                )
            }
            else -> PullToRefreshBox(
                isRefreshing = state.sync.refreshing,
                onRefresh = actions.refresh,
                // Clear of a camera cut-out on the side too, now that no rail stands there
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                SpotBody(state, actions)
            }
        }
    }
}

@Composable
private fun SpotBody(state: SpotUiState, actions: SpotActions) {
    val spot = state.spot ?: return
    val now = rememberNow()
    val listState = rememberLazyListState()
    val compact = isCompactHeight()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "banner") { SyncBanner(state.sync, state.online, actions.refresh) }
        if (state.notFound) item(key = "notfound") { NotFound(state, actions, Modifier) }
        if (spot.live != null) item(key = "now") { NowSection(spot, now) }
        item(key = "forecast") { ForecastSection(state, actions) }
        if (compact) {
            // Sideways one column would leave most of every row empty: what is read (analysis,
            // the spot) goes left, where it is and the links out go right
            item(key = "columns") {
                Divided {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            AiSection(state, actions)
                            Divided { AboutSection(spot) }
                        }
                        Column(Modifier.weight(1f)) {
                            if (spot.coordinates != null) LocationSection(spot)
                            Divided { LinksSection(spot) }
                            if (spot.sponsors.isNotEmpty()) Divided { SponsorsSection(spot) }
                        }
                    }
                }
            }
        } else {
            item(key = "ai") { Divided { AiSection(state, actions) } }
            item(key = "about") { Divided { AboutSection(spot) } }
            if (spot.coordinates != null) item(key = "location") { Divided { LocationSection(spot) } }
            item(key = "links") { Divided { LinksSection(spot) } }
            if (spot.sponsors.isNotEmpty()) item(key = "sponsors") { Divided { SponsorsSection(spot) } }
        }
        item(key = "footer") { Footer(spot) }
    }
    ConfirmDialog(state.pendingConfirm, actions)
}

/** A section with a hairline above it, so the flat sections still read as separate. */
@Composable
private fun Divided(content: @Composable () -> Unit) {
    Column {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotTopBar(state: SpotUiState, scrollBehavior: TopAppBarScrollBehavior, actions: SpotActions) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val unit = LocalUserSettings.current.windUnit
    val spot = state.spot
    // Sideways there is no height for a picture: a one-line bar with the name and actions
    val compact = isCompactHeight()
    val photo = if (compact) null else spot?.photoUrl
    // A spot without a photo shows where it is instead: the satellite snapshot the
    // location section renders anyway, so it costs no extra tiles
    val mapImage = if (photo == null && !compact) rememberSpotMapImage(spot) else null
    val hasBackdrop = photo != null || mapImage != null
    val fraction = scrollBehavior.state.collapsedFraction
    val overPhoto = hasBackdrop && fraction < 0.6f
    val contentColor = if (overPhoto) Color.White else MaterialTheme.colorScheme.onSurface
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = 1f - scrollBehavior.state.collapsedFraction },
            )
        } else if (mapImage != null) {
            Image(
                mapImage,
                contentDescription = stringResource(R.string.mini_map_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = 1f - scrollBehavior.state.collapsedFraction },
            )
        }
        if (hasBackdrop) {
            // Dark behind the bar's icons and the title, then the picture melts into the
            // page over the strip under the title rather than ending on a hard edge
            val surface = MaterialTheme.colorScheme.surface
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = 1f - scrollBehavior.state.collapsedFraction }
                    .drawWithCache {
                        val fadeStart = (1f - (BackdropFade + TitleClearance).toPx() / size.height).coerceIn(0.4f, 1f)
                        val brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.3f to Color.Black.copy(alpha = 0.15f),
                            fadeStart to Color.Black.copy(alpha = 0.6f),
                            1f to surface,
                        )
                        onDrawBehind { drawRect(brush) }
                    },
            )
        }
        val title: @Composable () -> Unit = {
            Text(
                spot?.name.orEmpty(),
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedElement("spot-name-${state.wgId}"),
            )
        }
        val navigationIcon: @Composable () -> Unit = {
            IconButton(onClick = { navigator.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }
        val barActions: @Composable RowScope.() -> Unit = {
            if (spot != null) {
                FavouriteButton(state.favourite, actions.toggleFavourite)
                IconButton(onClick = { navigator.navigate(CalculatorRoute(windKnots = actions.calculatorWind())) }) {
                    Icon(Icons.Rounded.Kitesurfing, contentDescription = stringResource(R.string.kite_size_description))
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(CoreUiR.string.open_on_web)) },
                        leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                        onClick = { menuOpen = false; Intents.openUrl(context, Intents.spotWebUrl(spot.wgId)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(CoreUiR.string.create_alert)) },
                        leadingIcon = { Icon(Icons.Rounded.NotificationAdd, null) },
                        onClick = { menuOpen = false; navigator.navigate(AlertEditorRoute(wgId = spot.wgId)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(CoreUiR.string.navigate)) },
                        leadingIcon = { Icon(Icons.Rounded.Directions, null) },
                        onClick = { menuOpen = false; Intents.navigate(context, spot) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(CoreUiR.string.share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menuOpen = false
                            val reading = Conditions.current(spot, java.time.ZonedDateTime.now())
                            Intents.share(context, spot, shareConditions(context, reading, unit))
                        },
                    )
                }
            }
        }
        val colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (hasBackdrop) Color.Transparent else MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor,
        )
        // Clear of the floating navigation's button of a sideways phone
        val modifier = Modifier.padding(start = LocalFloatingNavigationGutter.current)
        if (compact) {
            TopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = barActions,
                colors = colors,
                modifier = modifier,
                scrollBehavior = scrollBehavior,
            )
        } else {
            Column {
                LargeTopAppBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = barActions,
                    colors = colors,
                    modifier = modifier,
                    scrollBehavior = scrollBehavior,
                )
                // Room under the title for the backdrop to fade out in, closing with the bar
                if (hasBackdrop) Spacer(Modifier.height(BackdropFade * (1f - fraction)))
            }
        }
    }
}

/** How far the photo or map runs on under the title while it fades into the page. */
private val BackdropFade = 32.dp

/** The large bar's space under the title's baseline, which the fade starts above. */
private val TitleClearance = 8.dp

@Composable
private fun NotFound(state: SpotUiState, actions: SpotActions, modifier: Modifier) {
    EmptyState(
        icon = Icons.Rounded.DeleteOutline,
        title = stringResource(R.string.not_found_title),
        message = stringResource(R.string.not_found_message),
        modifier = modifier.fillMaxWidth(),
        action = {
            Button(onClick = actions.removeEverywhere, enabled = !state.removedEverywhere) {
                Text(stringResource(if (state.removedEverywhere) R.string.not_found_removed else R.string.not_found_remove))
            }
        },
    )
}

