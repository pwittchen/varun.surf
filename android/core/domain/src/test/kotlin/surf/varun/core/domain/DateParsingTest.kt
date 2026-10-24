package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.DayLabel
import java.time.DayOfWeek
import java.time.LocalDateTime

class DateParsingTest {
    @Test
    fun parsesForecastHours() {
        assertThat(DateParsing.parseForecastTime("Sat 24 Oct 2026 14:00")).isEqualTo(LocalDateTime.of(2026, 10, 24, 14, 0))
        assertThat(DateParsing.parseForecastTime("Fri 06 Nov 2026 09:00")).isEqualTo(LocalDateTime.of(2026, 11, 6, 9, 0))
        assertThat(DateParsing.parseForecastTime("Fri  6 Nov 2026 09:00")).isEqualTo(LocalDateTime.of(2026, 11, 6, 9, 0))
    }

    @Test
    fun rejectsDailyLabelsAndGarbage() {
        assertThat(DateParsing.parseForecastTime("Today")).isNull()
        assertThat(DateParsing.parseForecastTime(null)).isNull()
        assertThat(DateParsing.parseForecastTime("Sat 99 Oct 2026 14:00")).isNull()
    }

    @Test
    fun parsesEveryStationFormatTheWebKnows() {
        assertThat(DateParsing.parseStationTime("24/10/26 20:08:52")).isEqualTo(LocalDateTime.of(2026, 10, 24, 20, 8, 52))
        assertThat(DateParsing.parseStationTime("2026-10-24 20:08")).isEqualTo(LocalDateTime.of(2026, 10, 24, 20, 8))
        assertThat(DateParsing.parseStationTime("2026-10-24 20:08:05")).isEqualTo(LocalDateTime.of(2026, 10, 24, 20, 8, 5))
        assertThat(DateParsing.parseStationTime("24.10.2026 20:08")).isEqualTo(LocalDateTime.of(2026, 10, 24, 20, 8))
        assertThat(DateParsing.parseStationTime("yesterday")).isNull()
        assertThat(DateParsing.parseStationTime("32/13/26 20:08:52")).isNull()
    }

    @Test
    fun mapsDailyLabels() {
        assertThat(DateParsing.parseDayLabel("Today")).isEqualTo(DayLabel.Today)
        assertThat(DateParsing.parseDayLabel("Tomorrow")).isEqualTo(DayLabel.Tomorrow)
        assertThat(DateParsing.parseDayLabel("Day 3")).isEqualTo(DayLabel.Offset(2))
        assertThat(DateParsing.parseDayLabel("Wed")).isEqualTo(DayLabel.Weekday(DayOfWeek.WEDNESDAY))
        assertThat(DateParsing.parseDayLabel("Wednesday")).isEqualTo(DayLabel.Weekday(DayOfWeek.WEDNESDAY))
        assertThat(DateParsing.parseDayLabel("Someday")).isEqualTo(DayLabel.Other("Someday"))
    }
}
