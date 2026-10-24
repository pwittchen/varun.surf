package surf.varun.feature.spot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import surf.varun.core.data.repository.OnDemandState
import surf.varun.core.designsystem.component.InfoChip
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.ui.Format
import surf.varun.core.ui.localizedAnalysis

/** Section 3: the AI analysis, or the button that pays for one (spec 5.3.3, 6.5). */
@Composable
internal fun AiSection(state: SpotUiState, actions: SpotActions) {
    val spot = state.spot ?: return
    val context = LocalContext.current
    val analysis = spot.localizedAnalysis()
    SectionCard(stringResource(R.string.section_ai)) {
        if (analysis != null) {
            val (text, otherLanguage) = analysis
            if (otherLanguage) {
                InfoChip(stringResource(R.string.ai_other_language))
                Spacer(Modifier.height(8.dp))
            }
            Text(text.text, style = MaterialTheme.typography.bodyMedium)
            text.createdAt?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.ai_generated_at, Format.instant(context, it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ai_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PaidButton(
                state = state.analysisState,
                label = stringResource(R.string.ai_generate_button),
                running = stringResource(R.string.ai_generating),
                error = stringResource(R.string.ai_error),
                icon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) },
                onClick = actions.requestAnalysis,
            )
        }
    }
}

/** Section 4: reading the ICM meteogram, only for spots that have one. */
@Composable
internal fun IcmSection(state: SpotUiState, actions: SpotActions) {
    val spot = state.spot ?: return
    val icmReady = spot.availableModels.any { it.key == ForecastModelOption.ICM_KEY }
    SectionCard(stringResource(R.string.section_icm)) {
        if (icmReady) {
            // Already generated (by anyone, within 24 h): picking it costs nothing
            Text(stringResource(R.string.icm_available), style = MaterialTheme.typography.bodyMedium)
            if (state.model != ForecastModelOption.ICM_KEY) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { actions.selectModel(ForecastModelOption.ICM_KEY) }) {
                    Text(stringResource(R.string.icm_show))
                }
            }
        } else {
            PaidButton(
                state = state.icmState,
                label = stringResource(R.string.icm_generate_button),
                running = stringResource(R.string.icm_generating),
                error = stringResource(R.string.icm_error),
                icon = { Icon(Icons.Rounded.ShowChart, null, Modifier.size(18.dp)) },
                onClick = actions.requestIcm,
            )
        }
    }
}

/** A button for a paid operation: disabled while it runs, with its progress and failure under it. */
@Composable
private fun PaidButton(
    state: OnDemandState,
    label: String,
    running: String,
    error: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Column {
        Button(onClick = onClick, enabled = state != OnDemandState.Running) {
            icon()
            Spacer(Modifier.size(8.dp))
            Text(label)
        }
        when (state) {
            OnDemandState.Running -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(40.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(running, style = MaterialTheme.typography.bodySmall)
            }
            is OnDemandState.Failed -> Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.height(40.dp),
            )
            else -> Unit
        }
    }
}

/** The confirmation every paid operation goes through. */
@Composable
internal fun ConfirmDialog(pending: PendingConfirm?, actions: SpotActions) {
    pending ?: return
    val ai = pending == PendingConfirm.AI_ANALYSIS
    AlertDialog(
        onDismissRequest = actions.dismissConfirm,
        icon = { Icon(if (ai) Icons.Rounded.AutoAwesome else Icons.Rounded.ShowChart, null) },
        title = { Text(stringResource(if (ai) R.string.ai_modal_title else R.string.icm_modal_title)) },
        text = {
            Column {
                Text(stringResource(if (ai) R.string.ai_modal_question else R.string.icm_modal_question))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (ai) R.string.ai_modal_hint else R.string.icm_modal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = actions.confirm) { Text(stringResource(R.string.generate)) } },
        dismissButton = { TextButton(onClick = actions.dismissConfirm) { Text(stringResource(R.string.cancel)) } },
    )
}
