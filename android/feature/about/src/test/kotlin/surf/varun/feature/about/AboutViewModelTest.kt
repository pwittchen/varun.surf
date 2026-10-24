package surf.varun.feature.about

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
import surf.varun.core.model.Sponsor
import surf.varun.core.testing.FakeServiceRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsStatusAndMainSponsorsOnly() = runTest {
        val service = FakeServiceRepository(sponsorList = listOf(Sponsor(1, "Main", "https://a", true), Sponsor(2, "Spot only", null, false)))
        val state = AboutViewModel(service).uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.status!!.spotsCount).isEqualTo(827)
        assertThat(state.sponsors.map { it.name }).containsExactly("Main")
    }

    @Test
    fun sourcesAreWellFormed() {
        (DataSources.forecasts + DataSources.liveStations + DataSources.spots + Licences.all).forEach {
            assertThat(it.url).startsWith("http")
        }
    }
}
