package surf.varun.feature.alerts

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Direction
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Spot
import surf.varun.core.model.SpotInfo
import surf.varun.core.model.UserSettings
import surf.varun.core.model.WindUnit
import surf.varun.core.navigation.AlertEditorRoute

@OptIn(ExperimentalCoroutinesApi::class)
class AlertEditorViewModelTest {
    private val jastarnia = Spot(
        500760, "Jastarnia", "Poland",
        live = LiveConditions("", null, 10, 12, Direction.S, 10),
        info = SpotInfo(bestWind = "W, SW"),
    )
    private val lake = Spot(7, "Lake", "Austria", info = SpotInfo(bestWind = "N, NW"))
    private val alerts = FakeAlertsRepository()
    private val spots = FakeSpotsRepository(listOf(jastarnia, lake))
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(route: AlertEditorRoute = AlertEditorRoute()) = AlertEditorViewModel(route, alerts, spots, settings)

    @Test
    fun prefillsSpotAndDirectionsFromTheRoute() = runTest {
        val vm = vm(AlertEditorRoute(wgId = 500760))
        val state = vm.state.value
        assertThat(state.spot!!.name).isEqualTo("Jastarnia")
        assertThat(state.directions).containsExactly(Direction.W, Direction.SW)
    }

    @Test
    fun sliderValuesInTheUsersUnitAreStoredInKnots() = runTest {
        settings.current.value = UserSettings(windUnit = WindUnit.METERS_PER_SECOND)
        val vm = vm(AlertEditorRoute(wgId = 500760))
        vm.setMinWind(10f) // 10 m/s
        assertThat(vm.state.value.minWind).isEqualTo(19)
        settings.current.value = UserSettings(windUnit = WindUnit.KILOMETERS_PER_HOUR)
        vm.setMinWind(37f)
        assertThat(vm.state.value.minWind).isEqualTo(20)
        assertThat(AlertEditorViewModel.toKnots(5f, WindUnit.BEAUFORT)).isEqualTo(17)
        assertThat(AlertEditorViewModel.toKnots(500f, WindUnit.KNOTS)).isEqualTo(45)
    }

    @Test
    fun rejectsMaxBelowMinAndMissingSpot() = runTest {
        val vm = vm()
        vm.save()
        assertThat(vm.state.value.error).isEqualTo(EditorError.NO_SPOT)
        vm.selectSpot(jastarnia)
        vm.setMinWind(20f)
        vm.setMaxEnabled(true)
        vm.setMaxWind(15f)
        vm.save()
        assertThat(vm.state.value.error).isEqualTo(EditorError.MAX_BELOW_MIN)
        assertThat(alerts.all()).isEmpty()
    }

    @Test
    fun savesTheAlertAndReportsTheFirstOne() = runTest {
        val vm = vm(AlertEditorRoute(wgId = 500760))
        vm.setTrigger(AlertTrigger.LIVE)
        vm.toggleDirection(Direction.NW)
        vm.setQuietHours(false)
        vm.save()
        assertThat(vm.eventFlow.first()).isEqualTo(EditorEvent.Saved(firstAlert = true))
        val saved = alerts.all().single()
        assertThat(saved.wgId).isEqualTo(500760)
        assertThat(saved.trigger).isEqualTo(AlertTrigger.LIVE)
        assertThat(saved.directions).containsExactly(Direction.W, Direction.SW, Direction.NW)
        assertThat(saved.quietStartHour).isNull()
    }

    @Test
    fun spotWithoutStationForcesForecastAlerts() = runTest {
        val vm = vm()
        vm.setTrigger(AlertTrigger.LIVE)
        vm.selectSpot(lake)
        assertThat(vm.state.value.trigger).isEqualTo(AlertTrigger.FORECAST)
    }

    @Test
    fun editingKeepsHistoryAndDirections() = runTest {
        val id = alerts.save(
            surf.varun.core.model.WindAlert(wgId = 7, spotName = "Lake", minWind = 15, directions = setOf(Direction.E),
                lastFiredAt = java.time.Instant.EPOCH),
        )
        val vm = vm(AlertEditorRoute(alertId = id))
        assertThat(vm.state.value.isNew).isFalse()
        assertThat(vm.state.value.directions).containsExactly(Direction.E)
        vm.setHorizonDays(99)
        vm.save()
        vm.eventFlow.first()
        val saved = alerts.get(id)!!
        assertThat(saved.horizonDays).isEqualTo(AlertEditorViewModel.MAX_HORIZON_DAYS)
        assertThat(saved.lastFiredAt).isEqualTo(java.time.Instant.EPOCH)
        assertThat(alerts.all()).hasSize(1)
    }

    @Test
    fun searchFindsSpots() = runTest {
        val vm = vm()
        vm.setQuery("jast")
        assertThat(vm.results.first { it.isNotEmpty() }.map { it.wgId }).containsExactly(500760)
    }
}
