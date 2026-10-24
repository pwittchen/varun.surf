package surf.varun.core.model

import java.time.Instant

enum class AlertTrigger { LIVE, FORECAST }

/**
 * "Notify me when [wgId] reaches [minWind] kts (up to [maxWind]) from [directions]".
 * Thresholds are always knots, whatever unit the user reads them in.
 */
data class WindAlert(
    val id: Long = 0,
    val wgId: Int,
    val spotName: String,
    val minWind: Int,
    val maxWind: Int? = null,
    val directions: Set<Direction> = emptySet(),
    val trigger: AlertTrigger = AlertTrigger.FORECAST,
    val horizonDays: Int = 3,
    val minConsecutiveHours: Int = 2,
    val quietStartHour: Int? = 22,
    val quietEndHour: Int? = 7,
    val lastFiredAt: Instant? = null,
    val mutedUntil: Instant? = null,
    val enabled: Boolean = true,
)
