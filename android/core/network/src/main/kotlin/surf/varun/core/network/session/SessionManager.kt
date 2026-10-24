package surf.varun.core.network.session

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Owns the backend's `SESSION` cookie (spec 3.1).
 *
 * The cookie is a stateless signed token, `<expiry epoch second>.<signature>`,
 * handed out by `GET /api/v1/session`. The app never verifies the signature; it
 * only reads the expiry so it can renew the token before it runs out, and renews
 * it on a 401. Renewal is single-flight: whichever thread gets there first
 * fetches, the rest reuse its result.
 */
class SessionManager(
    private val baseUrl: HttpUrl,
    private val store: SessionStore,
    private val clock: Clock = Clock.systemUTC(),
) : CookieJar {
    private val lock = Any()

    @Volatile
    private var token: String? = null

    @Volatile
    private var initialized = false

    /** Set once the HTTP client exists; the session call goes through the bare client. */
    lateinit var sessionClient: OkHttpClient

    fun currentToken(): String? {
        if (!initialized) {
            synchronized(lock) {
                if (!initialized) {
                    token = store.load()
                    initialized = true
                }
            }
        }
        return token
    }

    /** Expiry encoded in the token, or null when it doesn't look like one. */
    fun expiryOf(value: String?): Instant? =
        value?.substringBefore('.', missingDelimiterValue = "")?.toLongOrNull()?.let(Instant::ofEpochSecond)

    /** True when there is no token or it expires within [margin]. */
    fun needsRenewal(margin: Duration = RENEW_BEFORE_EXPIRY): Boolean {
        val expiry = expiryOf(currentToken()) ?: return currentToken() == null
        return Duration.between(clock.instant(), expiry) < margin
    }

    /**
     * Fetch a fresh token unless another thread already replaced [staleToken] while
     * this one waited. Returns the token to use from now on (null when the backend
     * could not be reached).
     */
    fun renew(staleToken: String?): String? = synchronized(lock) {
        val current = currentToken()
        if (current != null && current != staleToken) return current
        val request = Request.Builder().url(baseUrl.resolve("api/v1/session")!!).get().build()
        try {
            sessionClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Session request failed with HTTP %d", response.code)
                    return current
                }
            }
        } catch (e: java.io.IOException) {
            Timber.w(e, "Session request failed")
            return current
        }
        // saveFromResponse stored the new cookie while the call ran
        return token
    }

    fun clear() = synchronized(lock) {
        token = null
        initialized = true
        store.save(null)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val session = cookies.lastOrNull { it.name == COOKIE_NAME } ?: return
        synchronized(lock) {
            val expired = session.expiresAt < clock.millis()
            token = if (expired) null else session.value
            initialized = true
            store.save(token)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (url.host != baseUrl.host) return emptyList()
        val value = currentToken() ?: return emptyList()
        val cookie = Cookie.Builder()
            .name(COOKIE_NAME)
            .value(value)
            .hostOnlyDomain(baseUrl.host)
            .path("/")
            .build()
        return listOf(cookie)
    }

    companion object {
        const val COOKIE_NAME = "SESSION"

        /** Renew proactively once fewer than 12 of the token's 24 hours are left. */
        val RENEW_BEFORE_EXPIRY: Duration = Duration.ofHours(12)
    }
}
