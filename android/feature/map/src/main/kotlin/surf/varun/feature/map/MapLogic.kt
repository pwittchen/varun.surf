package surf.varun.feature.map

import surf.varun.core.domain.Conditions
import surf.varun.core.domain.WindClass
import surf.varun.core.domain.WindReading
import surf.varun.core.model.Spot
import surf.varun.core.model.WindTimeline
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Wind for a spot at a timeline step, mirroring `getWindConditionsAtStep` in the
 * web's `weather.js`: step 0 is now (the live reading where the spot has one,
 * else the forecast for now); step N is hour N-1 of the shared `/api/v1/wind` grid.
 */
fun conditionsAtStep(spot: Spot, step: Int, timeline: WindTimeline?, now: ZonedDateTime): WindReading? {
    if (step < 1) return Conditions.current(spot, now)
    val series = timeline?.spots?.get(spot.wgId) ?: return null
    val index = step - 1
    val wind = series.wind.getOrNull(index) ?: return null
    return WindReading(
        wind = wind.toDouble(),
        // A spot without a gust value still has wind to draw; the sheet shows a dash
        gusts = series.gusts.getOrNull(index)?.toDouble() ?: Double.NaN,
        direction = series.direction.getOrNull(index),
        temp = null,
        isLive = false,
        forecastTime = timeline.hours.getOrNull(index),
        forecastLabel = timeline.rawHours.getOrNull(index),
    )
}

/** One marker's worth of data for the GeoJSON source, for the selected step. */
data class MapSpotFeature(
    val wgId: Int,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Wind in knots, null when the spot has nothing for this step (drawn grey, no number). */
    val wind: Int?,
    val gusts: Int?,
    /** Index into [surf.varun.core.model.Direction] (N=0 ... NW=7), null when unknown. */
    val dirIndex: Int?,
    val windClass: String,
    val isLive: Boolean,
)

object MapFeatures {
    const val NO_DATA = "nodata"

    fun windClassKey(knots: Double?): String =
        if (knots == null || knots.isNaN()) NO_DATA else WindClass.forMap(knots).name.lowercase()

    /** Features for every spot with coordinates; spots without wind for the step stay as grey dots. */
    fun build(spots: List<Spot>, step: Int, timeline: WindTimeline?, now: ZonedDateTime): List<MapSpotFeature> =
        spots.mapNotNull { spot ->
            val coordinates = spot.coordinates ?: return@mapNotNull null
            val reading = conditionsAtStep(spot, step, timeline, now)
            MapSpotFeature(
                wgId = spot.wgId,
                name = spot.name,
                lat = coordinates.lat,
                lon = coordinates.lon,
                wind = reading?.wind?.let { Math.round(it).toInt() },
                gusts = reading?.gusts?.takeUnless { it.isNaN() }?.let { Math.round(it).toInt() },
                dirIndex = reading?.direction?.ordinal,
                windClass = windClassKey(reading?.wind),
                isLive = reading?.isLive == true,
            )
        }
}

/** The map's filter chips (spec 5.4). */
data class MapFilters(
    val country: String? = null,
    val favouritesOnly: Boolean = false,
    val liveOnly: Boolean = false,
) {
    val active: Boolean get() = country != null || favouritesOnly || liveOnly

    fun apply(spots: List<Spot>, favourites: Set<Int>): List<Spot> = spots.filter { spot ->
        (country == null || spot.country == country) &&
            (!favouritesOnly || spot.wgId in favourites) &&
            (!liveOnly || spot.live != null)
    }
}

/**
 * The timeline's steps: 0 = now, then one per hour of the grid. Day ticks sit on
 * the first step of every new day, which is also where the stronger haptic fires.
 */
data class TimelineSteps(val hours: List<LocalDateTime?>) {
    val count: Int get() = hours.size + 1
    val lastStep: Int get() = hours.size

    fun timeAt(step: Int): LocalDateTime? = if (step < 1) null else hours.getOrNull(step - 1)

    fun isMidnight(step: Int): Boolean = timeAt(step)?.hour == 0

    /** Steps where a new day starts (midnight), for the slider's day ticks. */
    val dayStarts: List<Int>
        get() = (1..lastStep).filter { isMidnight(it) }

    fun clamp(step: Int): Int = step.coerceIn(0, lastStep)
}
