package surf.varun.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.model.WindAlert
import surf.varun.core.model.WindUnit
import javax.inject.Inject

data class AlertsListState(
    val loading: Boolean = true,
    val alerts: List<WindAlert> = emptyList(),
    val unit: WindUnit = WindUnit.KNOTS,
)

@HiltViewModel
class AlertsListViewModel @Inject constructor(
    private val alerts: AlertsRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<AlertsListState> = combine(alerts.alerts, settings.settings) { list, s ->
        AlertsListState(loading = false, alerts = list, unit = s.windUnit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsListState())

    fun setEnabled(alert: WindAlert, enabled: Boolean) {
        viewModelScope.launch { alerts.save(alert.copy(enabled = enabled)) }
    }

    fun delete(alert: WindAlert) {
        viewModelScope.launch { alerts.delete(alert.id) }
    }

    /** Undo puts the alert back under its old id. */
    fun restore(alert: WindAlert) {
        viewModelScope.launch { alerts.save(alert) }
    }
}
