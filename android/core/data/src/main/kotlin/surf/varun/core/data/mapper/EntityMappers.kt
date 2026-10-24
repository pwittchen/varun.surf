package surf.varun.core.data.mapper

import org.json.JSONArray
import org.json.JSONObject
import surf.varun.core.database.entity.AiAnalysisEntity
import surf.varun.core.database.entity.AlertEntity
import surf.varun.core.database.entity.AvailableModelEntity
import surf.varun.core.database.entity.DailyForecastEntity
import surf.varun.core.database.entity.ForecastColumns
import surf.varun.core.database.entity.HourlyForecastEntity
import surf.varun.core.database.entity.LiveConditionsEntity
import surf.varun.core.database.entity.LiveHistoryEntity
import surf.varun.core.database.entity.SpotEntity
import surf.varun.core.database.entity.SpotInfoColumns
import surf.varun.core.database.entity.SpotWithConditions
import surf.varun.core.domain.DateParsing
import surf.varun.core.model.AiAnalysis
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Coordinates
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.ForecastModelOption
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Sponsor
import surf.varun.core.model.Spot
import surf.varun.core.model.SpotInfo
import surf.varun.core.model.SpotLinks
import surf.varun.core.model.WindAlert
import java.time.Instant
import java.time.LocalDateTime

internal const val LANG_EN = "en"
internal const val LANG_PL = "pl"

// --- model -> entity -------------------------------------------------------------

internal fun Spot.toEntity(serverOrder: Int, fetchedAt: Long) = SpotEntity(
    wgId = wgId,
    name = name,
    country = country,
    serverOrder = serverOrder,
    windguruUrl = links.windguru,
    windfinderUrl = links.windfinder,
    icmUrl = links.icm,
    webcamUrl = links.webcam,
    locationUrl = links.location,
    lat = coordinates?.lat,
    lon = coordinates?.lon,
    photoUrl = photoUrl,
    info = info?.toColumns(),
    infoPl = infoPl?.toColumns(),
    sponsors = sponsors.takeIf { it.isNotEmpty() }?.let(::encodeSponsors),
    lastUpdated = lastUpdated,
    fetchedAt = fetchedAt,
)

private fun SpotInfo.toColumns() = SpotInfoColumns(type, bestWind, waterTemp, experience, launch, hazards, season, description)

internal fun LiveConditions.toEntity(wgId: Int, fetchedAt: Long) = LiveConditionsEntity(
    wgId = wgId,
    rawDate = rawDate,
    observedAt = observedAt?.toString(),
    wind = wind,
    gusts = gusts,
    direction = direction?.name,
    temp = temp,
    fetchedAt = fetchedAt,
)

internal fun LiveConditions.toHistoryEntity(wgId: Int, position: Int) = LiveHistoryEntity(
    wgId = wgId,
    position = position,
    rawDate = rawDate,
    observedAt = observedAt?.toString(),
    wind = wind,
    gusts = gusts,
    direction = direction?.name,
    temp = temp,
)

private fun ForecastEntry.toColumns() = ForecastColumns(
    rawDate = rawDate,
    wind = wind,
    gusts = gusts,
    direction = direction?.name,
    temp = temp,
    precipitation = precipitation,
    cloudCoverPercent = cloudCoverPercent,
    pressureHpa = pressureHpa,
    wave = wave,
    wavePeriod = wavePeriod,
    waveDirection = waveDirection?.name,
)

internal fun ForecastEntry.toDailyEntity(wgId: Int, position: Int) = DailyForecastEntity(wgId, position, toColumns())

internal fun ForecastEntry.toHourlyEntity(wgId: Int, model: String) =
    time?.let { HourlyForecastEntity(wgId, model, it.toString(), toColumns()) }

internal fun Spot.analysisEntities(): List<AiAnalysisEntity> = listOfNotNull(
    aiAnalysisEn?.let { AiAnalysisEntity(wgId, LANG_EN, it.text, it.createdAt?.toEpochMilli()) },
    aiAnalysisPl?.let { AiAnalysisEntity(wgId, LANG_PL, it.text, it.createdAt?.toEpochMilli()) },
)

internal fun Spot.modelEntities(): List<AvailableModelEntity> =
    availableModels.mapIndexed { index, model -> AvailableModelEntity(wgId, model.key, model.name, index) }

// --- entity -> model -------------------------------------------------------------

