package surf.varun.feature.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.domain.KiteCalculator
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.SkillLevel
import surf.varun.core.navigation.CalculatorRoute
import kotlin.math.roundToInt

/** Text the user typed, kept as typed so a half-entered number isn't rewritten under them. */
data class CalculatorUiState(
    val windText: String = "",
    val weightText: String = "",
    val skill: SkillLevel? = null,
    val prefilledFromSpot: Boolean = false,
    val loaded: Boolean = false,
) {
    val wind: Double? get() = windText.replace(',', '.').toDoubleOrNull()
    val weight: Double? get() = weightText.replace(',', '.').toDoubleOrNull()

    /** The web shows either a warning or the result, never both. */
    val warning: KiteCalculator.Warning? get() = KiteCalculator.validate(wind, weight, skill)
    val result: KiteCalculator.Result? get() = KiteCalculator.calculate(wind, weight, skill)

    /** Nothing entered yet: no warning shouted at an empty form. */
    val pristine: Boolean get() = windText.isBlank() && weightText.isBlank() && skill == null
}

@HiltViewModel(assistedFactory = CalculatorViewModel.Factory::class)
class CalculatorViewModel @AssistedInject constructor(
    @Assisted private val route: CalculatorRoute,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: CalculatorRoute): CalculatorViewModel
    }

    private val _state = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.calculatorInputs.first()
            // On a spot the spot's wind always wins over the remembered one (calculator.js#restoreInputs)
            val wind = route.windKnots?.roundToInt()?.toDouble() ?: saved.windKnots
            _state.value = CalculatorUiState(
                windText = wind?.let(::format).orEmpty(),
                weightText = saved.weightKg?.let(::format).orEmpty(),
                skill = saved.skill,
                prefilledFromSpot = route.windKnots != null,
                loaded = true,
            )
            persist()
        }
    }

    fun onWind(text: String) = edit { it.copy(windText = text.filterNumeric(), prefilledFromSpot = false) }

    fun onWindSlider(value: Float) = edit { it.copy(windText = value.roundToInt().toString(), prefilledFromSpot = false) }

    fun onWeight(text: String) = edit { it.copy(weightText = text.filterNumeric()) }

    fun onSkill(skill: SkillLevel) = edit { it.copy(skill = skill) }

    private fun edit(transform: (CalculatorUiState) -> CalculatorUiState) {
        _state.update(transform)
        persist()
    }

    /** Inputs are remembered as they change, like the web does on every `change`. */
    private fun persist() {
        val s = _state.value
        viewModelScope.launch { settingsRepository.setCalculatorInputs(CalculatorInputs(s.wind, s.weight, s.skill)) }
    }

    private fun String.filterNumeric() = filter { it.isDigit() || it == '.' || it == ',' }.take(6)

    private fun format(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
