package surf.varun.core.network

/**
 * @param baseUrl backend root, ending with "/"
 * @param clientId sent as `X-Client` so app traffic is visible apart from the web
 */
data class NetworkConfig(
    val baseUrl: String = BuildConfig.BASE_URL,
    val clientId: String,
)
