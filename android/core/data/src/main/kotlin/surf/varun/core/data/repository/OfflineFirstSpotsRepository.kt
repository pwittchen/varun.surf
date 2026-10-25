package surf.varun.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import surf.varun.core.data.mapper.LANG_EN
import surf.varun.core.data.mapper.LANG_PL
import surf.varun.core.data.mapper.analysis
import surf.varun.core.data.mapper.analysisEntities
import surf.varun.core.data.mapper.modelEntities
import surf.varun.core.data.mapper.toDailyEntity
import surf.varun.core.data.mapper.toEntity
import surf.varun.core.data.mapper.toHistoryEntity
import surf.varun.core.data.mapper.toHourlyEntity
import surf.varun.core.data.mapper.toModel
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.data.util.catching
import surf.varun.core.database.dao.SpotDao
import surf.varun.core.database.entity.SpotDetailEntity
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.model.Spot
import surf.varun.core.network.VarunNetworkDataSource
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstSpotsRepository @Inject constructor(
    private val network: VarunNetworkDataSource,
    private val dao: SpotDao,
    private val clock: Clock,
) : SpotsRepository {
    private val listMutex = Mutex()
    private val detailMutexes = ConcurrentHashMap<String, Mutex>()

    private val _spotsSync = MutableStateFlow(SyncStatus())
    override val spotsSync: StateFlow<SyncStatus> = _spotsSync.asStateFlow()

    private val detailErrors = MutableStateFlow<Map<String, Throwable?>>(emptyMap())
    private val detailRefreshing = MutableStateFlow<Set<String>>(emptySet())

    // Room re-emits on every write to the tables behind the list, changed or not; the
    // ~820 rows are compared before they are mapped, and mapped off the main thread
    override val spots: Flow<List<Spot>> = dao.observeSpots()
        .distinctUntilChanged()
        .map { rows -> rows.map { it.toModel() } }
        .flowOn(Dispatchers.Default)

    override suspend fun refreshSpots(maxAge: Duration): Result<Unit> = listMutex.withLock {
        val last = dao.lastListFetch()
        if (_spotsSync.value.lastSuccessAt == null && last != null) {
            _spotsSync.update { it.copy(lastSuccessAt = Instant.ofEpochMilli(last)) }
        }
        if (last != null && !maxAge.isZero && clock.millis() - last < maxAge.toMillis()) {
            return Result.success(Unit)
        }
        _spotsSync.update { it.copy(refreshing = true) }
        val result = catching {
            val spots = network.spots()
            val now = clock.millis()
            dao.replaceList(
                spots = spots.mapIndexed { index, spot -> spot.toEntity(index, now) },
                live = spots.mapNotNull { spot -> spot.live?.toEntity(spot.wgId, now) },
                daily = spots.flatMap { spot -> spot.daily.mapIndexed { i, day -> day.toDailyEntity(spot.wgId, i) } },
            )
            now
        }
        _spotsSync.update { status ->
            result.fold(
                onSuccess = { SyncStatus(lastSuccessAt = Instant.ofEpochMilli(it)) },
                onFailure = { status.copy(refreshing = false, lastError = it) },
            )
        }
        result.onFailure { Timber.w(it, "Spots refresh failed") }.map { }
    }

    // Every list refresh touches the spot tables and every grid forecast the hourly one,
    // which re-emits each of these for spots that did not change
    override fun observeSpot(wgId: Int, model: String): Flow<Spot?> = combine(
        dao.observeSpot(wgId).distinctUntilChanged(),
        dao.observeHistory(wgId).distinctUntilChanged(),
        dao.observeHourly(wgId, model).distinctUntilChanged(),
        dao.observeAnalysis(wgId).distinctUntilChanged(),
        dao.observeModels(wgId).distinctUntilChanged(),
    ) { row, history, hourly, analysis, models ->
        row?.toModel()?.copy(
            liveHistory = history.map { it.toModel() },
            hourly = hourly.mapNotNull { it.toModel().takeIf { entry -> entry.time != null } },
            aiAnalysisEn = analysis.analysis(LANG_EN),
            aiAnalysisPl = analysis.analysis(LANG_PL),
            availableModels = models.map { it.toModel() },
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override fun spotSync(wgId: Int, model: String): Flow<SyncStatus> {
        val key = key(wgId, model)
        return combine(dao.observeDetail(wgId, model), detailErrors, detailRefreshing) { detail, errors, refreshing ->
            SyncStatus(
                refreshing = key in refreshing,
                lastSuccessAt = detail?.fetchedAt?.let(Instant::ofEpochMilli),
                lastError = errors[key],
            )
        }.distinctUntilChanged()
    }

    override suspend fun refreshSpot(wgId: Int, model: String, maxAge: Duration): Result<Unit> {
        val key = key(wgId, model)
        return detailMutexes.getOrPut(key) { Mutex() }.withLock {
            val detail = dao.detail(wgId, model)
            if (detail != null && !maxAge.isZero && clock.millis() - detail.fetchedAt < maxAge.toMillis()) {
                return@withLock Result.success(Unit)
            }
            detailRefreshing.update { it + key }
            val result = catching {
                // GFS is what /spots/{id} serves; asking for it explicitly would skip the
                // server's model discovery for this spot
                val spot = network.spot(wgId, model.takeUnless { it == ForecastModelOption.DEFAULT_KEY })
                writeDetail(spot, model)
            }
            detailRefreshing.update { it - key }
            detailErrors.update { it + (key to result.exceptionOrNull()) }
            result.onFailure { Timber.w(it, "Spot %d (%s) refresh failed", wgId, model) }
        }
    }

    internal suspend fun writeDetail(spot: Spot, model: String) {
        val now = clock.millis()
        val existing = dao.spots(listOf(spot.wgId)).firstOrNull()?.spot
        dao.replaceSpot(
            // The list's freshness is MAX(fetchedAt): a spot opened from a deep link
            // before the list ever loaded must not make the list look fresh
            spot = spot.toEntity(existing?.serverOrder ?: Int.MAX_VALUE, existing?.fetchedAt ?: 0L),
            live = spot.live?.toEntity(spot.wgId, now),
            history = spot.liveHistory.mapIndexed { index, live -> live.toHistoryEntity(spot.wgId, index) },
            daily = spot.daily.mapIndexed { index, day -> day.toDailyEntity(spot.wgId, index) },
            model = model,
            hourly = spot.hourly.mapNotNull { it.toHourlyEntity(spot.wgId, model) },
            analysis = spot.analysisEntities(),
            models = spot.modelEntities(),
            fetchedAt = now,
        )
        dao.trimDetails(KEEP_DETAIL_FOR_SPOTS, spot.wgId, SpotsRepository.GRID_MODEL)
    }

    override fun observeGridForecast(wgId: Int): Flow<List<ForecastEntry>> =
        dao.observeHourly(wgId, SpotsRepository.GRID_MODEL)
            .distinctUntilChanged()
            .map { rows -> rows.map { it.toModel() } }
            .flowOn(Dispatchers.Default)

    override suspend fun refreshGridForecast(wgId: Int, maxAge: Duration): Result<List<ForecastEntry>> {
        val model = SpotsRepository.GRID_MODEL
        val detail = dao.detail(wgId, model)
        if (detail != null && !maxAge.isZero && clock.millis() - detail.fetchedAt < maxAge.toMillis()) {
            return Result.success(dao.hourly(wgId, model).map { it.toModel() })
        }
        return catching {
            val hours = network.hourlyForecast(wgId)
            dao.deleteHourly(wgId, model)
            dao.insertHourly(hours.mapNotNull { it.toHourlyEntity(wgId, model) })
            dao.upsertDetail(SpotDetailEntity(wgId, model, clock.millis()))
            hours
        }
    }

    override suspend fun spots(ids: Collection<Int>): List<Spot> =
        if (ids.isEmpty()) emptyList() else dao.spots(ids.toList()).map { it.toModel() }

    override suspend fun clearCachedDetails() = dao.clearCachedDetails()

    private fun key(wgId: Int, model: String) = "$wgId/$model"

    companion object {
        /** Spec 7.4: hourly detail is kept for the 30 most recently opened spots (plus favourites). */
        const val KEEP_DETAIL_FOR_SPOTS = 30
    }
}
