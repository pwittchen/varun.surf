package surf.varun.core.network

import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import surf.varun.core.network.session.SessionInterceptor
import surf.varun.core.network.session.SessionManager
import java.util.concurrent.TimeUnit

/**
 * Builds the HTTP clients and API interfaces. Hilt's NetworkModule and the tests
 * both go through here, so what is tested is what ships.
 */
class HttpStack(
    config: NetworkConfig,
    val session: SessionManager,
    cache: Cache? = null,
    retry: RetryInterceptor = RetryInterceptor(),
) {
    /** Cookies and client headers only - shared with the image loader. */
    val baseClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(session)
        .addInterceptor(ClientHeadersInterceptor(config.clientId))
        .apply { if (cache != null) cache(cache) }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        .also { session.sessionClient = it }

    private val apiClient: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(SessionInterceptor(session))
        .addInterceptor(retry)
        .build()

    /**
     * 90 s for the paid POSTs - the backend streams the model's answer before it
     * replies - and no [RetryInterceptor]: a timeout may still have been billed.
     * The session interceptor may repeat a request rejected with 401, which the
     * backend refuses before any model is called.
     */
    private val paidClient: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(SessionInterceptor(session))
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: VarunApi = retrofit(config.baseUrl, apiClient).create(VarunApi::class.java)
    val paidApi: VarunPaidApi = retrofit(config.baseUrl, paidClient).create(VarunPaidApi::class.java)
}
