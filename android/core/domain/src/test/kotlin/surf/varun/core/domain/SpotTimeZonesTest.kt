package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.Coordinates
import java.time.ZoneId

class SpotTimeZonesTest {
    @Test
    fun singleZoneCountries() {
        assertThat(SpotTimeZones.idFor("Poland", null)).isEqualTo("Europe/Warsaw")
        assertThat(SpotTimeZones.idFor("United Kingdom", null)).isEqualTo("Europe/London")
    }

    @Test
    fun multiZoneCountriesSplitByCoordinates() {
        assertThat(SpotTimeZones.idFor("Spain", Coordinates(28.0, -16.5))).isEqualTo("Atlantic/Canary")
        assertThat(SpotTimeZones.idFor("Spain", Coordinates(36.0, -5.6))).isEqualTo("Europe/Madrid")
        assertThat(SpotTimeZones.idFor("Portugal", Coordinates(33.0, -16.3))).isEqualTo("Atlantic/Madeira")
        assertThat(SpotTimeZones.idFor("USA", Coordinates(20.9, -156.4))).isEqualTo("Pacific/Honolulu")
        assertThat(SpotTimeZones.idFor("USA", Coordinates(45.7, -121.5))).isEqualTo("America/Los_Angeles")
        assertThat(SpotTimeZones.idFor("USA", Coordinates(26.1, -97.2))).isEqualTo("America/Chicago")
        assertThat(SpotTimeZones.idFor("USA", Coordinates(35.3, -75.5))).isEqualTo("America/New_York")
        assertThat(SpotTimeZones.idFor("Brazil", Coordinates(-27.6, -48.5))).isEqualTo("America/Sao_Paulo")
        assertThat(SpotTimeZones.idFor("Brazil", Coordinates(-2.8, -40.5))).isEqualTo("America/Fortaleza")
        assertThat(SpotTimeZones.idFor("Australia", Coordinates(-31.0, 115.3))).isEqualTo("Australia/Perth")
        assertThat(SpotTimeZones.idFor("Australia", Coordinates(-26.8, 153.1))).isEqualTo("Australia/Brisbane")
        assertThat(SpotTimeZones.idFor("Australia", Coordinates(-37.9, 145.0))).isEqualTo("Australia/Melbourne")
        assertThat(SpotTimeZones.idFor("Australia", Coordinates(-34.0, 151.2))).isEqualTo("Australia/Sydney")
        assertThat(SpotTimeZones.idFor("Mexico", Coordinates(24.0, -110.0))).isEqualTo("America/Mazatlan")
    }

    @Test
    fun everyKnownZoneIdIsValidAndUnknownFallsBack() {
        Countries.of("Poland") // touch
        val fallback = ZoneId.of("UTC")
        assertThat(SpotTimeZones.zoneFor("Atlantis", null, fallback)).isEqualTo(fallback)
        listOf(
            "Poland", "Czech Republic", "Iceland", "Morocco", "Cape Verde", "Turks and Caicos",
            "Argentina", "India", "Taiwan", "Spain", "Portugal", "USA", "Brazil", "Australia", "Mexico",
        ).forEach { assertThat(SpotTimeZones.zoneFor(it, null, fallback)).isNotEqualTo(fallback) }
    }
}
