package surf.varun.widget

import surf.varun.core.domain.Conditions
import surf.varun.core.domain.WindClass
import surf.varun.core.domain.WindReading
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.Spot
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** What a widget shows for one spot, computed off the Glance composition. */
data class SpotWidgetModel(
    val wgId: Int,
    val name: String,
    val reading: WindReading?,
    val stale: Boolean,
    val ageMinutes: Long?,
    /** Forecast for the next hours (spot-local), for the large spot widget's strip. */
    val strip: List<ForecastEntry>,
) {
    val windClass: WindClass get() = reading?.windClass ?: WindClass.WEAK
}

object WidgetModels {
    /** The strip covers the next 12 hours in 2 hour steps - what fits a 4×2 widget. */
    const val STRIP_HOURS = 12
    const val STRIP_STEP = 2

    /**
     * [grid] is the spot's grid-aligned forecast: it stands in for "now" when the
     * spot has no station and feeds the strip.
     */
    fun spot(spot: Spot, grid: List<ForecastEntry>, now: ZonedDateTime): SpotWidgetModel {
        val withHours = if (spot.hourly.isEmpty() && grid.isNotEmpty()) spot.copy(hourly = grid) else spot
        val zone = Conditions.zoneOf(spot)
        val live = spot.live
        return SpotWidgetModel(
            wgId = spot.wgId,
            name = spot.name,
            reading = Conditions.current(withHours, now),
            stale = live != null && Conditions.isStale(live, zone, now),
            ageMinutes = live?.let { Conditions.ageOf(it, zone, now)?.toMinutes() },
            strip = strip(grid, Conditions.now(spot, now)),
        )
    }

    fun strip(grid: List<ForecastEntry>, spotNow: LocalDateTime): List<ForecastEntry> {
        val from = spotNow.withMinute(0).withSecond(0).withNano(0).plusHours(1)
        val until = from.plusHours(STRIP_HOURS.toLong())
        return grid
            .filter { it.time != null && it.time!! >= from && it.time!! < until }
            .sortedBy { it.time }
            .filter { (java.time.Duration.between(from, it.time).toHours() % STRIP_STEP) == 0L }
    }

    /** Favourites by "firing now", strongest first; spots without data keep their order at the end. */
    fun favourites(spots: List<Spot>, now: ZonedDateTime): List<SpotWidgetModel> =
        spots.map { it to Conditions.firingScore(it, now) }
            .sortedByDescending { it.second }
            .map { (spot, _) -> spot(spot, emptyList(), now) }

    /** The web's arrows (`getWindArrow`): where the wind blows to, N = ↓. Glance can't rotate images. */
    fun arrow(direction: Direction?): String = when (direction) {
        Direction.N -> "↓"
        Direction.NE -> "↙"
        Direction.E -> "←"
        Direction.SE -> "↖"
        Direction.S -> "↑"
        Direction.SW -> "↗"
        Direction.W -> "→"
        Direction.NW -> "↘"
        null -> "•"
    }
}
