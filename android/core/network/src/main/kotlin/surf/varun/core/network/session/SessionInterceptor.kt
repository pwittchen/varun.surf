package surf.varun.core.network.session

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Makes sure every API request carries a valid SESSION cookie: renews it ahead of
 * expiry, and on a 401 renews once and retries once. A second 401 is returned to
 * the caller as an error.
 *
 * Must run as an application interceptor, before the cookie jar adds the cookie
 * (the bridge interceptor reads the jar after this one proceeds).
 */
class SessionInterceptor(private val session: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!requiresSession(request.url.encodedPath)) return chain.proceed(request)

        if (session.needsRenewal()) session.renew(session.currentToken())

        val tokenUsed = session.currentToken()
        val response = chain.proceed(request)
        if (response.code != 401) return response

        response.close()
        session.renew(tokenUsed)
        return chain.proceed(request)
    }

    private fun requiresSession(path: String): Boolean =
        path.startsWith("/api/v1/") && path != "/api/v1/session" && path != "/api/v1/health"
}
