package surf.varun.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import surf.varun.core.network.session.InMemorySessionStore
import surf.varun.core.network.session.SessionManager
import surf.varun.core.testing.Fixtures
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class HttpStackTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sessionCalls = AtomicInteger()
    private val spotsCalls = AtomicInteger()

    /** Token the fake backend currently accepts; anything else is a 401. */
    @Volatile
    private var validToken = token(hoursLeft = 24)

    /** When set, even a freshly issued cookie is refused. */
    @Volatile
    private var rejectAll = false

    private fun token(hoursLeft: Long) = "${now.plusSeconds(hoursLeft * 3600).epochSecond}.c2ln"

    private lateinit var store: InMemorySessionStore
    private lateinit var source: VarunNetworkDataSource
    private lateinit var session: SessionManager

    private var spotsResponder: (Int) -> MockResponse = { MockResponse.Builder().body(Fixtures.spots).build() }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                if (path == "/api/v1/session") {
                    sessionCalls.incrementAndGet()
                    Thread.sleep(50)
                    return MockResponse.Builder().code(204)
                        .addHeader("Set-Cookie", "SESSION=$validToken; Max-Age=86400; Path=/; HttpOnly")
                        .build()
                }
                val cookie = request.headers["Cookie"].orEmpty()
                if (rejectAll || cookie != "SESSION=$validToken") return MockResponse.Builder().code(401).build()
                return when {
                    path == "/api/v1/spots" -> spotsResponder(spotsCalls.incrementAndGet())
                    path == "/api/v1/spots/500760" -> MockResponse.Builder().body(Fixtures.spot).build()
                    path == "/api/v1/spots/404" -> MockResponse.Builder().code(404).build()
                    path.endsWith("/analysis") -> MockResponse.Builder().code(503).build()
                    path == "/api/v1/status" -> MockResponse.Builder().body(Fixtures.status).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        store = InMemorySessionStore()
        build()
    }

    private fun build() {
        val config = NetworkConfig(baseUrl = server.url("/").toString(), clientId = "varun-android/test")
        session = SessionManager(server.url("/"), store, clock)
        val stack = HttpStack(config, session, retry = RetryInterceptor(sleeper = {}))
        source = VarunNetworkDataSource(stack.api, stack.paidApi, config)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun firstCallFetchesSessionThenData() = runBlocking {
        val spots = source.spots()
        assertThat(spots).hasSize(60)
        assertThat(sessionCalls.get()).isEqualTo(1)
        assertThat(store.load()).isEqualTo(validToken)
        // the cookie survives "process death": a new stack with the same store needs no session call
        build()
        source.status()
        assertThat(sessionCalls.get()).isEqualTo(1)
    }

    @Test
    fun sendsClientHeader() = runBlocking {
        source.status()
        val recorded = generateSequence { server.takeRequest() }.first { it.url.encodedPath == "/api/v1/status" }
        assertThat(recorded.headers["X-Client"]).isEqualTo("varun-android/test")
    }

    @Test
    fun unauthorizedRenewsOnceAndRetries() = runBlocking {
        store.save(token(hoursLeft = 20)) // fresh enough, but the server rotated its secret
        source.status()
        assertThat(sessionCalls.get()).isEqualTo(1)
    }

    @Test
    fun secondUnauthorizedSurfacesAsError() = runBlocking {
        store.save(token(hoursLeft = 20))
        rejectAll = true
        val error = runCatching { source.status() }.exceptionOrNull()
        assertThat(error).isInstanceOf(ApiError.Unauthorized::class.java)
        assertThat(sessionCalls.get()).isEqualTo(1)
    }

    @Test
    fun renewsProactivelyWhenLessThanTwelveHoursLeft() = runBlocking {
        store.save(token(hoursLeft = 11))
        validToken = token(hoursLeft = 11) // still accepted, but about to run out
        source.status()
        assertThat(sessionCalls.get()).isEqualTo(1)
    }

    @Test
    fun concurrentRequestsShareOneSessionCall() = runBlocking {
        List(8) { async(kotlinx.coroutines.Dispatchers.IO) { source.status() } }.awaitAll()
        assertThat(sessionCalls.get()).isEqualTo(1)
    }

    @Test
    fun getsAreRetriedOnServerErrors() = runBlocking {
        spotsResponder = { call ->
            if (call < 3) MockResponse.Builder().code(502).build() else MockResponse.Builder().body(Fixtures.spots).build()
        }
        assertThat(source.spots()).hasSize(60)
        assertThat(spotsCalls.get()).isEqualTo(3)
    }

    @Test
    fun getsGiveUpAfterThreeRetries() = runBlocking {
        spotsResponder = { MockResponse.Builder().code(500).build() }
        val error = runCatching { source.spots() }.exceptionOrNull()
        assertThat(error).isInstanceOf(ApiError.Server::class.java)
        assertThat(spotsCalls.get()).isEqualTo(4)
    }

    @Test
    fun paidCallIsNotRetriedAndMapsUnavailable() = runBlocking {
        val error = runCatching { source.generateAnalysis(Fixtures.SPOT_ID, "en") }.exceptionOrNull()
        assertThat(error).isInstanceOf(ApiError.Unavailable::class.java)
        val analysisCalls = generateSequence { server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
            .count { it.url.encodedPath.endsWith("/analysis") }
        assertThat(analysisCalls).isEqualTo(1)
    }

    @Test
    fun notFoundIsReported() = runBlocking {
        val error = runCatching { source.spot(404) }.exceptionOrNull()
        assertThat(error).isInstanceOf(ApiError.NotFound::class.java)
    }

    @Test
    fun offlineIsReported() = runBlocking {
        server.close()
        val error = runCatching { source.status() }.exceptionOrNull()
        assertThat(error).isInstanceOf(ApiError.Offline::class.java)
    }
}
