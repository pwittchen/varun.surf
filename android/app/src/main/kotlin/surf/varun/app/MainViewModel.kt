package surf.varun.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.model.StartTab
import surf.varun.core.model.UserSettings
import surf.varun.core.navigation.TopLevel
import java.time.Duration
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val settings: UserSettings) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    favouritesRepository: FavouritesRepository,
    private val spotsRepository: SpotsRepository,
) : ViewModel() {
    private val startTab = combine(settingsRepository.settings, favouritesRepository.favouriteIds) { settings, favourites ->
        when (settings.startTab) {
            StartTab.SPOTS -> TopLevel.SPOTS
            StartTab.FAVOURITES -> TopLevel.FAVOURITES
            StartTab.MAP -> TopLevel.MAP
            // Favourites become the start destination once the user has one (spec 5.1)
            StartTab.AUTO -> if (favourites.isNotEmpty()) TopLevel.FAVOURITES else TopLevel.SPOTS
        }
    }

    val uiState: StateFlow<MainUiState> = settingsRepository.settings
        .map<UserSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    private var resolvedStartTab: TopLevel? = null

    /** Decided once per launch, so adding the first favourite doesn't move the user. */
    suspend fun startTab(): TopLevel = resolvedStartTab ?: startTab.first().also { resolvedStartTab = it }

    /** Refresh the list on foreground when the cached copy is older than a minute (spec 6.1). */
    fun onForeground() {
        viewModelScope.launch { spotsRepository.refreshSpots(maxAge = Duration.ofMinutes(1)) }
    }
}
