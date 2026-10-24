package surf.varun.feature.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import surf.varun.core.designsystem.component.WindNumber
import surf.varun.core.designsystem.theme.Numbers
import surf.varun.core.domain.KiteCalculator
import surf.varun.core.model.SkillLevel
import surf.varun.core.navigation.LocalNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(viewModel: CalculatorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calculator_title)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.loaded) return@Column
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.windText,
                    onValueChange = viewModel::onWind,
                    label = { Text(stringResource(R.string.wind_speed_label)) },
                    supportingText = if (state.prefilledFromSpot) ({ Text(stringResource(R.string.prefilled_from_spot)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                state.wind?.let { WindNumber(it.toInt().toString(), it, style = Numbers.large, modifier = Modifier.padding(start = 16.dp)) }
            }
            Slider(
                value = (state.wind ?: 15.0).toFloat().coerceIn(5f, 50f),
                onValueChange = viewModel::onWindSlider,
                valueRange = 5f..50f,
                steps = 44,
            )
            OutlinedTextField(
                value = state.weightText,
                onValueChange = viewModel::onWeight,
                label = { Text(stringResource(R.string.rider_weight_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SkillDropdown(state.skill, viewModel::onSkill)

            val warning = state.warning
            val result = state.result
            when {
                state.pristine -> Unit
                warning != null -> WarningCard(warning)
                result != null -> ResultCard(result)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDropdown(selected: SkillLevel?, onSelect: (SkillLevel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { stringResource(it.label()) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.skill_level_label)) },
            placeholder = { Text(stringResource(R.string.skill_level_placeholder)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SkillLevel.entries.forEach { skill ->
                DropdownMenuItem(
                    text = { Text(stringResource(skill.label())) },
                    onClick = {
                        onSelect(skill)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: KiteCalculator.Result) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.recommended_equipment), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.kite_size_label), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.kite_size_value, result.kiteSizeM2), style = Numbers.large)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.board_size_label), style = MaterialTheme.typography.labelMedium)
                    Text(
                        when (val board = result.board) {
                            is KiteCalculator.Board.TwinTip -> stringResource(R.string.board_twin_tip, board.lengthCm, board.widthCm)
                            is KiteCalculator.Board.Directional -> stringResource(R.string.board_directional, board.length)
                        },
                        style = Numbers.medium,
                    )
                }
            }
            Text(stringResource(R.string.calc_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WarningCard(warning: KiteCalculator.Warning) {
    val (title, message) = warning.text()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column {
                Text(stringResource(title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(message), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

private fun KiteCalculator.Warning.text(): Pair<Int, Int> = when (this) {
    KiteCalculator.Warning.INVALID_WIND_SPEED -> R.string.warning_invalid_wind_title to R.string.warning_invalid_wind
    KiteCalculator.Warning.INVALID_WEIGHT -> R.string.warning_invalid_weight_title to R.string.warning_invalid_weight
    KiteCalculator.Warning.WEIGHT_TOO_LOW -> R.string.warning_weight_low_title to R.string.warning_weight_low
    KiteCalculator.Warning.SKILL_LEVEL_REQUIRED -> R.string.warning_skill_title to R.string.warning_skill
    KiteCalculator.Warning.WEIGHT_TOO_HIGH -> R.string.warning_weight_high_title to R.string.warning_weight_high
    KiteCalculator.Warning.EXTREME_WIND -> R.string.warning_extreme_title to R.string.warning_extreme
    KiteCalculator.Warning.LOW_WIND -> R.string.warning_low_wind_title to R.string.warning_low_wind
    KiteCalculator.Warning.INSUFFICIENT_WIND_WAVES -> R.string.warning_waves_title to R.string.warning_waves
}

private fun SkillLevel.label() = when (this) {
    SkillLevel.BEGINNER_FLAT -> R.string.skill_beginner_flat
    SkillLevel.BEGINNER_SMALL -> R.string.skill_beginner_small
    SkillLevel.INTERMEDIATE_FLAT -> R.string.skill_intermediate_flat
    SkillLevel.INTERMEDIATE_MEDIUM -> R.string.skill_intermediate_medium
    SkillLevel.ADVANCED_FLAT -> R.string.skill_advanced_flat
    SkillLevel.ADVANCED_MEDIUM -> R.string.skill_advanced_medium
    SkillLevel.ADVANCED_LARGE -> R.string.skill_advanced_large
}
