package surf.varun.core.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.LocationRepository
import surf.varun.core.data.repository.OnDemandKind
import surf.varun.core.data.repository.OnDemandRepository
import surf.varun.core.data.repository.OnDemandState
import surf.varun.core.data.repository.RecentsRepository
import surf.varun.core.data.repository.ServiceRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.util.ConnectivityMonitor
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.Coordinates
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.model.ForecastProgress
import surf.varun.core.model.ServiceStatus
import surf.varun.core.model.Sponsor
import surf.varun.core.model.Spot
import surf.varun.core.model.UserSettings
import surf.varun.core.model.WindAlert
import java.time.Duration
import java.time.Instant

/* In-memory fakes of the data layer for ViewModel tests. */

class FakeSpotsRepository(initial: List<Spot> = emptyList()) : SpotsRepository {
    val list = MutableStateFlow(initial)
    val details = MutableStateFlow<Map<Pair<Int, String>, Spot>>(emptyMap())
    val refreshCalls = mutableListOf<Duration>()
    val spotRefreshCalls = mutableListOf<Triple<Int, String, Duration>>()
    var refreshResult: Result<Unit> = Result.success(Unit)
    private val _sync = MutableStateFlow(SyncStatus())

    override val spots: Flow<List<Spot>> = list
    override val spotsSync: StateFlow<SyncStatus> = _sync

    override suspend fun refreshSpots(maxAge: Duration): Result<Unit> {
        refreshCalls += maxAge
        return refreshResult
    }

    override fun observeSpot(wgId: Int, model: String): Flow<Spot?> =
        details.map { it[wgId to model] ?: list.value.firstOrNull { spot -> spot.wgId == wgId } }

    override fun spotSync(wgId: Int, model: String): Flow<SyncStatus> = _sync

    override suspend fun refreshSpot(wgId: Int, model: String, maxAge: Duration): Result<Unit> {
        spotRefreshCalls += Triple(wgId, model, maxAge)
        return refreshResult
    }

    override fun observeGridForecast(wgId: Int): Flow<List<ForecastEntry>> = MutableStateFlow(emptyList())

    override suspend fun refreshGridForecast(wgId: Int, maxAge: Duration): Result<List<ForecastEntry>> = Result.success(emptyList())

    override suspend fun spots(ids: Collection<Int>): List<Spot> = list.value.filter { it.wgId in ids }

    override suspend fun clearCachedDetails() = Unit

    fun setSync(status: SyncStatus) {
        _sync.value = status
    }
}

class FakeFavouritesRepository(initial: List<Int> = emptyList()) : FavouritesRepository {
    private val ids = MutableStateFlow(initial)
    override val favouriteIds: Flow<List<Int>> = ids

    override suspend fun setFavourite(wgId: Int, favourite: Boolean) {
        ids.update { if (favourite) (it - wgId) + wgId else it - wgId }
    }

    override suspend fun toggle(wgId: Int): Boolean {
        val now = wgId !in ids.value
        setFavourite(wgId, now)
        return now
    }

    override suspend fun reorder(ids: List<Int>) {
        this.ids.value = ids
    }

    val current: List<Int> get() = ids.value
}

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    val inputs = MutableStateFlow(CalculatorInputs())
    val languageState = MutableStateFlow(AppLanguage.SYSTEM)
    val models = mutableMapOf<Int, String>()

    override val settings: Flow<UserSettings> = state

    override suspend fun update(transform: (UserSettings) -> UserSettings) = state.update(transform)

    override val calculatorInputs: Flow<CalculatorInputs> = inputs

    override suspend fun setCalculatorInputs(inputs: CalculatorInputs) {
        this.inputs.value = inputs
    }

    override val language: Flow<AppLanguage> = languageState

    override suspend fun setLanguage(language: AppLanguage) {
        languageState.value = language
    }

    override suspend fun modelFor(wgId: Int): String = models[wgId] ?: ForecastModelOption.DEFAULT_KEY

    override suspend fun rememberModel(wgId: Int, model: String) {
        models[wgId] = model
    }
}

