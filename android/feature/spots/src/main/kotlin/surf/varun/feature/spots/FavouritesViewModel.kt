package surf.varun.feature.spots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.util.ConnectivityMonitor
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.ui.SpotCardModel
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import javax.inject.Inject

data class FavouritesUiState(
    val items: ImmutableList<SpotCardModel> = persistentListOf(),
    /** Favourites the backend no longer has (removed from spots.json). */
    val missing: ImmutableList<Int> = persistentListOf(),
    val loading: Boolean = true,
    val hasCache: Boolean = false,
    val reordering: Boolean = false,
    val sync: SyncStatus = SyncStatus(),
    val online: Boolean = true,
    val now: ZonedDateTime = ZonedDateTime.now(),
)

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val spotsRepository: SpotsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val alertsRepository: AlertsRepository,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {
    private val reordering = MutableStateFlow(false)

    /** Order being dragged, before it is saved on drop. */
    private val draftOrder = MutableStateFlow<List<Int>?>(null)

    private val ticker = flow {
        while (true) {
            emit(ZonedDateTime.now(clock))
            delay(30_000)
        }
    }

    private val status = combine(spotsRepository.spotsSync, connectivity.isOnline.onStart { emit(true) }, reordering, draftOrder) { sync, online, r, draft ->
        Status(sync, online, r, draft)
    }

    val uiState: StateFlow<FavouritesUiState> = combine(
        spotsRepository.spots,
        favouritesRepository.favouriteIds,
        ticker,
        status,
    ) { spots, favouriteIds, now, status ->
        val byId = spots.associateBy { it.wgId }
        val order = status.draft ?: favouriteIds
        FavouritesUiState(
            items = order.mapNotNull { id -> byId[id]?.let { SpotCardModel.of(it, favourite = true, now = now) } }.toImmutableList(),
            missing = if (spots.isEmpty()) persistentListOf() else favouriteIds.filter { it !in byId }.toImmutableList(),
            loading = false,
            hasCache = spots.isNotEmpty(),
            reordering = status.reordering,
            sync = status.sync,
            online = status.online,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouritesUiState())

    /** While visible: the list (which carries the live readings) every 60 s. */
    suspend fun keepFresh() {
        while (true) {
            spotsRepository.refreshSpots(maxAge = Duration.ofMinutes(1))
            delay(60_000)
        }
    }

    fun refresh() {
        viewModelScope.launch { spotsRepository.refreshSpots() }
    }

    fun setReordering(enabled: Boolean) {
        reordering.value = enabled
        if (!enabled) commitOrder()
    }

    /** Move during a drag; saved by [commitOrder] when the finger lifts. */
    fun move(from: Int, to: Int) {
        val current = (draftOrder.value ?: uiState.value.items.map { it.spot.wgId }).toMutableList()
        if (from !in current.indices || to !in current.indices || from == to) return
        current.add(to, current.removeAt(from))
        draftOrder.value = current
    }

    fun commitOrder() {
        val draft = draftOrder.value ?: return
        viewModelScope.launch {
            // Favourites the backend no longer lists keep their place at the end
            favouritesRepository.reorder(draft + uiState.value.missing)
            draftOrder.value = null
        }
    }

    fun remove(wgId: Int) {
        viewModelScope.launch { favouritesRepository.setFavourite(wgId, false) }
    }

    /** A spot gone from the backend: drop it from favourites and its alerts (spec 6.2). */
    fun removeMissing(wgId: Int) {
        viewModelScope.launch {
            favouritesRepository.setFavourite(wgId, false)
            alertsRepository.deleteForSpot(wgId)
        }
    }

    private data class Status(val sync: SyncStatus, val online: Boolean, val reordering: Boolean, val draft: List<Int>?)
}
