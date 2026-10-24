package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import java.time.LocalDateTime

class ForecastDaysTest {
    private fun at(day: Int, hour: Int, wind: Double) = ForecastEntry(
        rawDate = "", time = LocalDateTime.of(2026, 10, day, hour, 0), wind = wind, gusts = wind, direction = Direction.N,
    )

    @Test
    fun keepsDaytimeHoursSixToTwentyOneInclusive() {
        val days = ForecastDays.daytimeDays(listOf(at(24, 5, 30.0), at(24, 6, 3.0), at(24, 21, 3.0), at(24, 22, 30.0)))
        assertThat(days.single().hours.map { it.time!!.hour }).containsExactly(6, 21)
    }

    @Test
    fun windyDaysFilterDropsDaysWithoutTwelveKnots() {
        val hours = listOf(at(24, 10, 11.9), at(25, 10, 12.0), at(26, 4, 25.0), at(26, 12, 5.0))
        val days = ForecastDays.daytimeDays(hours, windyOnly = true)
        assertThat(days.map { it.date.dayOfMonth }).containsExactly(25)
        assertThat(ForecastDays.daytimeDays(hours).map { it.date.dayOfMonth }).containsExactly(24, 25, 26).inOrder()
    }
}
