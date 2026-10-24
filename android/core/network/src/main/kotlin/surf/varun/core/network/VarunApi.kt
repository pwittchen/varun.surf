package surf.varun.core.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import surf.varun.core.network.dto.ForecastProgressDto
import surf.varun.core.network.dto.HourlyForecastDto
import surf.varun.core.network.dto.SponsorDto
import surf.varun.core.network.dto.SpotDto
import surf.varun.core.network.dto.StatusDto
import surf.varun.core.network.dto.WindTimelineDto

/** The read-only part of the backend API. Every call needs the SESSION cookie. */
interface VarunApi {
    /** Large (~820 spots, ~580 KB): returned raw so it can be decoded as a stream. */
    @Streaming
    @GET("api/v1/spots")
    suspend fun spots(): ResponseBody

    @GET("api/v1/spots/{wgId}")
    suspend fun spot(@Path("wgId") wgId: Int): SpotDto

    @GET("api/v1/spots/{wgId}/{model}")
    suspend fun spot(@Path("wgId") wgId: Int, @Path("model") model: String): SpotDto

    @GET("api/v1/forecast/{wgId}")
    suspend fun forecast(@Path("wgId") wgId: Int): HourlyForecastDto

    @GET("api/v1/wind")
    suspend fun wind(@Query("hours") hours: Int): WindTimelineDto

    @Streaming
    @GET("api/v1/wind")
    suspend fun windRaw(@Query("hours") hours: Int): ResponseBody

    @GET("api/v1/sponsors")
    suspend fun sponsors(): List<SponsorDto>

    @GET("api/v1/status")
    suspend fun status(): StatusDto

    @GET("api/v1/status/forecast")
    suspend fun forecastProgress(): ForecastProgressDto
}

/**
 * The two operations that cost money per call. Kept apart so they run on a client
 * with a 90 s timeout and no automatic retry - see spec 6.5.
 */
interface VarunPaidApi {
    @POST("api/v1/spots/{wgId}/analysis")
    suspend fun generateAnalysis(@Path("wgId") wgId: Int, @Query("lang") lang: String): SpotDto

    @POST("api/v1/spots/{wgId}/icm")
    suspend fun generateIcm(@Path("wgId") wgId: Int): SpotDto
}
