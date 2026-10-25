package surf.varun.feature.spots

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.Coordinates
import surf.varun.core.model.DayLabel
import surf.varun.core.model.Direction
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.LiveConditions
import surf.varun.core.model.Spot
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SpotListingTest {
    private val now = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, ZoneId.of("Europe/Warsaw"))

    private fun today(wind: Double) = ForecastEntry(rawDate = "Today", day = DayLabel.Today, wind = wind, gusts = wind, direction = Direction.W)

    private fun live(wind: Int) = LiveConditions("", LocalDateTime.of(2026, 10, 24, 11, 55), wind, wind + 2, Direction.SW, 10)

    private val jastarnia = Spot(1, "Jastarnia", "Poland", coordinates = Coordinates(54.69, 18.66), live = live(20), daily = listOf(today(15.0)))
    private val leba = Spot(2, "Łeba", "Poland", coordinates = Coordinates(54.76, 17.55), daily = listOf(today(25.0)))
    private val tarifa = Spot(3, "Tarifa", "Spain", coordinates = Coordinates(36.0, -5.6), daily = listOf(today(8.0)))
    private val empty = Spot(4, "Atlantis", "Nowhere")
    private val spots = listOf(empty, tarifa, leba, jastarnia)

    @Test
    fun firingSortPutsSpotsWithoutDataLast() {
        val order = SpotListing.apply(spots, SpotFilters(), SpotSort.FIRING, emptySet(), null, now).map { it.spot.wgId }
        assertThat(order).containsExactly(2, 1, 3, 4).inOrder()
    }

    @Test
    fun filtersCombine() {
        val live = SpotListing.apply(spots, SpotFilters(liveOnly = true), SpotSort.FIRING, emptySet(), null, now)
        assertThat(live.map { it.spot.wgId }).containsExactly(1)
        val polishLive = SpotListing.apply(spots, SpotFilters(liveOnly = true, country = "Poland"), SpotSort.NAME, emptySet(), null, now)
        assertThat(polishLive.map { it.spot.wgId }).containsExactly(1)
        val polish = SpotListing.apply(spots, SpotFilters(country = "Poland"), SpotSort.NAME, emptySet(), null, now)
        assertThat(polish.map { it.spot.name }).containsExactly("Jastarnia", "Łeba").inOrder()
    }

    @Test
    fun distanceSortPutsTheClosestFirst() {
        val gdansk = Coordinates(54.35, 18.65)
        val all = SpotListing.apply(spots, SpotFilters(), SpotSort.DISTANCE, emptySet(), gdansk, now)
        assertThat(all.map { it.spot.wgId }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(all.first().distanceKm!!).isWithin(2.0).of(38.0)
    }

    @Test
    fun favouritesAreMarked() {
        val models = SpotListing.apply(spots, SpotFilters(), SpotSort.NAME, setOf(3), null, now)
        assertThat(models.single { it.favourite }.spot.wgId).isEqualTo(3)
    }

    @Test
    fun countriesAndRouteResolution() {
        assertThat(SpotListing.countries(spots).map { it.name to it.count })
            .containsExactly("Nowhere" to 1, "Poland" to 2, "Spain" to 1).inOrder()
        val known = setOf("Poland", "Czech Republic")
        assertThat(SpotListing.resolveCountry("czech-republic", known)).isEqualTo("Czech Republic")
        assertThat(SpotListing.resolveCountry("POLAND", known)).isEqualTo("Poland")
        assertThat(SpotListing.resolveCountry("atlantis", known)).isNull()
    }
}
