package surf.varun.feature.spot

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.OnDemandKind
import surf.varun.core.data.repository.OnDemandRepository
import surf.varun.core.data.repository.OnDemandState
import surf.varun.core.data.repository.RecentsRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.util.ConnectivityMonitor
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.model.Spot
import surf.varun.core.model.UserSettings
import surf.varun.core.model.WindAlert
import java.time.Duration
import java.time.Instant

class FakeSpotsRepository : SpotsRepository {
    /** Spot per model key. */
    val spotsByModel = MutableStateFlow<Map<String, Spot>>(emptyMap())
    val syncs = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
    val refreshCalls = mutableListOf<Pair<String, Duration>>()
    var refreshResult: Result<Unit> = Result.success(Unit)

    override val spots: Flow<List<Spot>> = flowOf(emptyList())
    override val spotsSync: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus())
    override suspend fun refreshSpots(maxAge: Duration) = Result.success(Unit)
    override fun observeSpot(wgId: Int, model: String): Flow<Spot?> = spotsByModel.map { it[model] }
    override fun spotSync(wgId: Int, model: String): Flow<SyncStatus> = syncs.map { it[model] ?: SyncStatus() }
    override suspend fun refreshSpot(wgId: Int, model: String, maxAge: Duration): Result<Unit> {
        refreshCalls += model to maxAge
        return refreshResult
    }
    override fun observeGridForecast(wgId: Int): Flow<List<ForecastEntry>> = flowOf(emptyList())
    override suspend fun refreshGridForecast(wgId: Int, maxAge: Duration) = Result.success(emptyList<ForecastEntry>())
    override suspend fun spots(ids: Collection<Int>): List<Spot> = emptyList()
    override suspend fun clearCachedDetails() = Unit
}

class FakeFavouritesRepository : FavouritesRepository {
    val ids = MutableStateFlow<List<Int>>(emptyList())
    override val favouriteIds: Flow<List<Int>> = ids
    override suspend fun setFavourite(wgId: Int, favourite: Boolean) =
        ids.update { if (favourite) (it - wgId) + wgId else it - wgId }
    override suspend fun toggle(wgId: Int): Boolean {
        val now = wgId !in ids.value
        setFavourite(wgId, now)
        return now
    }
    override suspend fun reorder(ids: List<Int>) { this.ids.value = ids }
}

class FakeAlertsRepository : AlertsRepository {
    val deletedForSpot = mutableListOf<Int>()
    override val alerts: Flow<List<WindAlert>> = flowOf(emptyList())
    override suspend fun all(): List<WindAlert> = emptyList()
    override suspend fun get(id: Long): WindAlert? = null
    override suspend fun save(alert: WindAlert): Long = 1
    override suspend fun delete(id: Long) = Unit
    override suspend fun deleteForSpot(wgId: Int) { deletedForSpot += wgId }
    override suspend fun markFired(id: Long, at: Instant) = Unit
    override suspend fun mute(id: Long, until: Instant) = Unit
}

class FakeRecentsRepository : RecentsRepository {
    val opened = mutableListOf<Int>()
    override val recentSpotIds: Flow<List<Int>> = flowOf(emptyList())
    override val recentSearches: Flow<List<String>> = flowOf(emptyList())
    override suspend fun spotOpened(wgId: Int) { opened += wgId }
    override suspend fun searched(query: String) = Unit
    override suspend fun clearSearches() = Unit
}

class FakeSettingsRepository(var model: String = ForecastModelOption.DEFAULT_KEY) : SettingsRepository {
    val state = MutableStateFlow(UserSettings())
    val remembered = mutableMapOf<Int, String>()
    override val settings: Flow<UserSettings> = state
    override suspend fun update(transform: (UserSettings) -> UserSettings) = state.update(transform)
    override val calculatorInputs: Flow<CalculatorInputs> = flowOf(CalculatorInputs())
    override suspend fun setCalculatorInputs(inputs: CalculatorInputs) = Unit
    override val language: Flow<AppLanguage> = flowOf(AppLanguage.SYSTEM)
    override suspend fun setLanguage(language: AppLanguage) = Unit
    override suspend fun modelFor(wgId: Int): String = remembered[wgId] ?: model
    override suspend fun rememberModel(wgId: Int, model: String) { remembered[wgId] = model }
}

class FakeOnDemandRepository : OnDemandRepository {
    val states = MutableStateFlow<Map<OnDemandKind, OnDemandState>>(emptyMap())
    val analysisCalls = mutableListOf<String>()

    override fun state(wgId: Int, kind: OnDemandKind): Flow<OnDemandState> = states.map { it[kind] ?: OnDemandState.Idle }
    override fun generateAnalysis(wgId: Int, language: String): Deferred<Result<Spot>> {
        analysisCalls += language
        return CompletableDeferred(Result.failure(IllegalStateException()))
    }
    override fun acknowledge(wgId: Int, kind: OnDemandKind) = Unit
}

class FakeConnectivity(online: Boolean = true) : ConnectivityMonitor {
    val state = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = state
}
