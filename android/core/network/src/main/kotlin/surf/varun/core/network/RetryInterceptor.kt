package surf.varun.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Retries idempotent GETs on 5xx and network failures with exponential backoff
 * (spec 6.2). Never touches POSTs - those are the paid operations. A refused
 * request (4xx) is final.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 400,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        var attempt = 0
        var delay = initialDelayMs
        while (true) {
            val response: Response? = try {
                chain.proceed(request)
            } catch (e: IOException) {
                // A cancelled call must not be retried
                if (e is InterruptedIOException && chain.call().isCanceled()) throw e
                if (attempt >= maxRetries) throw e
                null
            }
            if (response != null) {
                if (response.code < 500 || attempt >= maxRetries) return response
                response.close()
            }
            attempt++
            sleeper(delay)
            delay *= 2
        }
    }
}
