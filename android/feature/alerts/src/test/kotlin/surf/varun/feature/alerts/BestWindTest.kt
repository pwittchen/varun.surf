package surf.varun.feature.alerts

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.Direction.E
import surf.varun.core.model.Direction.N
import surf.varun.core.model.Direction.NE
import surf.varun.core.model.Direction.NW
import surf.varun.core.model.Direction.S
import surf.varun.core.model.Direction.SE
import surf.varun.core.model.Direction.SW
import surf.varun.core.model.Direction.W

class BestWindTest {
    @Test
    fun listsAndSeparators() {
        assertThat(BestWind.parse("W, SW, NW")).containsExactly(W, SW, NW)
        assertThat(BestWind.parse("SW/W")).containsExactly(SW, W)
        assertThat(BestWind.parse("S; SE")).containsExactly(S, SE)
    }

    @Test
    fun ignoresWordsAroundTheCodes() {
        assertThat(BestWind.parse("Meltemi N, NE")).containsExactly(N, NE)
        assertThat(BestWind.parse("Bura NE, Maestral NW")).containsExactly(NE, NW)
        assertThat(BestWind.parse("Strong Levante E")).containsExactly(E)
        assertThat(BestWind.parse("W (Ponente), NW (Maestrale)")).containsExactly(W, NW)
        assertThat(BestWind.parse("SE trade winds")).containsExactly(SE)
    }

    @Test
    fun rangesAndSixteenPointCodes() {
        assertThat(BestWind.parse("N-NE")).containsExactly(N, NE)
        assertThat(BestWind.parse("NW-NE")).containsExactly(NW, N, NE)
        assertThat(BestWind.parse("E, ENE")).containsExactly(E, NE)
    }

    @Test
    fun unknownMeansAnyDirection() {
        assertThat(BestWind.parse("Thermal afternoon breeze")).isEmpty()
        assertThat(BestWind.parse("")).isEmpty()
        assertThat(BestWind.parse(null)).isEmpty()
    }
}
