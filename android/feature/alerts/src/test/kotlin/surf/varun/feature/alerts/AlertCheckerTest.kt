package surf.varun.feature.alerts

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.domain.AlertEvaluator
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Spot
import surf.varun.core.model.WindAlert
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertCheckerTest {
    private val now = ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, ZoneId.of("Europe/Warsaw"))
    private val live = LiveConditions("", null, 21, 26, Direction.SW, 12)
    private val jastarnia = Spot(500760, "Jastarnia", "Poland", live = live)
    private val calm = Spot(2, "Calm", "Poland", live = live.copy(wind = 5, gusts = 7))

    private fun alert(id: Long, wgId: Int, trigger: AlertTrigger, min: Int = 15) =
        WindAlert(id = id, wgId = wgId, spotName = "x", minWind = min, trigger = trigger, minConsecutiveHours = 2)

    @Test
    fun liveHitsOnlyForEnabledMatchingAlerts() {
        val alerts = listOf(
            alert(1, 500760, AlertTrigger.LIVE),
            alert(2, 2, AlertTrigger.LIVE),
            alert(3, 500760, AlertTrigger.LIVE).copy(enabled = false),
            alert(4, 500760, AlertTrigger.FORECAST),
            alert(5, 999, AlertTrigger.LIVE),
        )
        val hits = AlertChecker.live(alerts, mapOf(500760 to jastarnia, 2 to calm), now)
        assertThat(hits.map { it.alert.id }).containsExactly(1L)
        assertThat(hits.single().spotName).isEqualTo("Jastarnia")
        assertThat(hits.single().match).isEqualTo(AlertEvaluator.Match.Live(21, 26, Direction.SW))
    }

    @Test
    fun suppressedAlertsStayQuiet() {
        val recently = alert(1, 500760, AlertTrigger.LIVE).copy(lastFiredAt = now.minusHours(1).toInstant())
        assertThat(AlertChecker.live(listOf(recently), mapOf(500760 to jastarnia), now)).isEmpty()
    }

    @Test
    fun forecastWindowsUseTheGridHours() {
        fun hour(h: Int, wind: Double) = ForecastEntry(
            rawDate = "", time = LocalDateTime.of(2026, 10, 24, h, 0), wind = wind, gusts = wind + 4, direction = Direction.W,
        )
        val hours = mapOf(500760 to listOf(hour(12, 18.0), hour(13, 19.0), hour(14, 9.0)), 2 to listOf(hour(12, 4.0)))
        val alerts = listOf(alert(1, 500760, AlertTrigger.FORECAST), alert(2, 2, AlertTrigger.FORECAST), alert(3, 77, AlertTrigger.FORECAST))
        val hits = AlertChecker.forecast(alerts, hours, mapOf(500760 to jastarnia), now)
        assertThat(hits.map { it.alert.id }).containsExactly(1L)
        val window = hits.single().match as AlertEvaluator.Match.Window
        assertThat(window.start).isEqualTo(LocalDateTime.of(2026, 10, 24, 12, 0))
        assertThat(window.maxWind).isEqualTo(19.0)
    }
}
