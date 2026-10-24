package surf.varun.core.model

/**
 * A kite spot as the app sees it. Built from the backend's `Spot` JSON; fields the
 * list endpoint strips (hourly forecast, live history) are empty there.
 */
data class Spot(
    val wgId: Int,
    val name: String,
    val country: String,
    val links: SpotLinks = SpotLinks(),
    val coordinates: Coordinates? = null,
    val photoUrl: String? = null,
    val live: LiveConditions? = null,
    val liveHistory: List<LiveConditions> = emptyList(),
    val daily: List<ForecastEntry> = emptyList(),
    val hourly: List<ForecastEntry> = emptyList(),
    val aiAnalysisEn: AiAnalysis? = null,
    val aiAnalysisPl: AiAnalysis? = null,
    val info: SpotInfo? = null,
    val infoPl: SpotInfo? = null,
    val sponsors: List<Sponsor> = emptyList(),
    val availableModels: List<ForecastModelOption> = emptyList(),
    val lastUpdated: String? = null,
) {
    val hasLiveStation: Boolean get() = live != null
    val hasWaveData: Boolean get() = daily.any { it.wave != null } || hourly.any { it.wave != null }
}

/** External links; null means the spot has none (the server sends "" or null). */
data class SpotLinks(
    val windguru: String? = null,
    val windfinder: String? = null,
    val icm: String? = null,
    val webcam: String? = null,
    val location: String? = null,
)
