package surf.varun.feature.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.util.IoDispatcher
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.UserSettings
import javax.inject.Inject

data class SettingsUiState(val settings: UserSettings = UserSettings(), val language: AppLanguage = AppLanguage.SYSTEM)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val spotsRepository: SpotsRepository,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, settingsRepository.language, ::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /**
     * Per-app language (spec 5.8, 6.9): AppCompat stores it and, on Android 13+, hands
     * it to the platform so the system picker shows the same choice.
     */
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
        AppCompatDelegate.setApplicationLocales(
            language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList(),
        )
    }

    /** Cached forecasts, the wind grid, images, HTTP and map tile caches; never favourites or alerts. */
    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            spotsRepository.clearCachedDetails()
            withContext(io) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            onDone()
        }
    }
}
