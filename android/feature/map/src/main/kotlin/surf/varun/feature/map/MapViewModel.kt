package surf.varun.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.LocationRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.repository.WindTimelineRepository
import surf.varun.core.domain.WindReading
import surf.varun.core.model.Coordinates
import surf.varun.core.model.Spot
import surf.varun.core.model.UserSettings
import surf.varun.core.model.WindTimeline
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import javax.inject.Inject

data class MapUiState(
    val loading: Boolean = true,
    /** Every spot, for the country sheet's counts. */
    val allSpots: List<Spot> = emptyList(),
    /** Spots passing the filters - what the map draws. */
    val spots: List<Spot> = emptyList(),
    val features: List<MapSpotFeature> = emptyList(),
    val favourites: Set<Int> = emptySet(),
    val filters: MapFilters = MapFilters(),
    val step: Int = 0,
    val steps: TimelineSteps = TimelineSteps(emptyList()),
    val timeline: WindTimeline? = null,
    val timelineFailed: Boolean = false,
    val selected: Spot? = null,
    val selectedReading: WindReading? = null,
    val settings: UserSettings = UserSettings(),
    val now: ZonedDateTime = ZonedDateTime.now(),
)

data class MyLocationState(
    val position: Coordinates? = null,
    val locating: Boolean = false,
)

sealed interface LocateResult {
    data class Found(val position: Coordinates) : LocateResult

    data object Failed : LocateResult
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val spotsRepository: SpotsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val settingsRepository: SettingsRepository,
    private val windTimelineRepository: WindTimelineRepository,
    private val locationRepository: LocationRepository,
    private val clock: Clock,
) : ViewModel() {
    private val filters = MutableStateFlow(MapFilters())
    private val step = MutableStateFlow(0)
    private val selectedId = MutableStateFlow<Int?>(null)
    private val now = MutableStateFlow(ZonedDateTime.now(clock))
    private val timelineFailed = MutableStateFlow(false)

    private data class Inputs(
        val spots: List<Spot>,
        val favourites: Set<Int>,
        val settings: UserSettings,
        val timeline: WindTimeline?,
        val filters: MapFilters,
    )

    private val inputs = combine(
        spotsRepository.spots,
        favouritesRepository.favouriteIds.map { it.toSet() },
        settingsRepository.settings,
        windTimelineRepository.timeline.map { it?.timeline },
        filters,
    ) { spots, favourites, settings, timeline, filters -> Inputs(spots, favourites, settings, timeline, filters) }

    val uiState: StateFlow<MapUiState> = combine(
        inputs,
        step,
        selectedId,
        now,
        timelineFailed,
    ) { input, step, selectedId, now, failed ->
        val steps = TimelineSteps(input.timeline?.hours.orEmpty())
        val clamped = steps.clamp(step)
        val visible = input.filters.apply(input.spots, input.favourites)
        val selected = selectedId?.let { id -> input.spots.firstOrNull { it.wgId == id } }
        MapUiState(
            loading = false,
            allSpots = input.spots,
            spots = visible,
            features = MapFeatures.build(visible, clamped, input.timeline, now),
            favourites = input.favourites,
            filters = input.filters,
            step = clamped,
            steps = steps,
            timeline = input.timeline,
            timelineFailed = failed && input.timeline == null,
            selected = selected,
            selectedReading = selected?.let { conditionsAtStep(it, clamped, input.timeline, now) },
            settings = input.settings,
            now = now,
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        loadTimeline()
    }

    /** The grid reaches as far as the user asked (5 days or the whole run); the repository decides when to refetch. */
    fun loadTimeline(force: Boolean = false) {
        viewModelScope.launch {
            val hours = settingsRepository.settings.first().timelineReach.hours
            timelineFailed.value = windTimelineRepository.ensureFresh(hours, force).isFailure
        }
    }

    /**
     * While the map is visible: refresh the list when older than a minute, then
     * every 60 s (spec 6.1). Runs under the screen's STARTED lifecycle, so it stops
     * in the background.
     */
    suspend fun keepFresh() {
        while (true) {
            now.value = ZonedDateTime.now(clock)
            spotsRepository.refreshSpots(maxAge = Duration.ofMinutes(1))
            loadTimeline()
            delay(REFRESH_PERIOD_MS)
        }
    }

    /** Clamped to the grid when the state is built, so a shorter refetch never strands it. */
    fun setStep(value: Int) {
        step.value = value.coerceAtLeast(0)
    }

    fun select(wgId: Int?) {
        selectedId.value = wgId
    }

    fun setCountry(country: String?) = filters.update { it.copy(country = country) }

    fun toggleFavouritesOnly() = filters.update { it.copy(favouritesOnly = !it.favouritesOnly) }

    fun toggleLiveOnly() = filters.update { it.copy(liveOnly = !it.liveOnly) }

    fun toggleFavourite(wgId: Int) {
        viewModelScope.launch { favouritesRepository.toggle(wgId) }
    }

    fun setWindField(enabled: Boolean) = updateSettings { it.copy(mapWindField = enabled) }

    fun setMarkers(enabled: Boolean) = updateSettings { it.copy(mapMarkers = enabled) }

    fun setSatellite(enabled: Boolean) = updateSettings { it.copy(mapSatellite = enabled) }

    fun setGlobe(enabled: Boolean) = updateSettings { it.copy(mapGlobe = enabled) }

    fun setGlobeSpin(enabled: Boolean) = updateSettings { it.copy(mapGlobeSpin = enabled) }

    private fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun hasLocationPermission(): Boolean = locationRepository.hasPermission()

    private val _myLocation = MutableStateFlow(MyLocationState())

    /** Where the user was last found (the blue dot) and whether a search is running. */
    val myLocation: StateFlow<MyLocationState> = _myLocation

    private val locateResultsChannel = Channel<LocateResult>(Channel.CONFLATED)

    /** One result per [locate] that ran to the end; a cancelled search sends none. */
    val locateResults: Flow<LocateResult> = locateResultsChannel.receiveAsFlow()

    private var locateJob: Job? = null

    /** Find the user. Presses while a search is running join it instead of queueing another. */
    fun locate() {
        if (locateJob?.isActive == true) return
        _myLocation.update { it.copy(locating = true) }
        locateJob = viewModelScope.launch {
            val found = locationRepository.currentLocation()
            _myLocation.update { MyLocationState(position = found ?: it.position, locating = false) }
            locateResultsChannel.send(if (found != null) LocateResult.Found(found) else LocateResult.Failed)
        }
    }

    /** Drop a running search, so a fix arriving later never moves a map the user has left. */
    fun cancelLocate() {
        locateJob?.cancel()
        locateJob = null
        locateResultsChannel.tryReceive()
        _myLocation.update { it.copy(locating = false) }
    }

    private companion object {
        const val REFRESH_PERIOD_MS = 60_000L
    }
}
