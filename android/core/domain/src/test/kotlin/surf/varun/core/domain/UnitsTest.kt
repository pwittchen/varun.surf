package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.TemperatureUnit
import surf.varun.core.model.WindUnit

class UnitsTest {
    @Test
    fun convertsWind() {
        assertThat(Units.formatWind(20.0, WindUnit.KNOTS)).isEqualTo("20")
        assertThat(Units.formatWind(20.0, WindUnit.KILOMETERS_PER_HOUR)).isEqualTo("37")
        assertThat(Units.formatWind(10.0, WindUnit.METERS_PER_SECOND)).isEqualTo("5.1")
        assertThat(Units.formatWind(30.0, WindUnit.METERS_PER_SECOND)).isEqualTo("15")
        assertThat(Units.toKnots(Units.wind(17.0, WindUnit.METERS_PER_SECOND), WindUnit.METERS_PER_SECOND)).isWithin(1e-9).of(17.0)
    }

    @Test
    fun beaufortScale() {
        assertThat(Units.beaufort(0.5)).isEqualTo(0)
        assertThat(Units.beaufort(10.0)).isEqualTo(3)
        assertThat(Units.beaufort(11.0)).isEqualTo(4)
        assertThat(Units.beaufort(21.0)).isEqualTo(5)
        assertThat(Units.beaufort(70.0)).isEqualTo(12)
    }

    @Test
    fun convertsTemperature() {
        assertThat(Units.formatTemperature(20.0, TemperatureUnit.FAHRENHEIT)).isEqualTo("68")
        assertThat(Units.formatTemperature(-3.4, TemperatureUnit.CELSIUS)).isEqualTo("-3")
    }
}
