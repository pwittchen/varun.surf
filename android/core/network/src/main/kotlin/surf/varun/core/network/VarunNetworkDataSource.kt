package surf.varun.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromStream
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.ForecastProgress
import surf.varun.core.model.ServiceStatus
import surf.varun.core.model.Sponsor
import surf.varun.core.model.Spot
import surf.varun.core.model.WindTimeline
import surf.varun.core.network.dto.SpotDto
import surf.varun.core.network.dto.WindTimelineDto
import surf.varun.core.network.dto.toModel
import javax.inject.Inject
import javax.inject.Singleton

/** The backend, in model terms. Every failure surfaces as an [ApiError]. */
@Singleton
class VarunNetworkDataSource @Inject constructor(
    private val api: VarunApi,
    private val paidApi: VarunPaidApi,
    private val config: NetworkConfig,
) {
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun spots(): List<Spot> = apiCall {
        withContext(Dispatchers.IO) {
            api.spots().use { body ->
                NetworkJson.decodeFromStream(ListSerializer(SpotDto.serializer()), body.byteStream())
            }.mapNotNull { it.toModel(config.baseUrl) }
        }
    }

    // Retrofit decodes on OkHttp's threads, but mapping hundreds of hourly rows (and
    // parsing their dates) would run on the caller's - usually the main thread
    suspend fun spot(wgId: Int, model: String? = null): Spot = apiCall {
        val dto = if (model == null) api.spot(wgId) else api.spot(wgId, model)
        withContext(Dispatchers.Default) { dto.toModel(config.baseUrl) }
            ?: throw ApiError.BadData(IllegalStateException("Spot $wgId without id or name"))
    }

    suspend fun hourlyForecast(wgId: Int): List<ForecastEntry> = apiCall {
        val dto = api.forecast(wgId)
        withContext(Dispatchers.Default) { dto.toModel() }
    }

    suspend fun windTimeline(hours: Int): WindTimeline = apiCall {
        val dto = api.wind(hours)
        withContext(Dispatchers.Default) { dto.toModel() }
    }

    /** The timeline as raw JSON, for callers that cache the payload as it came. */
    suspend fun windTimelineJson(hours: Int): String = apiCall {
        withContext(Dispatchers.IO) { api.windRaw(hours).use { it.string() } }
    }

    /** Parse what [windTimelineJson] returned. */
    fun parseWindTimeline(json: String): WindTimeline =
        NetworkJson.decodeFromString(WindTimelineDto.serializer(), json).toModel()

    suspend fun sponsors(): List<Sponsor> = apiCall { api.sponsors().mapNotNull { it.toModel() } }

    suspend fun status(): ServiceStatus = apiCall { api.status().toModel() }

    suspend fun forecastProgress(): ForecastProgress = apiCall { api.forecastProgress().toModel() }

    suspend fun generateAnalysis(wgId: Int, language: String): Spot = apiCall {
        paidApi.generateAnalysis(wgId, language).toModel(config.baseUrl)
            ?: throw ApiError.BadData(IllegalStateException("Spot $wgId without id or name"))
    }

    suspend fun generateIcm(wgId: Int): Spot = apiCall {
        paidApi.generateIcm(wgId).toModel(config.baseUrl)
            ?: throw ApiError.BadData(IllegalStateException("Spot $wgId without id or name"))
    }

    val baseUrl: String get() = config.baseUrl
}
