package surf.varun.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold start with and without the baseline profile, and list scrolling jank. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun coldStartWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun scrollSpotsList() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = { startActivityAndWait() },
    ) {
        scrollSpots()
    }

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
