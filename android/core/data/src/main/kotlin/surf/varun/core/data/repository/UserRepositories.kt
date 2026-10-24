package surf.varun.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import surf.varun.core.data.mapper.toEntity
import surf.varun.core.data.mapper.toModel
import surf.varun.core.database.dao.AlertDao
import surf.varun.core.database.dao.FavouriteDao
import surf.varun.core.database.dao.RecentDao
import surf.varun.core.database.entity.RecentSearchEntity
import surf.varun.core.database.entity.RecentSpotEntity
import surf.varun.core.model.WindAlert
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface FavouritesRepository {
    /** Favourite spot ids in the user's order. */
    val favouriteIds: Flow<List<Int>>

    suspend fun setFavourite(wgId: Int, favourite: Boolean)

    /** Flip and return the new state. */
    suspend fun toggle(wgId: Int): Boolean

    suspend fun reorder(ids: List<Int>)
}

@Singleton
class RoomFavouritesRepository @Inject constructor(private val dao: FavouriteDao) : FavouritesRepository {
    override val favouriteIds: Flow<List<Int>> = dao.observe().map { rows -> rows.map { it.wgId } }

    override suspend fun setFavourite(wgId: Int, favourite: Boolean) {
        if (favourite) dao.add(wgId) else dao.delete(wgId)
    }

    override suspend fun toggle(wgId: Int): Boolean {
        val isFavourite = dao.all().any { it.wgId == wgId }
        setFavourite(wgId, !isFavourite)
        return !isFavourite
    }

    override suspend fun reorder(ids: List<Int>) = dao.reorder(ids)
}

interface AlertsRepository {
    val alerts: Flow<List<WindAlert>>

    suspend fun all(): List<WindAlert>

    suspend fun get(id: Long): WindAlert?

    /** Insert or update; returns the alert's id. */
    suspend fun save(alert: WindAlert): Long

    suspend fun delete(id: Long)

    suspend fun deleteForSpot(wgId: Int)

    suspend fun markFired(id: Long, at: Instant)

    suspend fun mute(id: Long, until: Instant)
}

@Singleton
class RoomAlertsRepository @Inject constructor(private val dao: AlertDao) : AlertsRepository {
    override val alerts: Flow<List<WindAlert>> = dao.observe().map { rows -> rows.map { it.toModel() } }

    override suspend fun all(): List<WindAlert> = dao.all().map { it.toModel() }

    override suspend fun get(id: Long): WindAlert? = dao.get(id)?.toModel()

    override suspend fun save(alert: WindAlert): Long {
        val id = dao.upsert(alert.toEntity())
        // Upsert returns -1 when it updated an existing row
        return if (id == -1L) alert.id else id
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteForSpot(wgId: Int) = dao.deleteForSpot(wgId)

    override suspend fun markFired(id: Long, at: Instant) = dao.markFired(id, at.toEpochMilli())

    override suspend fun mute(id: Long, until: Instant) = dao.mute(id, until.toEpochMilli())
}

interface RecentsRepository {
    val recentSpotIds: Flow<List<Int>>
    val recentSearches: Flow<List<String>>

    suspend fun spotOpened(wgId: Int)

    suspend fun searched(query: String)

    suspend fun clearSearches()
}

@Singleton
class RoomRecentsRepository @Inject constructor(
    private val dao: RecentDao,
    private val clock: Clock,
) : RecentsRepository {
    override val recentSpotIds: Flow<List<Int>> = dao.observeSpots(RECENT_SPOTS).map { rows -> rows.map { it.wgId } }
    override val recentSearches: Flow<List<String>> = dao.observeSearches(RECENT_SEARCHES).map { rows -> rows.map { it.query } }

    override suspend fun spotOpened(wgId: Int) {
        dao.upsertSpot(RecentSpotEntity(wgId, clock.millis()))
        dao.trimSpots(KEEP_SPOTS)
    }

    override suspend fun searched(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        dao.upsertSearch(RecentSearchEntity(trimmed, clock.millis()))
        dao.trimSearches(RECENT_SEARCHES)
    }

    override suspend fun clearSearches() = dao.clearSearches()

    private companion object {
        const val RECENT_SPOTS = 10
        const val RECENT_SEARCHES = 8

        /** Recently opened spots also decide which spots keep their cached detail. */
        const val KEEP_SPOTS = 60
    }
}
