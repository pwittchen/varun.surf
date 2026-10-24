package surf.varun.core.model

import java.time.LocalDateTime

/**
 * One live station reading. [observedAt] is the station's local wall-clock time
 * (the backend sends it without a zone), null when the station's format is unknown.
 */
data class LiveConditions(
    val rawDate: String,
    val observedAt: LocalDateTime?,
    val wind: Int,
    val gusts: Int,
    val direction: Direction?,
    val temp: Int?,
)
