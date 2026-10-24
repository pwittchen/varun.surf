package surf.varun.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.ServiceRepository
import surf.varun.core.model.ForecastProgress
import surf.varun.core.model.ServiceStatus
import surf.varun.core.model.Sponsor
import javax.inject.Inject

data class AboutUiState(
    val loading: Boolean = true,
    val status: ServiceStatus? = null,
    val statusError: Boolean = false,
    val progress: ForecastProgress? = null,
    val sponsors: ImmutableList<Sponsor> = persistentListOf(),
)

@HiltViewModel
class AboutViewModel @Inject constructor(private val service: ServiceRepository) : ViewModel() {
    private val _state = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val status = async { service.status() }
            val progress = async { service.forecastProgress() }
            val sponsors = async { service.sponsors() }
            val s = status.await()
            _state.value = AboutUiState(
                loading = false,
                status = s.getOrNull(),
                statusError = s.isFailure,
                progress = progress.await().getOrNull(),
                sponsors = sponsors.await().getOrNull().orEmpty().filter { it.main }.toImmutableList(),
            )
        }
    }
}
