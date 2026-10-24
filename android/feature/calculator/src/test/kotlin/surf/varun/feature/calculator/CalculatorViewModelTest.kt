package surf.varun.feature.calculator

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
import surf.varun.core.domain.KiteCalculator
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.SkillLevel
import surf.varun.core.navigation.CalculatorRoute
import surf.varun.core.testing.FakeSettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun spotWindOverridesRememberedWindAndIsPersisted() = runTest {
        val settings = FakeSettingsRepository().apply { inputs.value = CalculatorInputs(10.0, 80.0, SkillLevel.INTERMEDIATE_FLAT) }
        val vm = CalculatorViewModel(CalculatorRoute(windKnots = 18.4), settings)
        val state = vm.uiState.value
        assertThat(state.windText).isEqualTo("18")
        assertThat(state.prefilledFromSpot).isTrue()
        assertThat(state.result).isEqualTo(KiteCalculator.Result(12, KiteCalculator.Board.TwinTip(140, 39)))
        assertThat(settings.inputs.value.windKnots).isEqualTo(18.0)
    }

    @Test
    fun warningInsteadOfResult() = runTest {
        val vm = CalculatorViewModel(CalculatorRoute(), FakeSettingsRepository())
        assertThat(vm.uiState.value.pristine).isTrue()
        vm.onWind("45")
        vm.onWeight("80")
        vm.onSkill(SkillLevel.ADVANCED_FLAT)
        assertThat(vm.uiState.value.warning).isEqualTo(KiteCalculator.Warning.EXTREME_WIND)
        assertThat(vm.uiState.value.result).isNull()
        vm.onWind("20,5")
        assertThat(vm.uiState.value.wind).isEqualTo(20.5)
        assertThat(vm.uiState.value.result).isNotNull()
    }
}
