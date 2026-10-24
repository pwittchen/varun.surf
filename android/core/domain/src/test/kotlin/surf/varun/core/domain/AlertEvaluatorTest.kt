package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.WindAlert
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertEvaluatorTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, zone)

    private val base = WindAlert(
        id = 1, wgId = 500760, spotName = "Jastarnia", minWind = 15,
        quietStartHour = 22, quietEndHour = 7,
    )

    private fun live(wind: Int, dir: Direction?) = LiveConditions("", null, wind, wind + 5, dir, 10)

    private fun hour(day: Int, h: Int, wind: Double, dir: Direction = Direction.SW) = ForecastEntry(
        rawDate = "", time = LocalDateTime.of(2026, 10, day, h, 0), wind = wind, gusts = wind + 5, direction = dir,
    )

    @Test
    fun liveAlertFiresAtThreshold() {
        val alert = base.copy(trigger = AlertTrigger.LIVE)
        assertThat(AlertEvaluator.evaluateLive(alert, live(15, Direction.SW), now)).isInstanceOf(AlertEvaluator.Decision.Fire::class.java)
        assertThat(AlertEvaluator.evaluateLive(alert, live(14, Direction.SW), now)).isEqualTo(AlertEvaluator.Decision.NoMatch)
    }

    @Test
    fun maxWindAndDirectionSectorsNarrowTheRule() {
        val alert = base.copy(trigger = AlertTrigger.LIVE, maxWind = 25, directions = setOf(Direction.NW, Direction.N, Direction.NE))
        assertThat(AlertEvaluator.matchesWind(alert, 20.0, Direction.N)).isTrue()
        // the N sector wraps 337.5 -> 22.5; NW and NE neighbours are separate bits
        assertThat(AlertEvaluator.matchesWind(alert, 20.0, Direction.NW)).isTrue()
        assertThat(AlertEvaluator.matchesWind(alert, 20.0, Direction.E)).isFalse()
        assertThat(AlertEvaluator.matchesWind(alert, 26.0, Direction.N)).isFalse()
        assertThat(AlertEvaluator.matchesWind(alert, 20.0, null)).isFalse()
        assertThat(Direction.fromDegrees(350.0)).isEqualTo(Direction.N)
        assertThat(Direction.fromDegrees(337.5)).isEqualTo(Direction.N)
        assertThat(Direction.fromDegrees(22.4)).isEqualTo(Direction.N)
        assertThat(Direction.fromDegrees(22.5)).isEqualTo(Direction.NE)
        assertThat(Direction.fromMask(Direction.toMask(alert.directions))).isEqualTo(alert.directions)
    }

    @Test
    fun liveCooldownIsThreeHours() {
        val alert = base.copy(trigger = AlertTrigger.LIVE, lastFiredAt = now.minusHours(2).toInstant())
        val decision = AlertEvaluator.evaluateLive(alert, live(20, Direction.SW), now)
        assertThat((decision as AlertEvaluator.Decision.Suppressed).reason).isEqualTo(AlertEvaluator.Reason.ALREADY_NOTIFIED)
        val later = base.copy(trigger = AlertTrigger.LIVE, lastFiredAt = now.minusHours(3).toInstant())
        assertThat(AlertEvaluator.evaluateLive(later, live(20, Direction.SW), now)).isInstanceOf(AlertEvaluator.Decision.Fire::class.java)
    }

    @Test
    fun quietHoursWrapMidnight() {
        val alert = base.copy(trigger = AlertTrigger.LIVE)
        assertThat(AlertEvaluator.inQuietHours(alert, now.withHour(23))).isTrue()
        assertThat(AlertEvaluator.inQuietHours(alert, now.withHour(6))).isTrue()
        assertThat(AlertEvaluator.inQuietHours(alert, now.withHour(7))).isFalse()
        val decision = AlertEvaluator.evaluateLive(alert, live(20, Direction.SW), now.withHour(23))
        assertThat((decision as AlertEvaluator.Decision.Suppressed).reason).isEqualTo(AlertEvaluator.Reason.QUIET_HOURS)
    }

    @Test
    fun forecastWindowNeedsConsecutiveDaytimeHours() {
        val alert = base.copy(minConsecutiveHours = 3, horizonDays = 2)
        val hours = listOf(
            hour(24, 10, 16.0), hour(24, 11, 10.0), hour(24, 12, 16.0), hour(24, 13, 17.0),
            // night hours never count
            hour(24, 22, 30.0), hour(24, 23, 30.0), hour(25, 0, 30.0),
            hour(25, 13, 16.0), hour(25, 14, 22.0), hour(25, 15, 18.0), hour(25, 16, 9.0),
        )
        val window = AlertEvaluator.findWindow(alert, hours, now.toLocalDateTime())!!
        assertThat(window.start).isEqualTo(LocalDateTime.of(2026, 10, 25, 13, 0))
        assertThat(window.end).isEqualTo(LocalDateTime.of(2026, 10, 25, 15, 0))
        assertThat(window.maxWind).isEqualTo(22.0)
        assertThat(window.direction).isEqualTo(Direction.SW)
    }

    @Test
    fun forecastWindowRespectsHorizonAndGaps() {
        val alert = base.copy(minConsecutiveHours = 2, horizonDays = 1)
        // a missing hour breaks the run
        val gap = listOf(hour(24, 10, 20.0), hour(24, 12, 20.0))
        assertThat(AlertEvaluator.findWindow(alert, gap, now.toLocalDateTime())).isNull()
        // beyond the horizon
        val far = listOf(hour(26, 10, 20.0), hour(26, 11, 20.0))
        assertThat(AlertEvaluator.findWindow(alert, far, now.toLocalDateTime())).isNull()
    }

    @Test
    fun forecastAlertNotifiesOncePerDay() {
        val hours = listOf(hour(24, 12, 20.0), hour(24, 13, 20.0))
        val alert = base.copy(minConsecutiveHours = 2)
        assertThat(AlertEvaluator.evaluateForecast(alert, hours, zone, now)).isInstanceOf(AlertEvaluator.Decision.Fire::class.java)
        val firedToday = alert.copy(lastFiredAt = now.minusHours(1).toInstant())
        val decision = AlertEvaluator.evaluateForecast(firedToday, hours, zone, now)
        assertThat((decision as AlertEvaluator.Decision.Suppressed).reason).isEqualTo(AlertEvaluator.Reason.ALREADY_NOTIFIED)
        val firedYesterday = alert.copy(lastFiredAt = now.minusDays(1).toInstant())
        assertThat(AlertEvaluator.evaluateForecast(firedYesterday, hours, zone, now)).isInstanceOf(AlertEvaluator.Decision.Fire::class.java)
    }

    @Test
    fun mutedAndDisabledAlertsStayQuiet() {
        val hours = listOf(hour(24, 12, 20.0), hour(24, 13, 20.0))
        val muted = base.copy(mutedUntil = now.plusHours(5).toInstant())
        assertThat((AlertEvaluator.evaluateForecast(muted, hours, zone, now) as AlertEvaluator.Decision.Suppressed).reason)
            .isEqualTo(AlertEvaluator.Reason.MUTED)
        val disabled = base.copy(enabled = false)
        assertThat((AlertEvaluator.evaluateForecast(disabled, hours, zone, now) as AlertEvaluator.Decision.Suppressed).reason)
            .isEqualTo(AlertEvaluator.Reason.DISABLED)
    }
}
