package surf.varun.feature.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import surf.varun.core.model.Spot
import surf.varun.core.testing.FakeRecentsRepository
import surf.varun.core.testing.FakeSpotsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun findsSpotsWithFoldedDiacriticsAndRemembersTheQuery() = runTest {
        val recents = FakeRecentsRepository()
        val vm = SearchViewModel(FakeSpotsRepository(listOf(Spot(1, "Łeba", "Poland"), Spot(2, "Tarifa", "Spain"))), recents)
        vm.uiState.test {
            vm.onQueryChange("leba")
            var state = awaitItem()
            while (state.results.isEmpty()) state = awaitItem()
            assertThat(state.results.map { it.wgId }).containsExactly(1)
            vm.onResultOpened()
            while (state.recentSearches.isEmpty()) state = awaitItem()
            assertThat(state.recentSearches).containsExactly("leba")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
