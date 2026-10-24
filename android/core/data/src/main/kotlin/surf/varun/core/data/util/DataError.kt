package surf.varun.core.data.util

import surf.varun.core.network.ApiError

/**
 * Failures as features see them; network types stay inside core:data.
 */
sealed class DataError(cause: Throwable?) : Exception(cause?.message, cause) {
    /** No connection: show the offline banner, keep the cache on screen. */
    class Offline(cause: Throwable?) : DataError(cause)

    /** The spot is gone from the backend (removed from spots.json). */
    class NotFound(cause: Throwable?) : DataError(cause)

    /** A paid feature is off or produced nothing (503). */
    class Unavailable(cause: Throwable?) : DataError(cause)

    class Other(cause: Throwable?) : DataError(cause)

    companion object {
        fun from(error: Throwable): DataError = when (error) {
            is DataError -> error
            is ApiError.Offline -> Offline(error)
            is ApiError.NotFound -> NotFound(error)
            is ApiError.Unavailable -> Unavailable(error)
            else -> Other(error)
        }
    }
}

/** runCatching that lets cancellation through and maps failures to [DataError]. */
internal suspend inline fun <T> catching(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(DataError.from(e))
}
