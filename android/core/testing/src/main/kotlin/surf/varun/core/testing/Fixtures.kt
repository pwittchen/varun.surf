package surf.varun.core.testing

/**
 * Real payloads captured from production (2026-10-24) - see `resources/fixtures`.
 * `spots.json` is trimmed to 60 spots (10 with a live station) to keep the repo small;
 * `wind.json` to the same spots.
 */
object Fixtures {
    const val SPOT_ID = 500760 // Jastarnia: live station, waves, ICM, six models

    fun read(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

    val spots: String get() = read("spots.json")
    val spot: String get() = read("spots-500760.json")
    val forecast: String get() = read("forecast-500760.json")
    val wind: String get() = read("wind.json")
    val status: String get() = read("status.json")
    val statusForecast: String get() = read("status-forecast.json")
    val sponsors: String get() = read("sponsors.json")
}
