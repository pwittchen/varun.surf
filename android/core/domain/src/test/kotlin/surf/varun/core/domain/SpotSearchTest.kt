package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.Spot

class SpotSearchTest {
    private val spots = listOf(
        Spot(1, "Łeba", "Poland"),
        Spot(2, "Jastarnia", "Poland"),
        Spot(3, "Tarifa, Valdevaqueros", "Spain"),
        Spot(4, "El Médano (Tenerife)", "Spain"),
        Spot(5, "Podersdorf", "Austria"),
    )
    private val index = SpotSearch.index(spots)

    @Test
    fun foldsDiacritics() {
        assertThat(SpotSearch.search(index, "leba").map { it.wgId }).containsExactly(1)
        assertThat(SpotSearch.search(index, "medano").map { it.wgId }).containsExactly(4)
    }

    @Test
    fun matchesCountryInBothLanguagesAndRanksNameMatchesFirst() {
        assertThat(SpotSearch.search(index, "hiszpania").map { it.wgId }).containsExactly(4, 3)
        assertThat(SpotSearch.search(index, "polska").map { it.wgId }).containsExactly(2, 1).inOrder()
        assertThat(SpotSearch.search(index, "ta").first().wgId).isEqualTo(3)
    }

    @Test
    fun everyWordMustMatch() {
        assertThat(SpotSearch.search(index, "tarifa spain").map { it.wgId }).containsExactly(3)
        assertThat(SpotSearch.search(index, "tarifa poland")).isEmpty()
        assertThat(SpotSearch.search(index, "  ")).isEmpty()
    }

    @Test
    fun countriesAndSlugs() {
        assertThat(Countries.of("Poland").flag).isEqualTo("🇵🇱")
        assertThat(Countries.of("Poland").localizedName(true)).isEqualTo("Polska")
        assertThat(Countries.fromSlug("czech-republic", listOf("Poland", "Czech Republic"))).isEqualTo("Czech Republic")
        assertThat(Countries.of("Atlantis").name).isEqualTo("Atlantis")
    }
}
