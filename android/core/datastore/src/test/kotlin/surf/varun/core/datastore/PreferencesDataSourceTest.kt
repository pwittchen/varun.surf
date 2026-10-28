package surf.varun.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.SkillLevel
import surf.varun.core.model.TableDensity
import surf.varun.core.model.ThemeMode
import surf.varun.core.model.UserSettings
import surf.varun.core.model.WindUnit

class PreferencesDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.source() = PreferencesDataSource(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("test.preferences_pb") },
    )

    @Test
    fun defaultsUntilChanged() = runTest(UnconfinedTestDispatcher()) {
        val source = source()
        assertThat(source.settings.first()).isEqualTo(UserSettings())
        source.update { it.copy(theme = ThemeMode.DARK, windUnit = WindUnit.BEAUFORT) }
        val settings = source.settings.first()
        assertThat(settings.theme).isEqualTo(ThemeMode.DARK)
        assertThat(settings.windUnit).isEqualTo(WindUnit.BEAUFORT)
    }

    @Test
    fun tableDensityCompactUntilChanged() = runTest(UnconfinedTestDispatcher()) {
        val source = source()
        assertThat(source.settings.first().tableDensity).isEqualTo(TableDensity.COMPACT)
        source.update { it.copy(tableDensity = TableDensity.COMFORTABLE) }
        assertThat(source.settings.first().tableDensity).isEqualTo(TableDensity.COMFORTABLE)
    }

    @Test
    fun calculatorInputsRoundTrip() = runTest(UnconfinedTestDispatcher()) {
        val source = source()
        source.setCalculatorInputs(CalculatorInputs(18.0, 80.0, SkillLevel.ADVANCED_FLAT))
        assertThat(source.calculatorInputs.first()).isEqualTo(CalculatorInputs(18.0, 80.0, SkillLevel.ADVANCED_FLAT))
        source.setCalculatorInputs(CalculatorInputs(weightKg = 70.0))
        assertThat(source.calculatorInputs.first()).isEqualTo(CalculatorInputs(weightKg = 70.0))
    }
}
