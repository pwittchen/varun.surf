package surf.varun.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import surf.varun.core.database.entity.AiAnalysisEntity
import surf.varun.core.database.entity.AvailableModelEntity
import surf.varun.core.database.entity.DailyForecastEntity
import surf.varun.core.database.entity.HourlyForecastEntity
import surf.varun.core.database.entity.LiveConditionsEntity
import surf.varun.core.database.entity.LiveHistoryEntity
import surf.varun.core.database.entity.SpotDetailEntity
import surf.varun.core.database.entity.SpotEntity
import surf.varun.core.database.entity.SpotWithConditions

@Dao
interface SpotDao {
    @Transaction
    @Query("SELECT * FROM spot ORDER BY serverOrder")
    fun observeSpots(): Flow<List<SpotWithConditions>>

    @Transaction
    @Query("SELECT * FROM spot ORDER BY serverOrder")
    suspend fun spots(): List<SpotWithConditions>

    @Transaction
    @Query("SELECT * FROM spot WHERE wgId = :wgId")
    fun observeSpot(wgId: Int): Flow<SpotWithConditions?>

    @Transaction
    @Query("SELECT * FROM spot WHERE wgId IN (:ids)")
    suspend fun spots(ids: List<Int>): List<SpotWithConditions>

    @Query("SELECT MAX(fetchedAt) FROM spot")
    suspend fun lastListFetch(): Long?

    @Query("SELECT COUNT(*) FROM spot")
    suspend fun count(): Int

    @Query("SELECT * FROM live_history WHERE wgId = :wgId ORDER BY position")
    fun observeHistory(wgId: Int): Flow<List<LiveHistoryEntity>>

    @Query("SELECT * FROM hourly_forecast WHERE wgId = :wgId AND model = :model ORDER BY time")
    fun observeHourly(wgId: Int, model: String): Flow<List<HourlyForecastEntity>>

    @Query("SELECT * FROM hourly_forecast WHERE wgId = :wgId AND model = :model ORDER BY time")
    suspend fun hourly(wgId: Int, model: String): List<HourlyForecastEntity>

    @Query("SELECT * FROM ai_analysis WHERE wgId = :wgId")
    fun observeAnalysis(wgId: Int): Flow<List<AiAnalysisEntity>>

    @Query("SELECT * FROM available_model WHERE wgId = :wgId ORDER BY position")
    fun observeModels(wgId: Int): Flow<List<AvailableModelEntity>>

    @Query("SELECT * FROM spot_detail WHERE wgId = :wgId AND model = :model")
    suspend fun detail(wgId: Int, model: String): SpotDetailEntity?

    @Query("SELECT * FROM spot_detail WHERE wgId = :wgId AND model = :model")
    fun observeDetail(wgId: Int, model: String): Flow<SpotDetailEntity?>

    // --- list refresh -------------------------------------------------------

    @Upsert
    suspend fun upsertSpots(spots: List<SpotEntity>)

    @Query("DELETE FROM spot WHERE wgId NOT IN (:keep)")
    suspend fun deleteSpotsNotIn(keep: List<Int>)

    @Query("DELETE FROM live_conditions")
    suspend fun clearLive()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLive(live: List<LiveConditionsEntity>)

    @Query("DELETE FROM daily_forecast")
    suspend fun clearDaily()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDaily(daily: List<DailyForecastEntity>)

    /**
     * Replace the whole list in one transaction: removed spots go, live readings
     * of spots whose station went quiet go, every daily forecast is replaced.
     */
    @Transaction
    suspend fun replaceList(
        spots: List<SpotEntity>,
        live: List<LiveConditionsEntity>,
        daily: List<DailyForecastEntity>,
    ) {
        deleteSpotsNotIn(spots.map { it.wgId })
        upsertSpots(spots)
        clearLive()
        insertLive(live)
        clearDaily()
        insertDaily(daily)
    }

    // --- single spot refresh ------------------------------------------------

