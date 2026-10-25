package surf.varun.feature.map

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import surf.varun.core.model.Coordinates
import surf.varun.core.model.TimelineReach
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val spots = FakeSpotsRepository(TestData.spots)
    private val favourites = FakeFavouritesRepository()
    private val settings = FakeSettingsRepository()
    private val clock = Clock.fixed(TestData.now.toInstant(), TestData.now.zone)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        timeline: FakeWindTimelineRepository = FakeWindTimelineRepository(TestData.timeline),
        location: FakeLocationRepository = FakeLocationRepository(),
    ) = MapViewModel(spots, favourites, settings, timeline, location, clock)

    /** Collect until the state satisfies [predicate] (the state is built on Dispatchers.Default). */
    private suspend fun MapViewModel.awaitState(predicate: (MapUiState) -> Boolean): MapUiState =
        withTimeout(5_000) { uiState.first { !it.loading && predicate(it) } }

    @Test
    fun loadsTheTimelineForTheChosenReach() = runBlocking<Unit> {
        settings.state.value = settings.state.value.copy(timelineReach = TimelineReach.FULL_RUN)
        val timeline = FakeWindTimelineRepository(TestData.timeline)
        val vm = viewModel(timeline)
        val state = vm.awaitState { it.timeline != null }
        assertThat(timeline.requestedHours).contains(384)
        assertThat(state.steps.lastStep).isEqualTo(3)
        assertThat(state.features.map { it.wgId }).containsExactly(500760, 1, 3)
    }

    @Test
    fun stepSwapsTheFeaturesAndIsClampedToTheGrid() = runBlocking<Unit> {
        val vm = viewModel()
        vm.awaitState { it.timeline != null }
        vm.setStep(1)
        assertThat(vm.awaitState { it.step == 1 }.features.first { it.wgId == 500760 }.wind).isEqualTo(20)
        vm.setStep(50)
        assertThat(vm.awaitState { it.step == 3 }.features.first { it.wgId == 1 }.wind).isEqualTo(32)
    }

    @Test
    fun selectionFollowsTheStep() = runBlocking<Unit> {
        val vm = viewModel()
        vm.awaitState { it.timeline != null }
        vm.select(500760)
        assertThat(vm.awaitState { it.selected != null }.selectedReading!!.isLive).isTrue()
        vm.setStep(1)
        assertThat(vm.awaitState { it.step == 1 }.selectedReading!!.wind).isEqualTo(20.0)
        vm.select(null)
        assertThat(vm.awaitState { it.selected == null }.selectedReading).isNull()
    }

    @Test
    fun filtersNarrowWhatIsDrawn() = runBlocking<Unit> {
        val vm = viewModel()
        vm.awaitState { it.features.isNotEmpty() }
        vm.setCountry("Spain")
        assertThat(vm.awaitState { it.filters.country == "Spain" }.features.map { it.wgId }).containsExactly(1)
        vm.setCountry(null)
        vm.toggleLiveOnly()
        assertThat(vm.awaitState { it.filters.liveOnly }.features.map { it.wgId }).containsExactly(500760)
        vm.toggleLiveOnly()
        favourites.setFavourite(3, true)
        vm.toggleFavouritesOnly()
        val state = vm.awaitState { it.filters.favouritesOnly && it.favourites.isNotEmpty() }
        assertThat(state.features.map { it.wgId }).containsExactly(3)
        // the country sheet still counts every spot
        assertThat(state.allSpots).hasSize(4)
    }

    @Test
    fun failedTimelineIsReportedButNowStillWorks() = runBlocking<Unit> {
        val vm = viewModel(FakeWindTimelineRepository(null, fail = true))
        val state = vm.awaitState { it.timelineFailed }
        assertThat(state.steps.lastStep).isEqualTo(0)
        assertThat(state.features.first { it.wgId == 500760 }.wind).isEqualTo(18)
    }

    @Test
    fun layerTogglesArePersisted() = runBlocking<Unit> {
        val vm = viewModel()
        vm.setWindField(false)
        vm.setMarkers(false)
        vm.setSatellite(true)
        val saved = settings.state.value
        assertThat(saved.mapWindField).isFalse()
        assertThat(saved.mapMarkers).isFalse()
        assertThat(saved.mapSatellite).isTrue()
    }

    @Test
    fun keepFreshRefreshesTheListWhileRunning() = runBlocking<Unit> {
        val vm = viewModel()
        val job = launch(Dispatchers.Default) { vm.keepFresh() }
        withTimeout(5_000) { while (spots.listRefreshes == 0) kotlinx.coroutines.delay(10) }
        job.cancel()
        assertThat(spots.listRefreshes).isEqualTo(1)
    }

    @Test
    fun favouriteToggle() = runBlocking<Unit> {
        val vm = viewModel()
        vm.toggleFavourite(1)
        assertThat(vm.awaitState { 1 in it.favourites }.favourites).containsExactly(1)
    }

    @Test
    fun locateShowsTheDotAndSendsWhereTheUserIs() = runBlocking<Unit> {
        val here = Coordinates(54.5, 18.5)
        val location = FakeLocationRepository(here).apply { answer = CompletableDeferred() }
        val vm = viewModel(location = location)
        vm.locate()
        assertThat(vm.myLocation.value.locating).isTrue()
        location.answer.complete(Unit)
        assertThat(withTimeout(5_000) { vm.locateResults.first() }).isEqualTo(LocateResult.Found(here))
        assertThat(vm.myLocation.value).isEqualTo(MyLocationState(position = here, locating = false))
    }

    @Test
    fun pressesDuringASearchJoinItInsteadOfQueueing() = runBlocking<Unit> {
        val location = FakeLocationRepository(Coordinates(54.5, 18.5)).apply { answer = CompletableDeferred() }
        val vm = viewModel(location = location)
        repeat(3) { vm.locate() }
        location.answer.complete(Unit)
        withTimeout(5_000) { vm.locateResults.first() }
        assertThat(location.calls).isEqualTo(1)
    }

    @Test
    fun failedSearchUnblocksAndKeepsTheLastDot() = runBlocking<Unit> {
        val here = Coordinates(54.5, 18.5)
        val location = FakeLocationRepository(here)
        val vm = viewModel(location = location)
        vm.locate()
        withTimeout(5_000) { vm.locateResults.first() }
        location.location = null
        vm.locate()
        assertThat(withTimeout(5_000) { vm.locateResults.first() }).isEqualTo(LocateResult.Failed)
        assertThat(vm.myLocation.value).isEqualTo(MyLocationState(position = here, locating = false))
    }

    @Test
    fun cancelledSearchSendsNothing() = runBlocking<Unit> {
        val location = FakeLocationRepository(Coordinates(54.5, 18.5)).apply { answer = CompletableDeferred() }
        val vm = viewModel(location = location)
        vm.locate()
        vm.cancelLocate()
        location.answer.complete(Unit)
        assertThat(vm.myLocation.value.locating).isFalse()
        assertThat(withTimeoutOrNull(200) { vm.locateResults.first() }).isNull()
        assertThat(vm.myLocation.value.position).isNull()
    }
}
