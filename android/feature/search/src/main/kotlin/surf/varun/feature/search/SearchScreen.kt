package surf.varun.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import surf.varun.core.designsystem.component.EmptyState
import surf.varun.core.designsystem.component.SectionHeader
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.domain.Countries
import surf.varun.core.model.Spot
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.navigation.SpotRoute
import surf.varun.core.ui.countryName

@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun open(spot: Spot) {
        if (query.isNotBlank()) viewModel.onResultOpened()
        navigator.navigate(SpotRoute(spot.wgId))
    }

    Scaffold(
        topBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.search_back))
                    }
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { state.results.firstOrNull()?.let(::open) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                }
                HorizontalDivider()
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (query.isBlank()) {
                if (state.recentSearches.isNotEmpty()) {
                    item(key = "searches-header") {
                        SectionHeader(stringResource(R.string.recent_searches)) {
                            TextButton(onClick = viewModel::clearHistory) { Text(stringResource(R.string.clear_history)) }
                        }
                    }
                    items(state.recentSearches, key = { "q-$it" }) { recent ->
                        ListItem(
                            colors = transparentListItemColors(),
                            headlineContent = { Text(recent) },
                            leadingContent = { Icon(Icons.Rounded.History, null) },
                            modifier = Modifier.clickable { viewModel.onQueryChange(recent) },
                        )
                    }
                }
                if (state.recentSpots.isNotEmpty()) {
                    item(key = "spots-header") { SectionHeader(stringResource(R.string.recent_spots)) }
                    items(state.recentSpots, key = { "r-${it.wgId}" }) { spot -> SpotRow(spot) { open(spot) } }
                }
                if (!state.hasSpots) {
                    item(key = "no-spots") { ListItem(colors = transparentListItemColors(), headlineContent = { Text(stringResource(R.string.no_spots_cached)) }) }
                }
            } else if (state.results.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.no_results_title),
                        message = stringResource(R.string.no_results_message, query),
                    )
                }
            } else {
                item(key = "results-header") { SectionHeader(stringResource(R.string.results)) }
                items(state.results, key = { it.wgId }) { spot -> SpotRow(spot) { open(spot) } }
            }
        }
    }
}

@Composable
private fun SpotRow(spot: Spot, onClick: () -> Unit) {
    ListItem(
        colors = transparentListItemColors(),
        headlineContent = { Text(spot.name) },
        supportingContent = { Text("${Countries.of(spot.country).flag} ${countryName(spot.country)}".trim()) },
        leadingContent = { Icon(Icons.Rounded.Place, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