    @Query("DELETE FROM live_conditions WHERE wgId = :wgId")
    suspend fun deleteLive(wgId: Int)

    @Query("DELETE FROM live_history WHERE wgId = :wgId")
    suspend fun deleteHistory(wgId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: List<LiveHistoryEntity>)

    @Query("DELETE FROM daily_forecast WHERE wgId = :wgId")
    suspend fun deleteDaily(wgId: Int)

    @Query("DELETE FROM hourly_forecast WHERE wgId = :wgId AND model = :model")
    suspend fun deleteHourly(wgId: Int, model: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHourly(hourly: List<HourlyForecastEntity>)

    @Query("DELETE FROM ai_analysis WHERE wgId = :wgId")
    suspend fun deleteAnalysis(wgId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: List<AiAnalysisEntity>)

    @Query("DELETE FROM available_model WHERE wgId = :wgId")
    suspend fun deleteModels(wgId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AvailableModelEntity>)

    @Upsert
    suspend fun upsertDetail(detail: SpotDetailEntity)

    @Transaction
    suspend fun replaceSpot(
        spot: SpotEntity,
        live: LiveConditionsEntity?,
        history: List<LiveHistoryEntity>,
        daily: List<DailyForecastEntity>,
        model: String,
        hourly: List<HourlyForecastEntity>,
        analysis: List<AiAnalysisEntity>,
        models: List<AvailableModelEntity>,
        fetchedAt: Long,
    ) {
        upsertSpots(listOf(spot))
        deleteLive(spot.wgId)
        live?.let { insertLive(listOf(it)) }
        deleteHistory(spot.wgId)
        insertHistory(history)
        deleteDaily(spot.wgId)
        insertDaily(daily)
        deleteHourly(spot.wgId, model)
        insertHourly(hourly)
        deleteAnalysis(spot.wgId)
        insertAnalysis(analysis)
        deleteModels(spot.wgId)
        insertModels(models)
        upsertDetail(SpotDetailEntity(spot.wgId, model, fetchedAt))
    }

    /*
     * Detail (hourly, history) is kept for the [keep] most recently opened spots,
     * the favourites and [current]. The grid forecast that widgets and alerts read
     * ([gridModel]) is left alone - it is refreshed by its own workers.
     */
    @Query(
        """
        DELETE FROM hourly_forecast WHERE model != :gridModel AND wgId != :current AND wgId NOT IN (
            SELECT wgId FROM recent_spot ORDER BY openedAt DESC LIMIT :keep
        ) AND wgId NOT IN (SELECT wgId FROM favourite)
        """,
    )
    suspend fun trimHourly(keep: Int, current: Int, gridModel: String)

    @Query(
        """
        DELETE FROM live_history WHERE wgId != :current AND wgId NOT IN (
            SELECT wgId FROM recent_spot ORDER BY openedAt DESC LIMIT :keep
        ) AND wgId NOT IN (SELECT wgId FROM favourite)
        """,
    )
    suspend fun trimHistory(keep: Int, current: Int)

    @Query(
        """
        DELETE FROM spot_detail WHERE model != :gridModel AND wgId != :current AND wgId NOT IN (
            SELECT wgId FROM recent_spot ORDER BY openedAt DESC LIMIT :keep
        ) AND wgId NOT IN (SELECT wgId FROM favourite)
        """,
    )
    suspend fun trimDetail(keep: Int, current: Int, gridModel: String)

    @Transaction
    suspend fun trimDetails(keep: Int, current: Int, gridModel: String) {
        trimHourly(keep, current, gridModel)
        trimHistory(keep, current)
        trimDetail(keep, current, gridModel)
    }

    @Query("DELETE FROM spot_detail")
    suspend fun clearDetails()

    @Query("DELETE FROM hourly_forecast")
    suspend fun clearHourly()

    @Transaction
    suspend fun clearCachedDetails() {
        clearDetails()
        clearHourly()
    }
}
