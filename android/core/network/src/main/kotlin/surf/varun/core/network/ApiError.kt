package surf.varun.core.network

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** Why a backend call failed, in the terms the UI needs to react to. */
sealed class ApiError(message: String?, cause: Throwable?) : Exception(message, cause) {
    /** No connection, DNS failure, timeout: try again when online. */
    class Offline(cause: Throwable) : ApiError(cause.message, cause)

    /** 404: the spot (or endpoint) doesn't exist (any more). */
    class NotFound(cause: Throwable) : ApiError(cause.message, cause)

    /** 503 from the paid endpoints: feature off, or nothing could be generated. */
    class Unavailable(cause: Throwable) : ApiError(cause.message, cause)

    /** 401 that survived a session renewal. */
    class Unauthorized(cause: Throwable) : ApiError(cause.message, cause)

    /** Any other HTTP error. */
    class Server(val code: Int, cause: Throwable) : ApiError(cause.message, cause)

    /** The payload didn't parse. */
    class BadData(cause: Throwable) : ApiError(cause.message, cause)

    companion object {
        fun from(error: Throwable): Throwable = when (error) {
            is ApiError -> error
            is HttpException -> when (error.code()) {
                404 -> NotFound(error)
                401 -> Unauthorized(error)
                503 -> Unavailable(error)
                else -> Server(error.code(), error)
            }
            is SerializationException, is IllegalArgumentException -> BadData(error)
            is IOException -> Offline(error)
            else -> error
        }
    }
}

/** Run [block], rethrowing failures as [ApiError] (cancellation passes through untouched). */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): T = try {
    block()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    throw ApiError.from(e)
}
