package surf.varun.feature.map

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import surf.varun.core.data.repository.CachedTimeline
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.LocationRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.repository.WindTimelineRepository
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.Coordinates
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.Spot
import surf.varun.core.model.UserSettings
import surf.varun.core.model.WindTimeline
import java.time.Duration
import java.time.Instant

internal class FakeSpotsRepository(initial: List<Spot>) : SpotsRepository {
    val list = MutableStateFlow(initial)
    var listRefreshes = 0
    override val spots: Flow<List<Spot>> = list
    override val spotsSync: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus())
    override suspend fun refreshSpots(maxAge: Duration): Result<Unit> {
        listRefreshes++
        return Result.success(Unit)
    }
    override fun observeSpot(wgId: Int, model: String): Flow<Spot?> = list.map { s -> s.firstOrNull { it.wgId == wgId } }
    override fun spotSync(wgId: Int, model: String): Flow<SyncStatus> = flowOf(SyncStatus())
    override suspend fun refreshSpot(wgId: Int, model: String, maxAge: Duration) = Result.success(Unit)
    override fun observeGridForecast(wgId: Int): Flow<List<ForecastEntry>> = emptyFlow()
    override suspend fun refreshGridForecast(wgId: Int, maxAge: Duration) = Result.success(emptyList<ForecastEntry>())
    override suspend fun spots(ids: Collection<Int>): List<Spot> = list.value.filter { it.wgId in ids }
    override suspend fun clearCachedDetails() = Unit
}

internal class FakeFavouritesRepository : FavouritesRepository {
    private val ids = MutableStateFlow<List<Int>>(emptyList())
    override val favouriteIds: Flow<List<Int>> = ids
    override suspend fun setFavourite(wgId: Int, favourite: Boolean) =
        ids.update { if (favourite) (it + wgId).distinct() else it - wgId }
    override suspend fun toggle(wgId: Int): Boolean {
        val now = wgId !in ids.value
        setFavourite(wgId, now)
        return now
    }
    override suspend fun reorder(ids: List<Int>) {
        this.ids.value = ids
    }
}

internal class FakeSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(UserSettings())
    override val settings: Flow<UserSettings> = state
    override suspend fun update(transform: (UserSettings) -> UserSettings) = state.update(transform)
    override val calculatorInputs: Flow<CalculatorInputs> = flowOf(CalculatorInputs())
    override suspend fun setCalculatorInputs(inputs: CalculatorInputs) = Unit
    override val language: Flow<AppLanguage> = flowOf(AppLanguage.SYSTEM)
    override suspend fun setLanguage(language: AppLanguage) = Unit
    override suspend fun modelFor(wgId: Int) = "gfs"
    override suspend fun rememberModel(wgId: Int, model: String) = Unit
}

internal class FakeWindTimelineRepository(private val data: WindTimeline?, private val fail: Boolean = false) : WindTimelineRepository {
    private val state = MutableStateFlow<CachedTimeline?>(null)
    var requestedHours = mutableListOf<Int>()
    override val timeline: StateFlow<CachedTimeline?> = state
    override suspend fun ensureFresh(hours: Int, force: Boolean): Result<CachedTimeline> {
        requestedHours += hours
        if (fail || data == null) return Result.failure(IllegalStateException("offline"))
        return Result.success(CachedTimeline(data, hours, Instant.EPOCH).also { state.value = it })
    }
}

internal class FakeLocationRepository(var location: Coordinates? = null) : LocationRepository {
    override fun hasPermission() = location != null
    override suspend fun currentLocation() = location
}
