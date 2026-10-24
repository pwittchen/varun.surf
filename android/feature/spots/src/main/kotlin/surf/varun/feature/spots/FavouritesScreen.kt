package surf.varun.feature.spots

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import surf.varun.core.designsystem.component.EmptyState
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.navigation.AlertEditorRoute
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.navigation.SearchRoute
import surf.varun.core.navigation.SpotRoute
import surf.varun.core.ui.SyncBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(viewModel: FavouritesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() } }

    val drag = remember { DragState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favourites_title)) },
                actions = {
                    if (state.items.size > 1) {
                        TextButton(onClick = { viewModel.setReordering(!state.reordering) }) {
                            Text(stringResource(if (state.reordering) R.string.reorder_done else R.string.reorder))
                        }
                    }
                    IconButton(onClick = { navigator.navigate(SearchRoute) }) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search_hint))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.sync.refreshing && state.hasCache,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> Loading()
                state.items.isEmpty() && state.missing.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.StarBorder,
                    title = stringResource(R.string.no_favourites_title),
                    message = stringResource(R.string.no_favourites_message),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "banner") {
                        Column { SyncBanner(state.sync, state.online, onRetry = viewModel::refresh) }
                    }
                    items(state.items, key = { it.spot.wgId }) { model ->
                        val index = state.items.indexOf(model)
                        val dragging = drag.key == model.spot.wgId
                        val moveUp = stringResource(R.string.move_up)
                        val moveDown = stringResource(R.string.move_down)
                        CardWithMenu(
                            model = model,
                            onOpen = { navigator.navigate(SpotRoute(model.spot.wgId)) },
                            onToggleFavourite = {
                                haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
                                viewModel.remove(model.spot.wgId)
                            },
                            onCreateAlert = { navigator.navigate(AlertEditorRoute(wgId = model.spot.wgId)) },
                            trailing = if (state.reordering) {
                                {
                                    DragHandle(
                                        name = model.spot.name,
                                        wgId = model.spot.wgId,
                                        listState = listState,
                                        drag = drag,
                                        onMove = viewModel::move,
                                        onDrop = viewModel::commitOrder,
                                        indexOf = { id -> state.items.indexOfFirst { it.spot.wgId == id } },
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) drag.offset else 0f }
                                .then(if (dragging) Modifier else Modifier.animateItem())
                                .semantics {
                                    if (state.reordering) {
                                        customActions = listOfNotNull(
                                            CustomAccessibilityAction(moveUp) { viewModel.move(index, index - 1); viewModel.commitOrder(); true }
                                                .takeIf { index > 0 },
                                            CustomAccessibilityAction(moveDown) { viewModel.move(index, index + 1); viewModel.commitOrder(); true }
                                                .takeIf { index < state.items.lastIndex },
                                        )
                                    }
                                },
                        )
                    }
                    items(state.missing, key = { "missing-$it" }) { wgId ->
                        ListItem(
                            colors = transparentListItemColors(),
                            leadingContent = { Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
                            headlineContent = { Text(stringResource(R.string.favourite_missing)) },
                            supportingContent = { Text("#$wgId") },
                            trailingContent = {
                                TextButton(onClick = { viewModel.removeMissing(wgId) }) { Text(stringResource(R.string.remove)) }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The card being dragged and how far it has travelled from its slot. */
private class DragState {
    var key by mutableStateOf<Int?>(null)
    var offset by mutableFloatStateOf(0f)
}

/**
 * Press-and-drag handle (spec 5.2: reordering only in Favourites). The dragged
 * card follows the finger; when its centre crosses a neighbour the two swap and
 * the offset is corrected by the distance between their slots.
 */
@Composable
private fun DragHandle(
    name: String,
    wgId: Int,
    listState: LazyListState,
    drag: DragState,
    onMove: (Int, Int) -> Unit,
    onDrop: () -> Unit,
    indexOf: (Int) -> Int,
) {
    val haptics = LocalHapticFeedback.current
    val currentIndexOf by rememberUpdatedState(indexOf)
    Icon(
        Icons.Rounded.DragIndicator,
        contentDescription = stringResource(R.string.drag_handle, name),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(12.dp)
            .size(24.dp)
            .pointerInput(wgId) {
                detectDragGestures(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        drag.key = wgId
                        drag.offset = 0f
                    },
                    onDragEnd = {
                        drag.key = null
                        drag.offset = 0f
                        onDrop()
                    },
                    onDragCancel = {
                        drag.key = null
                        drag.offset = 0f
                        onDrop()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.offset += amount.y
                        val visible = listState.layoutInfo.visibleItemsInfo
                        val current = visible.firstOrNull { it.key == wgId } ?: return@detectDragGestures
                        val centre = current.offset + current.size / 2 + drag.offset
                        val target = visible.firstOrNull {
                            it.key is Int && it.key != wgId && centre.toInt() in it.offset..(it.offset + it.size)
                        } ?: return@detectDragGestures
                        val from = currentIndexOf(wgId)
                        val to = currentIndexOf(target.key as Int)
                        if (from >= 0 && to >= 0) {
                            onMove(from, to)
                            drag.offset += current.offset - target.offset
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    },
                )
            },
    )
}
