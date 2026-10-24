package surf.varun.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import surf.varun.core.database.entity.AlertEntity
import surf.varun.core.database.entity.FavouriteEntity
import surf.varun.core.database.entity.RecentSearchEntity
import surf.varun.core.database.entity.RecentSpotEntity

@Dao
interface FavouriteDao {
    @Query("SELECT * FROM favourite ORDER BY position")
    fun observe(): Flow<List<FavouriteEntity>>

    @Query("SELECT * FROM favourite ORDER BY position")
    suspend fun all(): List<FavouriteEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM favourite")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favourite: FavouriteEntity)

    @Query("DELETE FROM favourite WHERE wgId = :wgId")
    suspend fun delete(wgId: Int)

    @Query("DELETE FROM favourite")
    suspend fun clear()

    @Insert
    suspend fun insertAll(favourites: List<FavouriteEntity>)

    @Transaction
    suspend fun add(wgId: Int) = insert(FavouriteEntity(wgId, maxPosition() + 1))

    /** Persist a new order: positions follow the order of [ids]. */
    @Transaction
    suspend fun reorder(ids: List<Int>) {
        clear()
        insertAll(ids.mapIndexed { index, id -> FavouriteEntity(id, index) })
    }
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alert ORDER BY id")
    fun observe(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alert ORDER BY id")
    suspend fun all(): List<AlertEntity>

    @Query("SELECT * FROM alert WHERE id = :id")
    suspend fun get(id: Long): AlertEntity?

    @Upsert
    suspend fun upsert(alert: AlertEntity): Long

    @Update
    suspend fun update(alert: AlertEntity)

    @Query("DELETE FROM alert WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM alert WHERE wgId = :wgId")
    suspend fun deleteForSpot(wgId: Int)

    @Query("UPDATE alert SET lastFiredAt = :at WHERE id = :id")
    suspend fun markFired(id: Long, at: Long)

    @Query("UPDATE alert SET mutedUntil = :until WHERE id = :id")
    suspend fun mute(id: Long, until: Long)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_spot ORDER BY openedAt DESC LIMIT :limit")
    fun observeSpots(limit: Int): Flow<List<RecentSpotEntity>>

    @Upsert
    suspend fun upsertSpot(spot: RecentSpotEntity)

    @Query("SELECT * FROM recent_search ORDER BY at DESC LIMIT :limit")
    fun observeSearches(limit: Int): Flow<List<RecentSearchEntity>>

    @Upsert
    suspend fun upsertSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_search")
    suspend fun clearSearches()

    @Query("DELETE FROM recent_search WHERE `query` NOT IN (SELECT `query` FROM recent_search ORDER BY at DESC LIMIT :keep)")
    suspend fun trimSearches(keep: Int)

    @Query("DELETE FROM recent_spot WHERE wgId NOT IN (SELECT wgId FROM recent_spot ORDER BY openedAt DESC LIMIT :keep)")
    suspend fun trimSpots(keep: Int)
}
