package surf.varun.core.network

import okhttp3.Interceptor
import okhttp3.Response

class ClientHeadersInterceptor(private val clientId: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("X-Client", clientId)
                .header("User-Agent", clientId)
                .header("Accept", "application/json")
                .build(),
        )
}
