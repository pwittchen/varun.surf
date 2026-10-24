package surf.varun.core.model

data class ServiceStatus(
    val status: String?,
    val version: String?,
    val uptime: String?,
    val startTime: String?,
    val spotsCount: Int?,
    val countriesCount: Int?,
    val liveStations: Int?,
)

data class ForecastProgress(
    val inProgress: Boolean,
    val total: Int,
    val completed: Int,
    val failed: Int,
    /** When the last sweep finished, null while the first one runs. */
    val finishedAt: java.time.Instant? = null,
)
