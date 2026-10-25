package surf.varun.feature.spots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.LocationRepository
import surf.varun.core.data.repository.ServiceRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.util.ConnectivityMonitor
import surf.varun.core.data.util.DefaultDispatcher
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.model.Coordinates
import surf.varun.core.model.ForecastProgress
import surf.varun.core.model.Sponsor
import surf.varun.core.model.Spot
import surf.varun.core.model.UserSettings
import surf.varun.core.navigation.SpotsRoute
import surf.varun.core.ui.SpotCardModel
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime

data class SpotsUiState(
    val items: ImmutableList<SpotCardModel> = persistentListOf(),
    /** True until the first emission of the cache. */
    val loading: Boolean = true,
    val hasCache: Boolean = false,
    val filters: SpotFilters = SpotFilters(),
    val sort: SpotSort = SpotSort.FIRING,
    val countries: ImmutableList<CountryCount> = persistentListOf(),
    val sync: SyncStatus = SyncStatus(),
    val online: Boolean = true,
    val progress: ForecastProgress? = null,
    val sponsors: ImmutableList<Sponsor> = persistentListOf(),
    val locationKnown: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val now: ZonedDateTime = ZonedDateTime.now(),
)

sealed interface SpotsEffect {
    data class Favourite(val added: Boolean) : SpotsEffect
    data object LocationUnavailable : SpotsEffect
}

@HiltViewModel(assistedFactory = SpotsViewModel.Factory::class)
class SpotsViewModel @AssistedInject constructor(
    @Assisted route: SpotsRoute,
    private val spotsRepository: SpotsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val settingsRepository: SettingsRepository,
    private val serviceRepository: ServiceRepository,
    private val locationRepository: LocationRepository,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
    @param:DefaultDispatcher computation: CoroutineDispatcher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: SpotsRoute): SpotsViewModel
    }

    private val requestedCountry = route.country
    private val filters = MutableStateFlow(SpotFilters())
    private val sort = MutableStateFlow(SpotSort.FIRING)
    private val location = MutableStateFlow<Coordinates?>(null)
    private val progress = MutableStateFlow<ForecastProgress?>(null)
    private val sponsors = MutableStateFlow<List<Sponsor>>(emptyList())
    private var countryResolved = false

    private val effects = Channel<SpotsEffect>(Channel.BUFFERED)
    val effect: Flow<SpotsEffect> = effects.receiveAsFlow()

    /** Ticks every 30 s so card ages ("3 min ago") and staleness stay right. */
    private val ticker = flow {
        while (true) {
            emit(ZonedDateTime.now(clock))
            delay(30_000)
        }
    }

    private val inputs = combine(filters, sort, location, ticker) { f, s, l, n -> Inputs(f, s, l, n) }
    private val extras = combine(progress, sponsors, connectivity.isOnline.onStart { emit(true) }, spotsRepository.spotsSync) { p, sp, o, sync ->
        Extras(p, sp, o, sync)
    }

    val uiState: StateFlow<SpotsUiState> = combine(
        spotsRepository.spots,
        favouritesRepository.favouriteIds,
        settingsRepository.settings,
        inputs,
        extras,
    ) { spots, favourites, settings, input, extra ->
        if (!countryResolved && spots.isNotEmpty()) {
            countryResolved = true
            SpotListing.resolveCountry(requestedCountry, spots.map { it.country }.toSet())?.let { country ->
                filters.update { it.copy(country = country) }
            }
        }
        SpotsUiState(
            items = SpotListing.apply(spots, input.filters, input.sort, favourites.toSet(), input.location, input.now).toImmutableList(),
            loading = false,
            hasCache = spots.isNotEmpty(),
            filters = input.filters,
            sort = input.sort,
            countries = SpotListing.countries(spots).toImmutableList(),
            sync = extra.sync,
            online = extra.online,
            progress = extra.progress,
            sponsors = extra.sponsors.toImmutableList(),
            locationKnown = input.location != null,
            settings = settings,
            now = input.now,
        )
    }
        // Filtering, sorting and building ~820 cards every tick is no work for the main thread
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpotsUiState())

    init {
        viewModelScope.launch { serviceRepository.sponsors().onSuccess { list -> sponsors.value = list.filter { it.main } } }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.useLocation && locationRepository.hasPermission()) location.value = locationRepository.currentLocation()
        }
    }

    /**
     * Runs while the screen is visible (the UI calls it from `repeatOnLifecycle(STARTED)`):
     * refreshes the list every 60 s like the web, and polls the forecast sweep every
     * 5 s while it runs (spec 6.1).
     */
    suspend fun keepFresh() {
        kotlinx.coroutines.coroutineScope {
            launch {
                while (true) {
                    spotsRepository.refreshSpots(maxAge = Duration.ofMinutes(1))
                    delay(60_000)
                }
            }
            launch {
                while (true) {
                    val current = serviceRepository.forecastProgress().getOrNull()
                    progress.value = current
                    delay(if (current?.inProgress == true) 5_000 else 60_000)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { spotsRepository.refreshSpots() }
    }

    fun setCountry(country: String?) = filters.update { it.copy(country = country) }

    fun setSort(value: SpotSort) {
        sort.value = value
    }

    fun toggleLiveOnly() = filters.update { it.copy(liveOnly = !it.liveOnly) }

    fun toggleWindyToday() = filters.update { it.copy(windyToday = !it.windyToday) }

    fun clearFilters() {
        filters.value = SpotFilters()
    }

    /** Called once the permission is granted (or already was). */
    fun enableNearby() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(useLocation = true) }
            val here = locationRepository.currentLocation()
            if (here == null) {
                effects.send(SpotsEffect.LocationUnavailable)
                return@launch
            }
            location.value = here
            filters.update { it.copy(nearby = !it.nearby) }
            if (sort.value == SpotSort.FIRING) sort.value = SpotSort.DISTANCE
        }
    }

    fun disableNearby() {
        filters.update { it.copy(nearby = false) }
        if (sort.value == SpotSort.DISTANCE) sort.value = SpotSort.FIRING
    }

    fun hasLocationPermission() = locationRepository.hasPermission()

    fun toggleFavourite(wgId: Int) {
        viewModelScope.launch { effects.send(SpotsEffect.Favourite(favouritesRepository.toggle(wgId))) }
    }

    private data class Inputs(val filters: SpotFilters, val sort: SpotSort, val location: Coordinates?, val now: ZonedDateTime)

    private data class Extras(val progress: ForecastProgress?, val sponsors: List<Sponsor>, val online: Boolean, val sync: SyncStatus)
}
