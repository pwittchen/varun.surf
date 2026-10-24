package surf.varun.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.domain.WindClass
import surf.varun.core.model.DayLabel
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Spot
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetModelsTest {
    private val now = ZonedDateTime.of(2026, 10, 24, 14, 20, 0, 0, ZoneId.of("Europe/Warsaw"))

    private fun live(wind: Int, gusts: Int, minute: Int = 10) =
        LiveConditions("", LocalDateTime.of(2026, 10, 24, 14, minute), wind, gusts, Direction.SW, 12)

    private fun hour(h: Int, wind: Double, day: Int = 24) = ForecastEntry(
        rawDate = "", time = LocalDateTime.of(2026, 10, day, h, 0), wind = wind, gusts = wind + 3, direction = Direction.W,
    )

    private val today = ForecastEntry(rawDate = "Today", day = DayLabel.Today, wind = 9.0, gusts = 12.0, direction = Direction.N)

    @Test
    fun favouritesAreOrderedByFiringScoreWithEmptyOnesLast() {
        val spots = listOf(
            Spot(1, "Empty", "Poland"),
            Spot(2, "Breezy", "Poland", live = live(10, 12)),
            Spot(3, "Firing", "Poland", live = live(22, 28)),
            Spot(4, "Forecast", "Poland", daily = listOf(today)),
        )
        val ordered = WidgetModels.favourites(spots, now)
        assertThat(ordered.map { it.wgId }).containsExactly(3, 2, 4, 1).inOrder()
        assertThat(ordered.first().windClass).isEqualTo(WindClass.STRONG) // (22 + 28) / 2 = 25
        assertThat(ordered.last().reading).isNull()
    }

    @Test
    fun liveReadingCarriesAgeAndStaleness() {
        val fresh = WidgetModels.spot(Spot(1, "A", "Poland", live = live(18, 23, minute = 10)), emptyList(), now)
        assertThat(fresh.ageMinutes).isEqualTo(10)
        assertThat(fresh.stale).isFalse()
        val old = WidgetModels.spot(
            Spot(1, "A", "Poland", live = live(18, 23).copy(observedAt = LocalDateTime.of(2026, 10, 24, 12, 0))),
            emptyList(),
            now,
        )
        assertThat(old.stale).isTrue()
    }

    @Test
    fun spotWithoutStationUsesTheClosestGridHour() {
        val grid = listOf(hour(13, 8.0), hour(14, 16.0), hour(15, 20.0))
        val model = WidgetModels.spot(Spot(1, "A", "Poland", daily = listOf(today)), grid, now)
        assertThat(model.reading!!.isLive).isFalse()
        assertThat(model.reading!!.wind).isEqualTo(16.0)
    }

    @Test
    fun stripCoversTheNextTwelveHoursInTwoHourSteps() {
        val grid = (14..23).map { hour(it, it.toDouble()) } + (0..6).map { hour(it, 5.0, day = 25) }
        val strip = WidgetModels.strip(grid, LocalDateTime.of(2026, 10, 24, 14, 20))
        assertThat(strip.map { it.time!!.hour }).containsExactly(15, 17, 19, 21, 23, 1).inOrder()
    }

    @Test
    fun arrowsPointWhereTheWindBlows() {
        assertThat(WidgetModels.arrow(Direction.N)).isEqualTo("↓")
        assertThat(WidgetModels.arrow(Direction.SW)).isEqualTo("↗")
        assertThat(WidgetModels.arrow(null)).isEqualTo("•")
    }
}
