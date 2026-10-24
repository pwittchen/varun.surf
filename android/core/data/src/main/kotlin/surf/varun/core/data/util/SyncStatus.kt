package surf.varun.core.data.util

import java.time.Instant

/**
 * How fresh a cached resource is. The UI shows "Updated X ago" from
 * [lastSuccessAt] and an error/offline banner from [lastError].
 */
data class SyncStatus(
    val refreshing: Boolean = false,
    val lastSuccessAt: Instant? = null,
    val lastError: Throwable? = null,
)
