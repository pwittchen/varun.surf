package surf.varun.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the startup path and the spots list scroll into a baseline profile
 * (spec 6.3: first frame from cache in under a second).
 *
 * `./gradlew :app:generateBaselineProfile` with a device or emulator attached.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        scrollSpots()
        openFirstSpot()
    }
}

internal const val PACKAGE = "surf.varun.app"