class FakeServiceRepository(
    var status: ServiceStatus = ServiceStatus("UP", "v1.0.0", "1d", null, 827, 59, 15),
    var progress: ForecastProgress = ForecastProgress(false, 827, 827, 0),
    var sponsorList: List<Sponsor> = emptyList(),
) : ServiceRepository {
    override suspend fun status() = Result.success(status)

    override suspend fun forecastProgress() = Result.success(progress)

    override suspend fun sponsors() = Result.success(sponsorList)

    override val baseUrl: String = "https://varun.surf/"
}

class FakeLocationRepository(var permission: Boolean = false, var location: Coordinates? = null) : LocationRepository {
    override fun hasPermission() = permission

    override suspend fun currentLocation() = if (permission) location else null
}

class FakeConnectivityMonitor(online: Boolean = true) : ConnectivityMonitor {
    val state = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = state
}

class FakeRecentsRepository : RecentsRepository {
    private val spotIds = MutableStateFlow<List<Int>>(emptyList())
    private val searches = MutableStateFlow<List<String>>(emptyList())
    override val recentSpotIds: Flow<List<Int>> = spotIds
    override val recentSearches: Flow<List<String>> = searches

    override suspend fun spotOpened(wgId: Int) = spotIds.update { listOf(wgId) + (it - wgId) }

    override suspend fun searched(query: String) = searches.update { listOf(query) + (it - query) }

    override suspend fun clearSearches() {
        searches.value = emptyList()
    }
}

class FakeAlertsRepository : AlertsRepository {
    private val state = MutableStateFlow<List<WindAlert>>(emptyList())
    private var nextId = 1L
    override val alerts: Flow<List<WindAlert>> = state

    override suspend fun all() = state.value

    override suspend fun get(id: Long) = state.value.firstOrNull { it.id == id }

    override suspend fun save(alert: WindAlert): Long {
        val id = if (alert.id == 0L) nextId++ else alert.id
        state.update { list -> list.filter { it.id != id } + alert.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) = state.update { list -> list.filter { it.id != id } }

    override suspend fun deleteForSpot(wgId: Int) = state.update { list -> list.filter { it.wgId != wgId } }

    override suspend fun markFired(id: Long, at: Instant) =
        state.update { list -> list.map { if (it.id == id) it.copy(lastFiredAt = at) else it } }

    override suspend fun mute(id: Long, until: Instant) =
        state.update { list -> list.map { if (it.id == id) it.copy(mutedUntil = until) else it } }
}

class FakeOnDemandRepository : OnDemandRepository {
    val states = MutableStateFlow<Map<Pair<Int, OnDemandKind>, OnDemandState>>(emptyMap())
    val calls = mutableListOf<Pair<Int, OnDemandKind>>()
    var result: Result<Spot>? = null

    override fun state(wgId: Int, kind: OnDemandKind): Flow<OnDemandState> =
        states.map { it[wgId to kind] ?: OnDemandState.Idle }

    override fun generateAnalysis(wgId: Int, language: String): Deferred<Result<Spot>> = call(wgId, OnDemandKind.AI_ANALYSIS)

    override fun generateIcm(wgId: Int): Deferred<Result<Spot>> = call(wgId, OnDemandKind.ICM_FORECAST)

    override fun acknowledge(wgId: Int, kind: OnDemandKind) = states.update { it - (wgId to kind) }

    private fun call(wgId: Int, kind: OnDemandKind): Deferred<Result<Spot>> {
        calls += wgId to kind
        val r = result ?: Result.failure(IllegalStateException("no result configured"))
        states.update { it + ((wgId to kind) to r.fold({ OnDemandState.Succeeded }, { e -> OnDemandState.Failed(e) })) }
        return CompletableDeferred(r)
    }
}
