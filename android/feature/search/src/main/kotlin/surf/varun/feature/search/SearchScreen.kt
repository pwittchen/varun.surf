package surf.varun.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import surf.varun.core.designsystem.component.EmptyState
import surf.varun.core.designsystem.component.SectionHeader
import surf.varun.core.domain.Countries
import surf.varun.core.model.Spot
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.navigation.SpotRoute
import surf.varun.core.ui.countryName

@OptIn(ExperimentalMaterial3Api::class)
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

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { state.results.firstOrNull()?.let(::open) },
                expanded = true,
                onExpandedChange = { if (!it) navigator.back() },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.search_back))
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                modifier = Modifier.focusRequester(focus),
            )
        },
        expanded = true,
        onExpandedChange = { if (!it) navigator.back() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isBlank()) {
                if (state.recentSearches.isNotEmpty()) {
                    item(key = "searches-header") {
                        SectionHeader(stringResource(R.string.recent_searches)) {
                            TextButton(onClick = viewModel::clearHistory) { Text(stringResource(R.string.clear_history)) }
                        }
                    }
                    items(state.recentSearches, key = { "q-$it" }) { recent ->
                        ListItem(
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
                    item(key = "no-spots") { ListItem(headlineContent = { Text(stringResource(R.string.no_spots_cached)) }) }
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
        headlineContent = { Text(spot.name) },
        supportingContent = { Text("${Countries.of(spot.country).flag} ${countryName(spot.country)}".trim()) },
        leadingContent = { Icon(Icons.Rounded.Place, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
