package surf.varun.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.Spot
import java.time.Duration

/**
 * Offline-first access to spots. Flows come from the local database and never go
 * blank while there is a cached copy; `refresh*` calls write fresh data into it.
 */
interface SpotsRepository {
    /** Every spot with live conditions and daily forecast (no hourly data), in server order. */
    val spots: Flow<List<Spot>>

    val spotsSync: StateFlow<SyncStatus>

    /** Refresh the list unless the cached copy is younger than [maxAge]. */
    suspend fun refreshSpots(maxAge: Duration = Duration.ZERO): Result<Unit>

    /** One spot with its detail (history, hourly forecast of [model], AI analysis, models). */
    fun observeSpot(wgId: Int, model: String): Flow<Spot?>

    fun spotSync(wgId: Int, model: String): Flow<SyncStatus>

    /** Refresh one spot's detail unless the cached copy is younger than [maxAge]. */
    suspend fun refreshSpot(wgId: Int, model: String, maxAge: Duration = Duration.ZERO): Result<Unit>

    /** Grid-aligned hourly forecast (`/api/v1/forecast`), for widgets and alerts. */
    fun observeGridForecast(wgId: Int): Flow<List<ForecastEntry>>

    suspend fun refreshGridForecast(wgId: Int, maxAge: Duration = Duration.ZERO): Result<List<ForecastEntry>>

    suspend fun spots(ids: Collection<Int>): List<Spot>

    /** Drop cached detail (hourly forecasts) - the "clear cache" setting. */
    suspend fun clearCachedDetails()

    companion object {
        /** Model key the grid-aligned forecast is stored under. */
        const val GRID_MODEL = "grid"
    }
}
