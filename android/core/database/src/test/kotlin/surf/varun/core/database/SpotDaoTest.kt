package surf.varun.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import surf.varun.core.database.entity.ForecastColumns
import surf.varun.core.database.entity.HourlyForecastEntity
import surf.varun.core.database.entity.LiveConditionsEntity
import surf.varun.core.database.entity.RecentSpotEntity
import surf.varun.core.database.entity.SpotEntity

@RunWith(RobolectricTestRunner::class)
class SpotDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VarunDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val spots = db.spotDao()

    @After
    fun tearDown() = db.close()

    private fun spot(id: Int, order: Int = id) = SpotEntity(
        wgId = id, name = "Spot $id", country = "Poland", serverOrder = order,
        windguruUrl = null, windfinderUrl = null, icmUrl = null, webcamUrl = null, locationUrl = null,
        lat = null, lon = null, photoUrl = null, info = null, infoPl = null, sponsors = null, lastUpdated = null, fetchedAt = 1,
    )

    private fun live(id: Int) = LiveConditionsEntity(id, "", null, 10, 12, "N", 5, 1)

    private fun hour(id: Int, model: String, h: Int) =
        HourlyForecastEntity(id, model, "2026-10-24T%02d:00".format(h), ForecastColumns("", 10.0, 12.0, "N", null, null, null, null, null, null, null))

    @Test
    fun replacingTheListDropsRemovedSpotsAndQuietStations() = runBlocking<Unit> {
        spots.replaceList(listOf(spot(1), spot(2)), listOf(live(1), live(2)), emptyList())
        spots.replaceList(listOf(spot(2, order = 0), spot(3, order = 1)), listOf(live(3)), emptyList())
        val list = spots.observeSpots().first()
        assertThat(list.map { it.spot.wgId }).containsExactly(2, 3).inOrder()
        assertThat(list.first { it.spot.wgId == 2 }.live).isNull()
        assertThat(list.first { it.spot.wgId == 3 }.live).isNotNull()
    }

    @Test
    fun detailIsTrimmedButFavouritesRecentsCurrentAndGridSurvive() = runBlocking<Unit> {
        spots.upsertSpots((1..5).map { spot(it) })
        spots.insertHourly((1..5).map { hour(it, "gfs", 12) } + hour(5, "grid", 12))
        db.recentDao().upsertSpot(RecentSpotEntity(1, 100))
        db.recentDao().upsertSpot(RecentSpotEntity(2, 50))
        db.favouriteDao().add(3)

        spots.trimDetails(keep = 1, current = 4, gridModel = "grid")

        val kept = (1..5).filter { spots.hourly(it, "gfs").isNotEmpty() }
        assertThat(kept).containsExactly(1, 3, 4)
        assertThat(spots.hourly(5, "grid")).isNotEmpty()
    }

    @Test
    fun favouritesReorderAndAppend() = runBlocking<Unit> {
        val favourites = db.favouriteDao()
        favourites.add(7)
        favourites.add(3)
        favourites.reorder(listOf(3, 7))
        favourites.add(9)
        assertThat(favourites.all().map { it.wgId }).containsExactly(3, 7, 9).inOrder()
    }
}
