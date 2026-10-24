package surf.varun.core.network.dto

import kotlinx.serialization.Serializable

/*
 * Wire formats as served by the backend today. Every field is optional with a
 * default: the backend evolves independently of app releases, so a missing or
 * new field must never break parsing (the Json instance ignores unknown keys).
 */

@Serializable
data class SpotDto(
    val wgId: Int? = null,
    val name: String? = null,
    val country: String? = null,
    val windguruUrl: String? = null,
    val windguruFallbackUrl: String? = null,
    val windfinderUrl: String? = null,
    val icmUrl: String? = null,
    val webcamUrl: String? = null,
    val locationUrl: String? = null,
    val currentConditions: CurrentConditionsDto? = null,
    val currentConditionsHistory: List<CurrentConditionsDto>? = null,
    val forecast: List<ForecastDto>? = null,
    val forecastHourly: List<ForecastDto>? = null,
    val aiAnalysisEn: String? = null,
    val aiAnalysisPl: String? = null,
    val aiAnalysisEnCreatedAt: String? = null,
    val aiAnalysisPlCreatedAt: String? = null,
    val spotPhotoUrl: String? = null,
    val coordinates: CoordinatesDto? = null,
    val spotInfo: SpotInfoDto? = null,
    val spotInfoPL: SpotInfoDto? = null,
    val sponsors: List<SponsorDto>? = null,
    val availableModels: List<AvailableModelDto>? = null,
    val lastUpdated: String? = null,
)

@Serializable
data class CurrentConditionsDto(
    val date: String? = null,
    val wind: Double? = null,
    val gusts: Double? = null,
    val direction: String? = null,
    val temp: Double? = null,
)

@Serializable
data class ForecastDto(
    val date: String? = null,
    val wind: Double? = null,
    val gusts: Double? = null,
    val direction: String? = null,
    val temp: Double? = null,
    val precipitation: Double? = null,
    val cloudCoverPercent: Double? = null,
    val pressureHpa: Double? = null,
    val wave: Double? = null,
    val wavePeriod: Double? = null,
    val waveDirection: String? = null,
)

@Serializable
data class CoordinatesDto(val lat: Double? = null, val lon: Double? = null)

@Serializable
data class SpotInfoDto(
    val type: String? = null,
    val bestWind: String? = null,
    val waterTemp: String? = null,
    val experience: String? = null,
    val launch: String? = null,
    val hazards: String? = null,
    val season: String? = null,
    val description: String? = null,
)

@Serializable
data class SponsorDto(
    val id: Int? = null,
    val main: Boolean? = null,
    val name: String? = null,
    val link: String? = null,
)

@Serializable
data class AvailableModelDto(val key: String? = null, val name: String? = null)

@Serializable
data class HourlyForecastDto(val wgId: Int? = null, val hours: List<ForecastDto>? = null)

@Serializable
data class WindTimelineDto(
    val hours: List<String>? = null,
    val spots: List<SpotWindDto>? = null,
)

@Serializable
data class SpotWindDto(
    val wgId: Int? = null,
    val wind: List<Int?>? = null,
    val gusts: List<Int?>? = null,
    val direction: List<Int?>? = null,
)

@Serializable
data class ForecastProgressDto(
    val inProgress: Boolean? = null,
    val total: Int? = null,
    val completed: Int? = null,
    val fetched: Int? = null,
    val empty: Int? = null,
    val failed: Int? = null,
    val cached: Int? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val elapsedMs: Long? = null,
)

@Serializable
data class StatusDto(
    val status: String? = null,
    val version: String? = null,
    val uptime: String? = null,
    val uptimeSeconds: Long? = null,
    val startTime: String? = null,
    val spotsCount: Int? = null,
    val countriesCount: Int? = null,
    val liveStations: Int? = null,
)
