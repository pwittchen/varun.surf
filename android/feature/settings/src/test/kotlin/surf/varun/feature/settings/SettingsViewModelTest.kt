package surf.varun.feature.settings

import androidx.test.core.app.ApplicationProvider
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.WindUnit
import surf.varun.core.testing.FakeServiceRepository
import surf.varun.core.testing.FakeSettingsRepository
import surf.varun.core.testing.FakeSpotsRepository
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val settings = FakeSettingsRepository()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val vm = SettingsViewModel(settings, FakeSpotsRepository(), FakeServiceRepository(), context, dispatcher)

    @Test
    fun updatesAreWrittenThrough() = runTest {
        vm.update { it.copy(windUnit = WindUnit.METERS_PER_SECOND) }
        assertThat(settings.state.value.windUnit).isEqualTo(WindUnit.METERS_PER_SECOND)
    }

    @Test
    fun languageIsMirroredForWidgetsAndNotifications() = runTest {
        vm.setLanguage(AppLanguage.POLISH)
        assertThat(settings.languageState.value).isEqualTo(AppLanguage.POLISH)
    }

    @Test
    fun clearCacheEmptiesTheCacheDirectory() = runTest {
        File(context.cacheDir, "wind-timeline-120.json.gz").writeText("x")
        var done = false
        vm.clearCache { done = true }
        assertThat(done).isTrue()
        assertThat(context.cacheDir.listFiles().orEmpty().toList()).isEmpty()
    }
}
