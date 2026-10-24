package surf.varun.feature.alerts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import surf.varun.core.domain.AlertEvaluator
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Direction
import surf.varun.core.model.WindAlert
import surf.varun.core.model.WindUnit
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class AlertMessagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>().also {
        android.provider.Settings.System.putString(it.contentResolver, android.provider.Settings.System.TIME_12_24, "24")
    }
    private val alert = WindAlert(id = 1, wgId = 500760, spotName = "Jastarnia", minWind = 15, directions = setOf(Direction.SW, Direction.W))

    @Test
    fun liveBody() {
        val hit = AlertHit(alert, AlertEvaluator.Match.Live(21, 26, Direction.SW), "Jastarnia")
        assertThat(AlertMessages.body(context, hit, WindUnit.KNOTS)).isEqualTo("Jastarnia is firing - 21 kts SW (gusts 26)")
    }

    @Test
    fun forecastBodyEndsAfterTheLastHour() {
        val window = AlertEvaluator.Match.Window(
            LocalDateTime.of(2026, 10, 24, 13, 0), LocalDateTime.of(2026, 10, 24, 16, 0), 22.0, 27.0, Direction.SW,
        )
        assertThat(AlertMessages.body(context, AlertHit(alert, window, "Jastarnia"), WindUnit.KNOTS))
            .isEqualTo("Wind window Sat 13:00-17:00 at Jastarnia, up to 22 kts")
    }

    @Test
    fun ruleSummaryUsesTheUsersUnit() {
        assertThat(AlertMessages.ruleSummary(context, alert.copy(trigger = AlertTrigger.LIVE), WindUnit.KILOMETERS_PER_HOUR))
            .isEqualTo("≥ 28 km/h · SW W · live station")
        assertThat(AlertMessages.ruleSummary(context, alert.copy(maxWind = 25, directions = emptySet(), horizonDays = 2), WindUnit.KNOTS))
            .isEqualTo("15-25 kts · any direction · forecast, next 2 days")
    }
}
