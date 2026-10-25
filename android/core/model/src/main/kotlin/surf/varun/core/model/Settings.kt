package surf.varun.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), POLISH("pl") }

enum class WindUnit { KNOTS, METERS_PER_SECOND, KILOMETERS_PER_HOUR, BEAUFORT }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class CardDensity { COMFORTABLE, COMPACT }

enum class StartTab { AUTO, SPOTS, FAVOURITES, MAP }

/** How far the map timeline reaches: five days like the web on a phone, or the whole run. */
enum class TimelineReach(val hours: Int) { FIVE_DAYS(120), FULL_RUN(384) }

enum class ParticleQuality { AUTO, LOW, HIGH, OFF }

data class UserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val windUnit: WindUnit = WindUnit.KNOTS,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val density: CardDensity = CardDensity.COMFORTABLE,
    val startTab: StartTab = StartTab.AUTO,
    val timelineReach: TimelineReach = TimelineReach.FIVE_DAYS,
    val particleQuality: ParticleQuality = ParticleQuality.AUTO,
    val useLocation: Boolean = false,
    val rememberModelGlobally: Boolean = false,
    val filterWindyDays: Boolean = false,
    val mapWindField: Boolean = true,
    val mapMarkers: Boolean = true,
    val mapSatellite: Boolean = true,
    /** The map tab shows the spots on a rotating globe instead of the flat map. */
    val mapGlobe: Boolean = false,
    /** The globe's idle spin. */
    val mapGlobeSpin: Boolean = true,
)
