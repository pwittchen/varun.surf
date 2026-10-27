package surf.varun.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import surf.varun.core.data.repository.CachedWindTimelineRepository
import surf.varun.core.data.repository.NetworkOnDemandRepository
import surf.varun.core.data.repository.NetworkServiceRepository
import surf.varun.core.data.repository.OfflineFirstSpotsRepository
import surf.varun.core.data.repository.OnDemandKind
import surf.varun.core.data.repository.OnDemandState
import surf.varun.core.data.repository.RoomAlertsRepository
import surf.varun.core.data.repository.RoomFavouritesRepository
import surf.varun.core.data.repository.RoomRecentsRepository
import surf.varun.core.data.util.DataError
import surf.varun.core.database.VarunDatabase
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Direction
import surf.varun.core.model.WindAlert
import surf.varun.core.testing.Fixtures
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RepositoriesTest {
    private val backend = TestBackend()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VarunDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = Instant.parse("2026-10-24T18:30:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val spots = OfflineFirstSpotsRepository(backend.dataSource, db.spotDao(), clock)

    @After
    fun tearDown() {
        db.close()
        backend.close()
    }

    @Test
    fun listRefreshFillsTheCacheAndRespectsMaxAge() = runBlocking<Unit> {
        assertThat(spots.refreshSpots().isSuccess).isTrue()
        val list = spots.spots.first()
        assertThat(list).hasSize(60)
        assertThat(list.count { it.live != null }).isEqualTo(10)
        assertThat(list.first().daily).isNotEmpty()
        assertThat(spots.spotsSync.value.lastSuccessAt).isEqualTo(now)

        now = now.plusSeconds(60 * 5)
        spots.refreshSpots(maxAge = Duration.ofMinutes(15))
        assertThat(backend.count("/api/v1/spots")).isEqualTo(1)
        now = now.plusSeconds(60 * 11)
        spots.refreshSpots(maxAge = Duration.ofMinutes(15))
        assertThat(backend.count("/api/v1/spots")).isEqualTo(2)
    }

    @Test
    fun failedRefreshKeepsTheCache() = runBlocking<Unit> {
        spots.refreshSpots()
        backend.overrides["/api/v1/spots"] = { MockResponse.Builder().code(500).build() }
        val result = spots.refreshSpots()
        assertThat(result.exceptionOrNull()).isInstanceOf(DataError::class.java)
        assertThat(spots.spots.first()).hasSize(60)
        assertThat(spots.spotsSync.value.lastError).isNotNull()
        assertThat(spots.spotsSync.value.lastSuccessAt).isNotNull()
    }

    @Test
    fun spotDetailIsCachedPerModel() = runBlocking<Unit> {
        spots.refreshSpots()
        assertThat(spots.refreshSpot(Fixtures.SPOT_ID, "gfs").isSuccess).isTrue()
        val spot = spots.observeSpot(Fixtures.SPOT_ID, "gfs").filterNotNull().first()
        assertThat(spot.hourly.size).isGreaterThan(100)
        assertThat(spot.liveHistory.size).isGreaterThan(100)
        assertThat(spot.availableModels).isNotEmpty()
        assertThat(spot.info!!.type).isEqualTo("Lagoon, flat water")
        // GFS goes through /spots/{id}, which triggers the server's model discovery
        assertThat(backend.count("/api/v1/spots/${Fixtures.SPOT_ID}")).isEqualTo(1)

        spots.refreshSpot(Fixtures.SPOT_ID, "ifs")
        assertThat(backend.count("/api/v1/spots/${Fixtures.SPOT_ID}/ifs")).isEqualTo(1)
        assertThat(spots.observeSpot(Fixtures.SPOT_ID, "ifs").first()!!.hourly).isNotEmpty()

        spots.refreshSpot(Fixtures.SPOT_ID, "gfs", maxAge = Duration.ofMinutes(1))
        assertThat(backend.count("/api/v1/spots/${Fixtures.SPOT_ID}")).isEqualTo(1)
        assertThat(spots.spotSync(Fixtures.SPOT_ID, "gfs").first().lastSuccessAt).isEqualTo(now)
    }

    @Test
    fun deepLinkedSpotDoesNotMakeTheListLookFresh() = runBlocking<Unit> {
        spots.refreshSpot(Fixtures.SPOT_ID, "gfs")
        spots.refreshSpots(maxAge = Duration.ofMinutes(15))
        assertThat(backend.count("/api/v1/spots")).isEqualTo(1)
    }

    @Test
    fun removedSpotIsReportedAsNotFound() = runBlocking<Unit> {
        val result = spots.refreshSpot(123, "gfs")
        assertThat(result.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(spots.spotSync(123, "gfs").first().lastError).isInstanceOf(DataError.NotFound::class.java)
    }

    @Test
    fun gridForecastIsStoredForWidgetsAndAlerts() = runBlocking<Unit> {
        spots.refreshSpots()
        val hours = spots.refreshGridForecast(Fixtures.SPOT_ID).getOrThrow()
        assertThat(hours).isNotEmpty()
        assertThat(spots.observeGridForecast(Fixtures.SPOT_ID).first()).hasSize(hours.size)
        spots.refreshGridForecast(Fixtures.SPOT_ID, maxAge = Duration.ofHours(1))
        assertThat(backend.count("/api/v1/forecast/${Fixtures.SPOT_ID}")).isEqualTo(1)
    }

    @Test
    fun paidCallsAreSingleFlightAndWriteTheResult() = runBlocking<Unit> {
        spots.refreshSpots()
        val analysed = Fixtures.spot.replace("\"aiAnalysisEn\":null", "\"aiAnalysisEn\":\"Windy weekend\"")
        backend.overrides["/api/v1/spots/${Fixtures.SPOT_ID}/analysis"] = {
            Thread.sleep(200)
            backend.ok(analysed)
        }
        val repo = NetworkOnDemandRepository(backend.dataSource, db.spotDao(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val first = repo.generateAnalysis(Fixtures.SPOT_ID, "en")
        val second = repo.generateAnalysis(Fixtures.SPOT_ID, "en")
        assertThat(second).isSameInstanceAs(first)
        assertThat(repo.state(Fixtures.SPOT_ID, OnDemandKind.AI_ANALYSIS).first()).isEqualTo(OnDemandState.Running)
        assertThat(first.await().isSuccess).isTrue()
        assertThat(backend.count("/api/v1/spots/${Fixtures.SPOT_ID}/analysis")).isEqualTo(1)
        val spot = spots.observeSpot(Fixtures.SPOT_ID, "gfs").filterNotNull().first()
        assertThat(spot.aiAnalysisEn!!.text).isEqualTo("Windy weekend")
        assertThat(repo.state(Fixtures.SPOT_ID, OnDemandKind.AI_ANALYSIS).first()).isEqualTo(OnDemandState.Succeeded)
    }

    @Test
    fun paidCallFailureIsReportedNotRetried() = runBlocking<Unit> {
        backend.overrides["/api/v1/spots/${Fixtures.SPOT_ID}/analysis"] = { MockResponse.Builder().code(503).build() }
        val repo = NetworkOnDemandRepository(backend.dataSource, db.spotDao(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val result = repo.generateAnalysis(Fixtures.SPOT_ID, "en").await()
        assertThat(result.exceptionOrNull()).isInstanceOf(DataError.Unavailable::class.java)
        assertThat(backend.count("/api/v1/spots/${Fixtures.SPOT_ID}/analysis")).isEqualTo(1)
        assertThat(repo.state(Fixtures.SPOT_ID, OnDemandKind.AI_ANALYSIS).first()).isInstanceOf(OnDemandState.Failed::class.java)
    }

    @Test
    fun favouritesKeepTheirOrder() = runBlocking<Unit> {
        val favourites = RoomFavouritesRepository(db.favouriteDao())
        favourites.setFavourite(3, true)
        favourites.setFavourite(1, true)
        assertThat(favourites.toggle(2)).isTrue()
        assertThat(favourites.favouriteIds.first()).containsExactly(3, 1, 2).inOrder()
        favourites.reorder(listOf(2, 3, 1))
        assertThat(favourites.favouriteIds.first()).containsExactly(2, 3, 1).inOrder()
        assertThat(favourites.toggle(3)).isFalse()
        assertThat(favourites.favouriteIds.first()).containsExactly(2, 1).inOrder()
    }

    @Test
    fun alertsRoundTrip() = runBlocking<Unit> {
        val alerts = RoomAlertsRepository(db.alertDao())
        val id = alerts.save(WindAlert(wgId = 1, spotName = "A", minWind = 15, directions = setOf(Direction.SW, Direction.W), trigger = AlertTrigger.LIVE))
        val saved = alerts.get(id)!!
        assertThat(saved.directions).containsExactly(Direction.SW, Direction.W)
        assertThat(alerts.save(saved.copy(minWind = 18))).isEqualTo(id)
        alerts.markFired(id, now)
        assertThat(alerts.get(id)!!.lastFiredAt).isEqualTo(now)
        assertThat(alerts.get(id)!!.minWind).isEqualTo(18)
        alerts.deleteForSpot(1)
        assertThat(alerts.all()).isEmpty()
    }

    @Test
    fun recentsAreDeduplicatedAndOrdered() = runBlocking<Unit> {
        val recents = RoomRecentsRepository(db.recentDao(), clock)
        recents.spotOpened(1)
        now = now.plusSeconds(1)
        recents.spotOpened(2)
        now = now.plusSeconds(1)
        recents.spotOpened(1)
        assertThat(recents.recentSpotIds.first()).containsExactly(1, 2).inOrder()
        recents.searched("t")
        recents.searched("tarifa")
        assertThat(recents.recentSearches.first()).containsExactly("tarifa")
    }

    @Test
    fun windTimelineIsCachedOnDisk() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = NetworkServiceRepository(backend.dataSource)
        val repo = CachedWindTimelineRepository(context, backend.dataSource, service, clock, Dispatchers.IO)
        val first = repo.ensureFresh(120).getOrThrow()
        assertThat(first.timeline.size).isEqualTo(120)
        repo.ensureFresh(120)
        assertThat(backend.count("/api/v1/wind")).isEqualTo(1)

        // a fresh repository (process restart) reads the file; offline it still has data
        backend.overrides["/api/v1/wind"] = { MockResponse.Builder().code(500).build() }
        val restarted = CachedWindTimelineRepository(context, backend.dataSource, service, clock, Dispatchers.IO)
        now = now.plusSeconds(2 * 3600)
        assertThat(restarted.ensureFresh(120).getOrThrow().timeline.size).isEqualTo(120)
    }
}
