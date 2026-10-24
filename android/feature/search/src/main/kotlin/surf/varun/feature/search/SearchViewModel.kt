package surf.varun.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.RecentsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.domain.SpotSearch
import surf.varun.core.model.Spot
import javax.inject.Inject

data class SearchUiState(
    val results: ImmutableList<Spot> = persistentListOf(),
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val recentSpots: ImmutableList<Spot> = persistentListOf(),
    val hasSpots: Boolean = true,
)

/** Local, instant search over the cached spots list (spec 5.5) - no network. */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    spotsRepository: SpotsRepository,
    private val recentsRepository: RecentsRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val index = spotsRepository.spots
        .map { spots -> SpotSearch.index(spots) to spots }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<SearchUiState> = combine(
        index,
        _query.debounce(80),
        recentsRepository.recentSearches,
        recentsRepository.recentSpotIds,
    ) { (searchIndex, spots), query, searches, recentIds ->
        val byId = spots.associateBy { it.wgId }
        SearchUiState(
            results = SpotSearch.search(searchIndex, query, limit = 100).toImmutableList(),
            recentSearches = searches.toImmutableList(),
            recentSpots = recentIds.mapNotNull(byId::get).toImmutableList(),
            hasSpots = spots.isNotEmpty(),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Remember what led to a spot being opened. */
    fun onResultOpened() {
        val q = _query.value
        viewModelScope.launch { recentsRepository.searched(q) }
    }

    fun clearHistory() {
        viewModelScope.launch { recentsRepository.clearSearches() }
    }
}
