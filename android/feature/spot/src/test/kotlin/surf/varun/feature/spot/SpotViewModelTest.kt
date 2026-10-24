package surf.varun.feature.spot

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import surf.varun.core.data.repository.OnDemandState
import surf.varun.core.data.util.DataError
import surf.varun.core.data.util.SyncStatus
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.model.Spot
import surf.varun.core.navigation.SpotRoute
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SpotViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val spots = FakeSpotsRepository()
    private val favourites = FakeFavouritesRepository()
    private val alerts = FakeAlertsRepository()
    private val recents = FakeRecentsRepository()
    private val settings = FakeSettingsRepository()
    private val onDemand = FakeOnDemandRepository()
    private val connectivity = FakeConnectivity()

    private fun hour(day: Int, h: Int, wind: Double) = ForecastEntry(
        rawDate = "", time = LocalDateTime.of(2026, 10, day, h, 0), wind = wind, gusts = wind + 4, direction = Direction.SW,
    )

    private val spot = Spot(
        wgId = 500760, name = "Jastarnia", country = "Poland",
        hourly = listOf(hour(24, 10, 8.0), hour(24, 3, 30.0), hour(25, 12, 14.0), hour(26, 12, 5.0)),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(route: SpotRoute = SpotRoute(500760)): SpotViewModel {
        val vm = SpotViewModel(route, spots, favourites, alerts, recents, settings, onDemand, connectivity, Clock.systemUTC())
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun openingRecordsTheSpotAndRefreshesWithOneMinuteMaxAge() = runTest(dispatcher) {
        settings.model = "ifs"
        spots.spotsByModel.value = mapOf("ifs" to spot)
        val vm = viewModel()
        assertThat(recents.opened).containsExactly(500760)
        assertThat(spots.refreshCalls).containsExactly("ifs" to Duration.ofMinutes(1))
        val state = vm.uiState.first { it.spot != null }
        assertThat(state.model).isEqualTo("ifs")
        assertThat(state.loading).isFalse()
    }

    @Test
    fun autoRefreshRunsEveryMinute() = runTest(dispatcher) {
        val vm = viewModel()
        val job = backgroundScope.launch { vm.autoRefresh() }
        advanceTimeBy(Duration.ofSeconds(121).toMillis())
        job.cancel()
        assertThat(spots.refreshCalls).hasSize(3) // open + two ticks
    }

    @Test
    fun switchingModelRefreshesAndRemembersIt() = runTest(dispatcher) {
        spots.spotsByModel.value = mapOf("gfs" to spot, "ifs" to spot.copy(name = "IFS"))
        val vm = viewModel()
        vm.selectModel("ifs")
        assertThat(spots.refreshCalls.last().first).isEqualTo("ifs")
        assertThat(settings.remembered[500760]).isEqualTo("ifs")
        assertThat(vm.uiState.first { it.model == "ifs" }.spot!!.name).isEqualTo("IFS")
    }

    @Test
    fun paidOperationsNeedTheConfirmation() = runTest(dispatcher) {
        val vm = viewModel()
        vm.requestAnalysis()
        assertThat(vm.uiState.value.pendingConfirm).isEqualTo(PendingConfirm.AI_ANALYSIS)
        assertThat(onDemand.analysisCalls).isEmpty()
        vm.dismissConfirm()
        vm.confirm("pl") // nothing pending any more
        assertThat(onDemand.analysisCalls).isEmpty()

        vm.requestAnalysis()
        vm.confirm("pl")
        assertThat(onDemand.analysisCalls).containsExactly("pl")
        assertThat(vm.uiState.value.pendingConfirm).isNull()
    }

    @Test
    fun paidOperationIsNotStartedTwiceWhileRunning() = runTest(dispatcher) {
        onDemand.states.value = mapOf(surf.varun.core.data.repository.OnDemandKind.AI_ANALYSIS to OnDemandState.Running)
        val vm = viewModel()
        vm.uiState.first { it.analysisState == OnDemandState.Running }
        vm.requestAnalysis()
        vm.confirm("en")
        assertThat(onDemand.analysisCalls).isEmpty()
    }

    @Test
    fun successfulIcmSelectsTheIcmModel() = runTest(dispatcher) {
        onDemand.icmResult = Result.success(spot)
        val vm = viewModel()
        vm.requestIcm()
        vm.confirm("en")
        assertThat(onDemand.icmCalls).isEqualTo(1)
        assertThat(vm.uiState.first { it.model == ForecastModelOption.ICM_KEY }.model).isEqualTo("icm")
        assertThat(spots.refreshCalls.last().first).isEqualTo("icm")
    }

    @Test
    fun notFoundOffersRemovalEverywhere() = runTest(dispatcher) {
        favourites.ids.value = listOf(500760, 1)
        spots.syncs.value = mapOf("gfs" to SyncStatus(lastError = DataError.NotFound(null)))
        val vm = viewModel()
        val state = vm.uiState.first { it.notFound }
        assertThat(state.loading).isFalse()
        vm.removeEverywhere()
        assertThat(favourites.ids.value).containsExactly(1)
        assertThat(alerts.deletedForSpot).containsExactly(500760)
        assertThat(vm.uiState.first { it.removedEverywhere }.removedEverywhere).isTrue()
    }

    @Test
    fun windyDaysFilterKeepsOnlyDaysWithTwelveKnots() = runTest(dispatcher) {
        spots.spotsByModel.value = mapOf("gfs" to spot)
        val vm = viewModel()
        assertThat(vm.uiState.first { it.spot != null }.days.map { it.date.dayOfMonth }).containsExactly(24, 25, 26).inOrder()
        vm.setWindyOnly(true)
        val filtered = vm.uiState.first { it.windyOnly }
        // the 30 kts on the 24th is at 03:00, outside daytime
        assertThat(filtered.days.map { it.date.dayOfMonth }).containsExactly(25)
        assertThat(filtered.noWindyDays).isFalse()

        spots.spotsByModel.value = mapOf("gfs" to spot.copy(hourly = listOf(hour(24, 10, 5.0))))
        assertThat(vm.uiState.first { it.spot?.hourly?.size == 1 }.noWindyDays).isTrue()
    }

    @Test
    fun focusTimeComesFromTheRoute() = runTest(dispatcher) {
        val vm = viewModel(SpotRoute(500760, focusTime = "2026-10-25T13:00"))
        assertThat(vm.uiState.value.focusTime).isEqualTo(LocalDateTime.of(2026, 10, 25, 13, 0))
        vm.focusConsumed()
        assertThat(vm.uiState.value.focusTime).isNull()
    }

    @Test
    fun favouriteToggleFollowsTheRepository() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleFavourite()
        assertThat(vm.uiState.first { it.favourite }.favourite).isTrue()
    }
}
