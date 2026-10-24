package surf.varun.core.data

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import surf.varun.core.network.HttpStack
import surf.varun.core.network.NetworkConfig
import surf.varun.core.network.RetryInterceptor
import surf.varun.core.network.VarunNetworkDataSource
import surf.varun.core.network.session.InMemorySessionStore
import surf.varun.core.network.session.SessionManager
import surf.varun.core.testing.Fixtures
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A MockWebServer serving the captured fixtures, with per-path overrides and call counts. */
class TestBackend : AutoCloseable {
    val server = MockWebServer()
    val calls = ConcurrentHashMap<String, AtomicInteger>()
    val overrides = ConcurrentHashMap<String, () -> MockResponse>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                calls.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                overrides[path]?.let { return it() }
                return when {
                    path == "/api/v1/session" -> MockResponse.Builder().code(204)
                        .addHeader("Set-Cookie", "SESSION=4102444800.c2ln; Path=/").build()
                    path == "/api/v1/spots" -> ok(Fixtures.spots)
                    path == "/api/v1/spots/${Fixtures.SPOT_ID}" -> ok(Fixtures.spot)
                    path.startsWith("/api/v1/spots/${Fixtures.SPOT_ID}/") && request.method == "GET" -> ok(Fixtures.spot)
                    path == "/api/v1/forecast/${Fixtures.SPOT_ID}" -> ok(Fixtures.forecast)
                    path == "/api/v1/wind" -> ok(Fixtures.wind)
                    path == "/api/v1/status/forecast" -> ok(Fixtures.statusForecast)
                    path == "/api/v1/status" -> ok(Fixtures.status)
                    path == "/api/v1/sponsors" -> ok(Fixtures.sponsors)
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    fun ok(body: String) = MockResponse.Builder().body(body).build()

    fun count(path: String) = calls[path]?.get() ?: 0

    val dataSource: VarunNetworkDataSource by lazy {
        val config = NetworkConfig(baseUrl = server.url("/").toString(), clientId = "test")
        val stack = HttpStack(config, SessionManager(server.url("/"), InMemorySessionStore()), retry = RetryInterceptor(sleeper = {}))
        VarunNetworkDataSource(stack.api, stack.paidApi, config)
    }

    override fun close() = server.close()
}
