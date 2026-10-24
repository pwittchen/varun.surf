package surf.varun.core.network.dto

import surf.varun.core.domain.DateParsing
import surf.varun.core.model.AiAnalysis
import surf.varun.core.model.Coordinates
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.model.ForecastProgress
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.ServiceStatus
import surf.varun.core.model.Sponsor
import surf.varun.core.model.Spot
import surf.varun.core.model.SpotInfo
import surf.varun.core.model.SpotLinks
import surf.varun.core.model.SpotWindSeries
import surf.varun.core.model.WindTimeline
import java.time.Instant
import kotlin.math.roundToInt

/** Empty strings mean "absent" on the wire. */
internal fun String?.orAbsent(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Spot DTO to model; null when the DTO lacks what makes a spot a spot (an id and
 * a name). [baseUrl] resolves the relative photo path.
 */
fun SpotDto.toModel(baseUrl: String): Spot? {
    val id = wgId ?: return null
    val spotName = name.orAbsent() ?: return null
    return Spot(
        wgId = id,
        name = spotName,
        country = country.orAbsent() ?: "",
        links = SpotLinks(
            windguru = windguruUrl.orAbsent() ?: windguruFallbackUrl.orAbsent(),
            windfinder = windfinderUrl.orAbsent(),
            icm = icmUrl.orAbsent(),
            webcam = webcamUrl.orAbsent(),
            location = locationUrl.orAbsent(),
        ),
        coordinates = coordinates?.toModel(),
        photoUrl = spotPhotoUrl.orAbsent()?.let { resolveUrl(baseUrl, it) },
        live = currentConditions?.toModel(),
        liveHistory = currentConditionsHistory.orEmpty().mapNotNull { it.toModel() },
        daily = forecast.orEmpty().mapNotNull { it.toDaily() },
        hourly = forecastHourly.orEmpty().mapNotNull { it.toHourly() },
        aiAnalysisEn = aiAnalysisEn.orAbsent()?.let { AiAnalysis(it, parseInstant(aiAnalysisEnCreatedAt)) },
        aiAnalysisPl = aiAnalysisPl.orAbsent()?.let { AiAnalysis(it, parseInstant(aiAnalysisPlCreatedAt)) },
        info = spotInfo?.toModel(),
        infoPl = spotInfoPL?.toModel(),
        sponsors = sponsors.orEmpty().mapNotNull { it.toModel() },
        availableModels = availableModels.orEmpty().mapNotNull { it.toModel() },
        lastUpdated = lastUpdated.orAbsent(),
    )
}

fun resolveUrl(baseUrl: String, path: String): String = when {
    path.startsWith("http://") || path.startsWith("https://") -> path
    else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}

private fun parseInstant(value: String?): Instant? = value.orAbsent()?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun CoordinatesDto.toModel(): Coordinates? {
    val la = lat ?: return null
    val lo = lon ?: return null
    if (!la.isFinite() || !lo.isFinite()) return null
    return Coordinates(la, lo)
}

fun CurrentConditionsDto.toModel(): LiveConditions? {
    val w = wind ?: return null
    val g = gusts ?: return null
    if (!w.isFinite() || !g.isFinite()) return null
    return LiveConditions(
        rawDate = date.orEmpty(),
        observedAt = DateParsing.parseStationTime(date),
        wind = w.roundToInt(),
        gusts = g.roundToInt(),
        direction = Direction.parse(direction),
        temp = temp?.takeIf { it.isFinite() }?.roundToInt(),
    )
}

private fun ForecastDto.toEntry(): ForecastEntry? {
    val w = wind?.takeIf { it.isFinite() } ?: return null
    val g = gusts?.takeIf { it.isFinite() } ?: w
    return ForecastEntry(
        rawDate = date.orEmpty(),
        wind = w,
        gusts = g,
        direction = Direction.parse(direction),
        temp = temp,
        precipitation = precipitation,
        cloudCoverPercent = cloudCoverPercent,
        pressureHpa = pressureHpa,
        wave = wave,
        wavePeriod = wavePeriod,
        waveDirection = Direction.parse(waveDirection),
    )
}

fun ForecastDto.toDaily(): ForecastEntry? = toEntry()?.copy(day = DateParsing.parseDayLabel(date))

fun ForecastDto.toHourly(): ForecastEntry? = toEntry()?.let { entry ->
    val time = DateParsing.parseForecastTime(date) ?: return null
    entry.copy(time = time)
}

fun SpotInfoDto.toModel() = SpotInfo(
    type = type.orAbsent(),
    bestWind = bestWind.orAbsent(),
    waterTemp = waterTemp.orAbsent(),
    experience = experience.orAbsent(),
    launch = launch.orAbsent(),
    hazards = hazards.orAbsent(),
    season = season.orAbsent(),
    description = description.orAbsent(),
)

fun SponsorDto.toModel(): Sponsor? {
    val sponsorName = name.orAbsent() ?: return null
    return Sponsor(id = id ?: 0, name = sponsorName, link = link.orAbsent(), main = main ?: false)
}

fun AvailableModelDto.toModel(): ForecastModelOption? {
    val modelKey = key.orAbsent() ?: return null
    return ForecastModelOption(modelKey, name.orAbsent() ?: modelKey.uppercase())
}

fun HourlyForecastDto.toModel(): List<ForecastEntry> = hours.orEmpty().mapNotNull { it.toHourly() }

fun WindTimelineDto.toModel(): WindTimeline {
    val raw = hours.orEmpty()
    return WindTimeline(
        hours = raw.map { DateParsing.parseForecastTime(it) },
        rawHours = raw,
        spots = spots.orEmpty().mapNotNull { dto ->
            val id = dto.wgId ?: return@mapNotNull null
            id to SpotWindSeries(
                wind = dto.wind.orEmpty(),
                gusts = dto.gusts.orEmpty(),
                direction = dto.direction.orEmpty().map { Direction.fromIndex(it) },
            )
        }.toMap(),
    )
}

fun ForecastProgressDto.toModel() = ForecastProgress(
    inProgress = inProgress ?: false,
    total = total ?: 0,
    completed = completed ?: 0,
    failed = failed ?: 0,
    finishedAt = finishedAt?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
)

fun StatusDto.toModel() = ServiceStatus(
    status = status,
    version = version,
    uptime = uptime,
    startTime = startTime,
    spotsCount = spotsCount,
    countriesCount = countriesCount,
    liveStations = liveStations,
)
