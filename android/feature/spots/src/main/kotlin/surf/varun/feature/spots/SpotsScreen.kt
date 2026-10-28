package surf.varun.feature.spots

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import surf.varun.core.designsystem.component.EmptyState
import surf.varun.core.domain.Countries
import surf.varun.core.navigation.AlertEditorRoute
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.navigation.SearchRoute
import surf.varun.core.navigation.SpotRoute
import surf.varun.core.ui.ForecastProgressBanner
import surf.varun.core.ui.LocalFloatingNavigationGutter
import surf.varun.core.ui.R as UiR
import surf.varun.core.ui.SpotCard
import surf.varun.core.ui.SpotCardModel
import surf.varun.core.ui.SyncBanner
import surf.varun.core.ui.countryName
import surf.varun.core.ui.isCompactHeight
import surf.varun.core.ui.sharedElement

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpotsScreen(viewModel: SpotsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCountries by rememberSaveable { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() } }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SpotsEffect.Favourite -> snackbar.showSnackbar(
                    resources.getString(if (effect.added) UiR.string.favourite_added else UiR.string.favourite_removed),
                )
            }
        }
    }

    // Sideways the screen has no height to spare: the chips join the search field in
    // the top bar instead of taking a row of their own, and the cards go two abreast
    val compact = isCompactHeight()
    val columns = if (compact) 2 else 1
    val chips = @Composable { modifier: Modifier ->
        FilterChips(
            state = state,
            onCountry = { showCountries = true },
            onSort = viewModel::setSort,
            onLiveOnly = viewModel::toggleLiveOnly,
            modifier = modifier,
        )
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                // Right of the floating navigation's button; the list below runs under it
                val gutter = LocalFloatingNavigationGutter.current
                Row(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(start = gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(
                        onClick = { navigator.navigate(SearchRoute) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                start = if (gutter > 0.dp) 0.dp else 16.dp,
                                end = if (compact) 0.dp else 16.dp,
                                top = 8.dp,
                                bottom = 4.dp,
                            ),
                    )
                    if (compact) chips(Modifier.weight(1.4f))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.sync.refreshing && state.hasCache,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading || (!state.hasCache && state.sync.refreshing) -> Loading()
                !state.hasCache -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.offline_empty_title),
                    message = stringResource(R.string.offline_empty_message),
                    modifier = Modifier.fillMaxSize(),
                    action = { TextButton(onClick = viewModel::refresh) { Text(stringResource(UiR.string.retry)) } },
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                ) {
                    item(key = "banners", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            ForecastProgressBanner(state.progress)
                            SyncBanner(state.sync, state.online, onRetry = viewModel::refresh)
                        }
                    }
                    if (!compact) {
                        stickyHeader(key = "chips") {
                            Surface(color = MaterialTheme.colorScheme.background) { chips(Modifier) }
                        }
                    }
                    if (state.items.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                icon = Icons.Rounded.SearchOff,
                                title = stringResource(R.string.no_spots_title),
                                message = stringResource(R.string.no_spots_message),
                                action = { TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.clear_filters)) } },
                            )
                        }
                    }
                    itemsIndexed(state.items, key = { _, model -> model.spot.wgId }) { index, model ->
                        // Side by side, the 16 dp edge stays on the outside and the pair sits 8 dp apart
                        val column = index % columns
                        val start = if (column == 0) 16.dp else 4.dp
                        val end = if (column == columns - 1) 16.dp else 4.dp
                        CardWithMenu(
                            model = model,
                            onOpen = { navigator.navigate(SpotRoute(model.spot.wgId)) },
                            onToggleFavourite = {
                                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                viewModel.toggleFavourite(model.spot.wgId)
                            },
                            onCreateAlert = { navigator.navigate(AlertEditorRoute(wgId = model.spot.wgId)) },
                            modifier = Modifier.padding(start = start, end = end).animateItem(),
                        )
                    }
                    item(key = "sponsors", span = { GridItemSpan(maxLineSpan) }) { SponsorsRow(state.sponsors) }
                }
            }
        }
    }

    if (showCountries) {
        CountrySheet(
            countries = state.countries,
            selected = state.filters.country,
            total = state.countries.sumOf { it.count },
            onSelect = {
                viewModel.setCountry(it)
                showCountries = false
            },
            onDismiss = { showCountries = false },
        )
    }
}

@Composable
internal fun CardWithMenu(
    model: SpotCardModel,
    onOpen: () -> Unit,
    onToggleFavourite: () -> Unit,
    onCreateAlert: () -> Unit,
    modifier: Modifier = Modifier,
    menuEnabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier) {
        SpotCard(
            model = model,
            onClick = onOpen,
            onToggleFavourite = onToggleFavourite,
            // Off while reordering: a long press on the drag handle would open the menu and take the gesture
            onLongClick = if (menuEnabled) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                }
            } else {
                null
            },
            trailing = trailing,
            modifier = Modifier.sharedElement("spot-card-${model.spot.wgId}"),
        )
        SpotContextMenu(model, menu && menuEnabled, onDismiss = { menu = false }, onToggleFavourite = onToggleFavourite, onCreateAlert = onCreateAlert)
    }
}

@Composable
private fun FilterChips(
    state: SpotsUiState,
    onCountry: () -> Unit,
    onSort: (SpotSort) -> Unit,
    onLiveOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Never scrolled: the text chips split what the live chip leaves, filling the row, and truncate their labels
        val country = state.filters.country
        FilterChip(
            selected = country != null,
            onClick = onCountry,
            label = {
                Text(
                    // "All", not "All countries": the globe beside it already says what the chip filters
                    if (country == null) stringResource(R.string.country_all_short)
                    else "${Countries.of(country).flag} ${countryName(country)}".trim(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.weight(1f),
            leadingIcon = if (country == null) ({ Icon(Icons.Rounded.Public, null) }) else null,
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
        )
        Box(Modifier.weight(1f)) {
            FilterChip(
                selected = state.sort != SpotSort.FIRING,
                onClick = { sortMenu = true },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(state.sort.label()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.sort_label)) },
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SpotSort.entries
                    .filter { it != SpotSort.DISTANCE || state.locationKnown }
                    .forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sort.label())) },
                            onClick = {
                                sortMenu = false
                                onSort(sort)
                            },
                        )
                    }
            }
        }
        // The map's live-stations icon; the text stays as its TalkBack label
        FilterChip(
            selected = state.filters.liveOnly,
            onClick = onLiveOnly,
            label = { Icon(Icons.Rounded.Sensors, contentDescription = stringResource(R.string.filter_live)) },
        )
    }
}

private fun SpotSort.label() = when (this) {
    SpotSort.FIRING -> R.string.sort_firing
    SpotSort.NAME -> R.string.sort_name
    SpotSort.WINDIEST_TODAY -> R.string.sort_windiest_today
    SpotSort.DISTANCE -> R.string.sort_distance
}

@Composable
internal fun Loading() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading_spots), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
