package surf.varun.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import surf.varun.core.model.AppLanguage
import surf.varun.core.model.CalculatorInputs
import surf.varun.core.model.SkillLevel
import surf.varun.core.model.UserSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings and small per-user state in a Preferences DataStore. The app language
 * is not here: Android keeps it per app (`AppCompatDelegate` / `LocaleManager`).
 */
@Singleton
class PreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val windUnit = stringPreferencesKey("wind_unit")
        val temperatureUnit = stringPreferencesKey("temperature_unit")
        val density = stringPreferencesKey("density")
        val tableDensity = stringPreferencesKey("table_density")
        val startTab = stringPreferencesKey("start_tab")
        val timelineReach = stringPreferencesKey("timeline_reach")
        val particleQuality = stringPreferencesKey("particle_quality")
        val useLocation = booleanPreferencesKey("use_location")
        val rememberModelGlobally = booleanPreferencesKey("remember_model_globally")
        val globalModel = stringPreferencesKey("global_model")
        val filterWindyDays = booleanPreferencesKey("filter_windy_days")
        val fadeOffDirection = booleanPreferencesKey("fade_off_direction")
        val showKiteSize = booleanPreferencesKey("show_kite_size")
        val mapWindField = booleanPreferencesKey("map_wind_field")
        val mapMarkers = booleanPreferencesKey("map_markers")
        val mapSatellite = booleanPreferencesKey("map_satellite")
        val mapGlobe = booleanPreferencesKey("map_globe")
        val mapGlobeSpin = booleanPreferencesKey("map_globe_spin")
        val mapTimeline = booleanPreferencesKey("map_timeline")
        val calcWind = doublePreferencesKey("calc_wind")
        val calcWeight = doublePreferencesKey("calc_weight")
        val calcSkill = stringPreferencesKey("calc_skill")
        val language = stringPreferencesKey("language")
    }

    val settings: Flow<UserSettings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    val calculatorInputs: Flow<CalculatorInputs> = dataStore.data.map { prefs ->
        CalculatorInputs(
            windKnots = prefs[Keys.calcWind],
            weightKg = prefs[Keys.calcWeight],
            skill = SkillLevel.fromKey(prefs[Keys.calcSkill]),
        )
    }.distinctUntilChanged()

    suspend fun setCalculatorInputs(inputs: CalculatorInputs) {
        dataStore.edit { prefs ->
            prefs.putOrRemove(Keys.calcWind, inputs.windKnots)
            prefs.putOrRemove(Keys.calcWeight, inputs.weightKg)
            prefs.putOrRemove(Keys.calcSkill, inputs.skill?.key)
        }
    }

    /** Model remembered across launches, only used when [UserSettings.rememberModelGlobally]. */
    val globalModel: Flow<String?> = dataStore.data.map { it[Keys.globalModel] }.distinctUntilChanged()

    suspend fun setGlobalModel(model: String?) {
        dataStore.edit { it.putOrRemove(Keys.globalModel, model) }
    }

    /**
     * The in-app language choice, mirrored here so a widget or notification built
     * without an Activity still knows it (the platform keeps the authoritative copy).
     */
    val language: Flow<AppLanguage> = dataStore.data.map { enumOr(it[Keys.language], AppLanguage.SYSTEM) }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.language] = language.name }
    }

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            theme = enumOr(this[Keys.theme], defaults.theme),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            windUnit = enumOr(this[Keys.windUnit], defaults.windUnit),
            temperatureUnit = enumOr(this[Keys.temperatureUnit], defaults.temperatureUnit),
            density = enumOr(this[Keys.density], defaults.density),
            tableDensity = enumOr(this[Keys.tableDensity], defaults.tableDensity),
            startTab = enumOr(this[Keys.startTab], defaults.startTab),
            timelineReach = enumOr(this[Keys.timelineReach], defaults.timelineReach),
            particleQuality = enumOr(this[Keys.particleQuality], defaults.particleQuality),
            useLocation = this[Keys.useLocation] ?: defaults.useLocation,
            rememberModelGlobally = this[Keys.rememberModelGlobally] ?: defaults.rememberModelGlobally,
            filterWindyDays = this[Keys.filterWindyDays] ?: defaults.filterWindyDays,
            fadeOffDirection = this[Keys.fadeOffDirection] ?: defaults.fadeOffDirection,
            showKiteSize = this[Keys.showKiteSize] ?: defaults.showKiteSize,
            mapWindField = this[Keys.mapWindField] ?: defaults.mapWindField,
            mapMarkers = this[Keys.mapMarkers] ?: defaults.mapMarkers,
            mapSatellite = this[Keys.mapSatellite] ?: defaults.mapSatellite,
            mapGlobe = this[Keys.mapGlobe] ?: defaults.mapGlobe,
            mapGlobeSpin = this[Keys.mapGlobeSpin] ?: defaults.mapGlobeSpin,
            mapTimeline = this[Keys.mapTimeline] ?: defaults.mapTimeline,
        )
    }

    private fun MutablePreferences.write(s: UserSettings) {
        this[Keys.theme] = s.theme.name
        this[Keys.dynamicColor] = s.dynamicColor
        this[Keys.windUnit] = s.windUnit.name
        this[Keys.temperatureUnit] = s.temperatureUnit.name
        this[Keys.density] = s.density.name
        this[Keys.tableDensity] = s.tableDensity.name
        this[Keys.startTab] = s.startTab.name
        this[Keys.timelineReach] = s.timelineReach.name
        this[Keys.particleQuality] = s.particleQuality.name
        this[Keys.useLocation] = s.useLocation
        this[Keys.rememberModelGlobally] = s.rememberModelGlobally
        this[Keys.filterWindyDays] = s.filterWindyDays
        this[Keys.fadeOffDirection] = s.fadeOffDirection
        this[Keys.showKiteSize] = s.showKiteSize
        this[Keys.mapWindField] = s.mapWindField
        this[Keys.mapMarkers] = s.mapMarkers
        this[Keys.mapSatellite] = s.mapSatellite
        this[Keys.mapGlobe] = s.mapGlobe
        this[Keys.mapGlobeSpin] = s.mapGlobeSpin
        this[Keys.mapTimeline] = s.mapTimeline
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}

