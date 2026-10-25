package surf.varun.feature.spots

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import surf.varun.core.model.Coordinates
import surf.varun.core.model.Spot
import surf.varun.core.navigation.SpotsRoute
import surf.varun.core.testing.FakeConnectivityMonitor
import surf.varun.core.testing.FakeFavouritesRepository
import surf.varun.core.testing.FakeLocationRepository
import surf.varun.core.testing.FakeServiceRepository
import surf.varun.core.testing.FakeSettingsRepository
import surf.varun.core.testing.FakeSpotsRepository
import java.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class SpotsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val spots = FakeSpotsRepository(
        listOf(
            Spot(1, "Jastarnia", "Poland", coordinates = Coordinates(54.69, 18.66)),
            Spot(2, "Brno dam", "Czech Republic"),
        ),
    )
    private val favourites = FakeFavouritesRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(route: SpotsRoute = SpotsRoute()) = SpotsViewModel(
        route, spots, favourites, FakeSettingsRepository(), FakeServiceRepository(),
        FakeLocationRepository(permission = true, location = Coordinates(54.35, 18.65)),
        FakeConnectivityMonitor(), Clock.systemUTC(), dispatcher,
    )

    @Test
    fun countryFromTheWebSlugIsApplied() = runTest(dispatcher) {
        val vm = viewModel(SpotsRoute(country = "czech-republic"))
        vm.uiState.test {
            var state = awaitItem()
            while (state.loading || state.filters.country == null) state = awaitItem()
            assertThat(state.filters.country).isEqualTo("Czech Republic")
            assertThat(state.items.map { it.spot.wgId }).containsExactly(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun togglingFavouriteUpdatesTheCards() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            vm.toggleFavourite(1)
            runCurrent()
            state = expectMostRecentItem()
            assertThat(state.items.first { it.spot.wgId == 1 }.favourite).isTrue()
            assertThat(favourites.current).containsExactly(1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
