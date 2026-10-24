package surf.varun.feature.spot

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.OnDemandKind
import surf.varun.core.data.repository.OnDemandRepository
import surf.varun.core.data.repository.OnDemandState
import surf.varun.core.data.repository.RecentsRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.util.ConnectivityMonitor
import surf.varun.core.data.util.DataError
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.domain.Conditions
import surf.varun.core.domain.ForecastDays
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.model.Spot
import surf.varun.core.model.UserSettings
import surf.varun.core.navigation.SpotRoute
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Which paid operation the user is being asked to confirm. */
enum class PendingConfirm { AI_ANALYSIS, ICM_FORECAST }

@Immutable
data class SpotUiState(
    val wgId: Int,
    val spot: Spot? = null,
    val model: String = ForecastModelOption.DEFAULT_KEY,
    val sync: SyncStatus = SyncStatus(),
    val online: Boolean = true,
    val favourite: Boolean = false,
    val settings: UserSettings = UserSettings(),
    /** Daytime days of the hourly forecast, after the windy-days filter. */
    val days: List<ForecastDays.Day> = emptyList(),
    /** Hourly data exists but the windy-days filter hid every day. */
    val noWindyDays: Boolean = false,
    val analysisState: OnDemandState = OnDemandState.Idle,
    val icmState: OnDemandState = OnDemandState.Idle,
    val pendingConfirm: PendingConfirm? = null,
    val focusTime: LocalDateTime? = null,
    val removedEverywhere: Boolean = false,
) {
    val notFound: Boolean get() = sync.lastError is DataError.NotFound
    val loading: Boolean get() = spot == null && !notFound && sync.lastError == null
    val windyOnly: Boolean get() = settings.filterWindyDays
}

sealed interface SpotEffect {
    data class Message(val text: Int) : SpotEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = SpotViewModel.Factory::class)