internal fun SpotWithConditions.toModel(): Spot = Spot(
    wgId = spot.wgId,
    name = spot.name,
    country = spot.country,
    links = SpotLinks(spot.windguruUrl, spot.windfinderUrl, spot.icmUrl, spot.webcamUrl, spot.locationUrl),
    coordinates = if (spot.lat != null && spot.lon != null) Coordinates(spot.lat!!, spot.lon!!) else null,
    photoUrl = spot.photoUrl,
    live = live?.toModel(),
    daily = daily.sortedBy { it.position }.map { it.toModel() },
    info = spot.info?.toModel(),
    infoPl = spot.infoPl?.toModel(),
    sponsors = spot.sponsors?.let(::decodeSponsors).orEmpty(),
    lastUpdated = spot.lastUpdated,
)

private fun SpotInfoColumns.toModel() = SpotInfo(type, bestWind, waterTemp, experience, launch, hazards, season, description)

internal fun LiveConditionsEntity.toModel() = LiveConditions(
    rawDate = rawDate,
    observedAt = observedAt?.let(::parseLocal) ?: DateParsing.parseStationTime(rawDate),
    wind = wind,
    gusts = gusts,
    direction = Direction.parse(direction),
    temp = temp,
)

internal fun LiveHistoryEntity.toModel() = LiveConditions(
    rawDate = rawDate,
    observedAt = observedAt?.let(::parseLocal),
    wind = wind,
    gusts = gusts,
    direction = Direction.parse(direction),
    temp = temp,
)

private fun ForecastColumns.toEntry(time: LocalDateTime?) = ForecastEntry(
    rawDate = rawDate,
    time = time,
    day = if (time == null) DateParsing.parseDayLabel(rawDate) else null,
    wind = wind,
    gusts = gusts,
    direction = Direction.parse(direction),
    temp = temp,
    precipitation = precipitation,
    cloudCoverPercent = cloudCoverPercent,
    pressureHpa = pressureHpa,
    wave = wave,
    wavePeriod = wavePeriod,
    waveDirection = Direction.parse(waveDirection),
)

internal fun DailyForecastEntity.toModel() = values.toEntry(null)

internal fun HourlyForecastEntity.toModel() = values.toEntry(parseLocal(time))

internal fun List<AiAnalysisEntity>.analysis(lang: String): AiAnalysis? =
    firstOrNull { it.lang == lang }?.let { AiAnalysis(it.text, it.createdAt?.let(Instant::ofEpochMilli)) }

internal fun AvailableModelEntity.toModel() = ForecastModelOption(key, name)

private fun parseLocal(value: String): LocalDateTime? = runCatching { LocalDateTime.parse(value) }.getOrNull()

// --- alerts ----------------------------------------------------------------------

internal fun AlertEntity.toModel() = WindAlert(
    id = id,
    wgId = wgId,
    spotName = spotName,
    minWind = minWind,
    maxWind = maxWind,
    directions = Direction.fromMask(directions),
    trigger = runCatching { AlertTrigger.valueOf(trigger) }.getOrDefault(AlertTrigger.FORECAST),
    horizonDays = horizonDays,
    minConsecutiveHours = minConsecutiveHours,
    quietStartHour = quietStartHour,
    quietEndHour = quietEndHour,
    lastFiredAt = lastFiredAt?.let(Instant::ofEpochMilli),
    mutedUntil = mutedUntil?.let(Instant::ofEpochMilli),
    enabled = enabled,
)

internal fun WindAlert.toEntity() = AlertEntity(
    id = id,
    wgId = wgId,
    spotName = spotName,
    minWind = minWind,
    maxWind = maxWind,
    directions = Direction.toMask(directions),
    trigger = trigger.name,
    horizonDays = horizonDays,
    minConsecutiveHours = minConsecutiveHours,
    quietStartHour = quietStartHour,
    quietEndHour = quietEndHour,
    lastFiredAt = lastFiredAt?.toEpochMilli(),
    mutedUntil = mutedUntil?.toEpochMilli(),
    enabled = enabled,
)

// --- sponsors as a JSON column -------------------------------------------------------

private fun encodeSponsors(sponsors: List<Sponsor>): String = JSONArray().apply {
    sponsors.forEach { s ->
        put(JSONObject().put("id", s.id).put("name", s.name).put("link", s.link ?: "").put("main", s.main))
    }
}.toString()

private fun decodeSponsors(json: String): List<Sponsor> = runCatching {
    val array = JSONArray(json)
    List(array.length()) { i ->
        val o = array.getJSONObject(i)
        Sponsor(o.optInt("id"), o.optString("name"), o.optString("link").takeIf { it.isNotEmpty() }, o.optBoolean("main"))
    }
}.getOrDefault(emptyList())
