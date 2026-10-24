package surf.varun.feature.alerts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.Spot
import surf.varun.core.model.UserSettings
import surf.varun.core.model.WindAlert
import java.time.Duration
import java.time.Instant

class FakeAlertsRepository : AlertsRepository {
    val stored = MutableStateFlow<List<WindAlert>>(emptyList())
    private var nextId = 1L

    override val alerts: Flow<List<WindAlert>> = stored

    override suspend fun all() = stored.value

    override suspend fun get(id: Long) = stored.value.firstOrNull { it.id == id }

    override suspend fun save(alert: WindAlert): Long {
        val id = if (alert.id == 0L) nextId++ else alert.id
        stored.update { list -> list.filterNot { it.id == id } + alert.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) = stored.update { list -> list.filterNot { it.id == id } }

    override suspend fun deleteForSpot(wgId: Int) = stored.update { list -> list.filterNot { it.wgId == wgId } }

    override suspend fun markFired(id: Long, at: Instant) =
        stored.update { list -> list.map { if (it.id == id) it.copy(lastFiredAt = at) else it } }

    override suspend fun mute(id: Long, until: Instant) =
        stored.update { list -> list.map { if (it.id == id) it.copy(mutedUntil = until) else it } }
}

class FakeSpotsRepository(spots: List<Spot>) : SpotsRepository {
    val all = MutableStateFlow(spots)
    override val spots: Flow<List<Spot>> = all
    override val spotsSync: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus())

    override suspend fun refreshSpots(maxAge: Duration) = Result.success(Unit)

    override fun observeSpot(wgId: Int, model: String): Flow<Spot?> = all.map { list -> list.firstOrNull { it.wgId == wgId } }

    override fun spotSync(wgId: Int, model: String): Flow<SyncStatus> = flowOf(SyncStatus())

    override suspend fun refreshSpot(wgId: Int, model: String, maxAge: Duration) = Result.success(Unit)

    override fun observeGridForecast(wgId: Int): Flow<List<ForecastEntry>> = flowOf(emptyList())

    override suspend fun refreshGridForecast(wgId: Int, maxAge: Duration) = Result.success(emptyList<ForecastEntry>())

    override suspend fun spots(ids: Collection<Int>) = all.value.filter { it.wgId in ids }

    override suspend fun clearCachedDetails() = Unit
}

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    val current = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = current

    override suspend fun update(transform: (UserSettings) -> UserSettings) = current.update(transform)

    override val calculatorInputs: Flow<CalculatorInputs> = flowOf(CalculatorInputs())

    override suspend fun setCalculatorInputs(inputs: CalculatorInputs) = Unit

    override val language: Flow<AppLanguage> = flowOf(AppLanguage.SYSTEM)

    override suspend fun setLanguage(language: AppLanguage) = Unit

    override suspend fun modelFor(wgId: Int) = "gfs"

    override suspend fun rememberModel(wgId: Int, model: String) = Unit
}