class SpotViewModel @AssistedInject constructor(
    @Assisted private val route: SpotRoute,
    private val spotsRepository: SpotsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val alertsRepository: AlertsRepository,
    private val recentsRepository: RecentsRepository,
    private val settingsRepository: SettingsRepository,
    private val onDemandRepository: OnDemandRepository,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: SpotRoute): SpotViewModel
    }

    private val wgId = route.wgId
    private val model = MutableStateFlow<String?>(null)
    private val local = MutableStateFlow(LocalState(focusTime = route.focusTime?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }))

    private val effects = Channel<SpotEffect>(Channel.BUFFERED)
    val effect: Flow<SpotEffect> = effects.receiveAsFlow()

    private data class LocalState(
        val pendingConfirm: PendingConfirm? = null,
        val focusTime: LocalDateTime? = null,
        val removedEverywhere: Boolean = false,
    )

    private data class Detail(val spot: Spot?, val sync: SyncStatus, val model: String)

    private val detail: Flow<Detail> = model.flatMapLatest { key ->
        val k = key ?: ForecastModelOption.DEFAULT_KEY
        combine(spotsRepository.observeSpot(wgId, k), spotsRepository.spotSync(wgId, k)) { spot, sync -> Detail(spot, sync, k) }
    }

    private val onDemand: Flow<Pair<OnDemandState, OnDemandState>> = combine(
        onDemandRepository.state(wgId, OnDemandKind.AI_ANALYSIS),
        onDemandRepository.state(wgId, OnDemandKind.ICM_FORECAST),
    ) { ai, icm -> ai to icm }

    val uiState: StateFlow<SpotUiState> = combine(
        detail,
        favouritesRepository.favouriteIds,
        settingsRepository.settings,
        combine(connectivity.isOnline, onDemand, local) { online, od, l -> Triple(online, od, l) },
    ) { d, favourites, settings, (online, od, l) ->
        val days = d.spot?.let { ForecastDays.daytimeDays(it.hourly, settings.filterWindyDays) }.orEmpty()
        SpotUiState(
            wgId = wgId,
            spot = d.spot,
            model = d.model,
            sync = d.sync,
            online = online,
            favourite = wgId in favourites,
            settings = settings,
            days = days,
            noWindyDays = settings.filterWindyDays && days.isEmpty() && d.spot?.hourly?.any(ForecastDays::isDaytime) == true,
            analysisState = od.first,
            icmState = od.second,
            pendingConfirm = l.pendingConfirm,
            focusTime = l.focusTime,
            removedEverywhere = l.removedEverywhere,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpotUiState(wgId))

    init {
        viewModelScope.launch {
            recentsRepository.spotOpened(wgId)
            val key = settingsRepository.modelFor(wgId)
            model.value = key
            spotsRepository.refreshSpot(wgId, key, maxAge = OPEN_MAX_AGE)
        }
    }

    private suspend fun currentModel(): String = model.value ?: settingsRepository.modelFor(wgId)

    /**
     * Refresh every 60 s while the screen is visible (spec 6.1). The UI runs this
     * inside `repeatOnLifecycle(STARTED)`, so it stops in the background.
     */
    suspend fun autoRefresh() {
        while (true) {
            delay(AUTO_REFRESH.toMillis())
            spotsRepository.refreshSpot(wgId, currentModel(), maxAge = AUTO_REFRESH.minusSeconds(5))
        }
    }

    /** Pull-to-refresh: always goes to the network. */
    fun refresh() {
        viewModelScope.launch { spotsRepository.refreshSpot(wgId, currentModel()) }
    }

    fun selectModel(key: String) {
        if (key == model.value) return
        model.value = key
        viewModelScope.launch {
            settingsRepository.rememberModel(wgId, key)
            spotsRepository.refreshSpot(wgId, key, maxAge = OPEN_MAX_AGE)
        }
    }

    fun toggleFavourite() {
        viewModelScope.launch {
            val now = favouritesRepository.toggle(wgId)
            effects.send(SpotEffect.Message(if (now) surf.varun.core.ui.R.string.favourite_added else surf.varun.core.ui.R.string.favourite_removed))
        }
    }

    fun setWindyOnly(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(filterWindyDays = enabled) } }
    }

    /** First step of a paid operation: only asks for confirmation (spec 6.5). */
    fun requestAnalysis() = local.update { it.copy(pendingConfirm = PendingConfirm.AI_ANALYSIS) }

    fun requestIcm() = local.update { it.copy(pendingConfirm = PendingConfirm.ICM_FORECAST) }

    fun dismissConfirm() = local.update { it.copy(pendingConfirm = null) }

    /**
     * The user confirmed. [language] is the language being read ("en"/"pl"); the
     * backend writes both, the requested one decides the outcome.
     */
    fun confirm(language: String) {
        val pending = local.value.pendingConfirm ?: return
        local.update { it.copy(pendingConfirm = null) }
        when (pending) {
            PendingConfirm.AI_ANALYSIS -> {
                if (uiState.value.analysisState == OnDemandState.Running) return
                onDemandRepository.acknowledge(wgId, OnDemandKind.AI_ANALYSIS)
                onDemandRepository.generateAnalysis(wgId, language)
            }
            PendingConfirm.ICM_FORECAST -> {
                if (uiState.value.icmState == OnDemandState.Running) return
                onDemandRepository.acknowledge(wgId, OnDemandKind.ICM_FORECAST)
                val deferred = onDemandRepository.generateIcm(wgId)
                viewModelScope.launch {
                    if (deferred.await().isSuccess) {
                        selectModel(ForecastModelOption.ICM_KEY)
                        effects.send(SpotEffect.Message(R.string.icm_ready))
                    }
                }
            }
        }
    }

    /** "This spot no longer exists": forget it in favourites and alerts. */
    fun removeEverywhere() {
        viewModelScope.launch {
            favouritesRepository.setFavourite(wgId, false)
            alertsRepository.deleteForSpot(wgId)
            local.update { it.copy(removedEverywhere = true) }
        }
    }

    fun focusConsumed() = local.update { it.copy(focusTime = null) }

    /** Wind to prefill the calculator with: live reading, else the forecast for now. */
    fun calculatorWind(): Double? {
        val spot = uiState.value.spot ?: return null
        return Conditions.current(spot, ZonedDateTime.now(clock))?.wind
    }

    companion object {
        val OPEN_MAX_AGE: Duration = Duration.ofMinutes(1)
        val AUTO_REFRESH: Duration = Duration.ofSeconds(60)
    }
}
