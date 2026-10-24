package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.DayLabel
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Spot
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ConditionsTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val now = ZonedDateTime.of(2026, 10, 24, 14, 20, 0, 0, warsaw)

    private fun hour(h: Int, wind: Double) = ForecastEntry(
        rawDate = "Sat 24 Oct 2026 $h:00",
        time = LocalDateTime.of(2026, 10, 24, h, 0),
        wind = wind, gusts = wind + 4, direction = Direction.SW,
    )

    private val daily = listOf(
        ForecastEntry(rawDate = "Today", day = DayLabel.Today, wind = 9.0, gusts = 12.0, direction = Direction.W),
        ForecastEntry(rawDate = "Tomorrow", day = DayLabel.Tomorrow, wind = 20.0, gusts = 25.0, direction = Direction.W),
    )

    private val live = LiveConditions("24/10/26 14:10:00", LocalDateTime.of(2026, 10, 24, 14, 10), 18, 23, Direction.SW, 12)

    @Test
    fun liveReadingWins() {
        val spot = Spot(1, "Jastarnia", "Poland", live = live, hourly = listOf(hour(14, 5.0)))
        val reading = Conditions.current(spot, now)!!
        assertThat(reading.isLive).isTrue()
        assertThat(reading.wind).isEqualTo(18.0)
        assertThat(Conditions.firingScore(spot, now)).isEqualTo(20.5)
    }

    @Test
    fun fallsBackToClosestHourThenToday() {
        val hourly = Spot(1, "A", "Poland", hourly = listOf(hour(12, 10.0), hour(14, 15.0), hour(17, 22.0)), daily = daily)
        assertThat(Conditions.current(hourly, now)!!.wind).isEqualTo(15.0)
        assertThat(Conditions.current(hourly, now)!!.isLive).isFalse()

        val dailyOnly = Spot(2, "B", "Poland", daily = daily)
        assertThat(Conditions.current(dailyOnly, now)!!.wind).isEqualTo(9.0)
    }

    @Test
    fun spotsWithoutDataSinkInTheFiringSort() {
        val empty = Spot(3, "C", "Poland")
        assertThat(Conditions.current(empty, now)).isNull()
        assertThat(Conditions.firingScore(empty, now)).isEqualTo(Double.NEGATIVE_INFINITY)
    }

    @Test
    fun usesTheSpotsOwnZoneForNow() {
        // 14:20 in Warsaw is 13:20 in the Canaries; the 13:00 row is the closest there
        val canary = Spot(
            4, "El Medano", "Spain",
            coordinates = surf.varun.core.model.Coordinates(28.0, -16.5),
            hourly = listOf(hour(13, 11.0), hour(14, 30.0)),
        )
        assertThat(Conditions.current(canary, now)!!.wind).isEqualTo(11.0)
    }

    @Test
    fun staleAfterSixtyMinutes() {
        val fresh = live.copy(observedAt = LocalDateTime.of(2026, 10, 24, 13, 21))
        val stale = live.copy(observedAt = LocalDateTime.of(2026, 10, 24, 13, 20))
        assertThat(Conditions.isStale(fresh, warsaw, now)).isFalse()
        assertThat(Conditions.isStale(stale, warsaw, now)).isTrue()
        assertThat(Conditions.isStale(live.copy(observedAt = null), warsaw, now)).isTrue()
    }
}
