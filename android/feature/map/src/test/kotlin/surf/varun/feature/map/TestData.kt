package surf.varun.feature.map

import surf.varun.core.model.Coordinates
import surf.varun.core.model.DayLabel
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Spot
import surf.varun.core.model.SpotWindSeries
import surf.varun.core.model.WindTimeline
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal object TestData {
    val now: ZonedDateTime = ZonedDateTime.of(2026, 10, 24, 22, 20, 0, 0, ZoneId.of("Europe/Warsaw"))

    private fun today(wind: Double) =
        ForecastEntry(rawDate = "Today", day = DayLabel.Today, wind = wind, gusts = wind + 4, direction = Direction.W)

    /** Live station, Poland. */
    val jastarnia = Spot(
        wgId = 500760, name = "Jastarnia", country = "Poland",
        coordinates = Coordinates(54.69, 18.66),
        live = LiveConditions("24/10/26 22:10:00", LocalDateTime.of(2026, 10, 24, 22, 10), 18, 23, Direction.SW, 12),
        daily = listOf(today(10.0)),
    )

    /** Forecast only, Spain. */
    val tarifa = Spot(
        wgId = 1, name = "Tarifa", country = "Spain",
        coordinates = Coordinates(36.0, -5.6),
        daily = listOf(today(25.0)),
    )

    /** No coordinates: never on the map. */
    val nowhere = Spot(wgId = 2, name = "Nowhere", country = "Poland", daily = listOf(today(5.0)))

    /** Not in the timeline at all. */
    val lonely = Spot(wgId = 3, name = "Lonely", country = "Italy", coordinates = Coordinates(45.0, 13.0))

    val spots = listOf(jastarnia, tarifa, nowhere, lonely)

    val timeline = WindTimeline(
        hours = listOf(
            LocalDateTime.of(2026, 10, 24, 22, 0),
            LocalDateTime.of(2026, 10, 24, 23, 0),
            LocalDateTime.of(2026, 10, 25, 0, 0),
        ),
        rawHours = listOf("Sat 24 Oct 2026 22:00", "Sat 24 Oct 2026 23:00", "Sun 25 Oct 2026 00:00"),
        spots = mapOf(
            500760 to SpotWindSeries(listOf(20, null, 4), listOf(26, null, null), listOf(Direction.SW, null, Direction.N)),
            1 to SpotWindSeries(listOf(30, 31, 32), listOf(35, 36, 37), listOf(Direction.NE, Direction.NE, null)),
        ),
    )
}
