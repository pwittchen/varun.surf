package surf.varun.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import surf.varun.core.datastore.PreferencesDataSource
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.model.UserSettings
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun update(transform: (UserSettings) -> UserSettings)

    val calculatorInputs: Flow<CalculatorInputs>

    suspend fun setCalculatorInputs(inputs: CalculatorInputs)

    val language: Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)

    /**
     * The model to open [wgId] with: what was picked for it this app session (the
     * web keeps it in sessionStorage), else the globally remembered one when that
     * setting is on, else GFS.
     */
    suspend fun modelFor(wgId: Int): String

    suspend fun rememberModel(wgId: Int, model: String)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val preferences: PreferencesDataSource,
) : SettingsRepository {
    private val sessionModels = ConcurrentHashMap<Int, String>()

    override val settings: Flow<UserSettings> = preferences.settings

    override suspend fun update(transform: (UserSettings) -> UserSettings) = preferences.update(transform)

    override val calculatorInputs: Flow<CalculatorInputs> = preferences.calculatorInputs

    override suspend fun setCalculatorInputs(inputs: CalculatorInputs) = preferences.setCalculatorInputs(inputs)

    override val language: Flow<AppLanguage> = preferences.language

    override suspend fun setLanguage(language: AppLanguage) = preferences.setLanguage(language)

    override suspend fun modelFor(wgId: Int): String {
        sessionModels[wgId]?.let { return it }
        if (preferences.settings.first().rememberModelGlobally) {
            preferences.globalModel.first()?.let { return it }
        }
        return ForecastModelOption.DEFAULT_KEY
    }

    override suspend fun rememberModel(wgId: Int, model: String) {
        sessionModels[wgId] = model
        if (preferences.settings.first().rememberModelGlobally && model != ForecastModelOption.ICM_KEY) {
            preferences.setGlobalModel(model)
        }
    }
}
