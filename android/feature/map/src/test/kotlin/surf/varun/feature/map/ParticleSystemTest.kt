package surf.varun.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import surf.varun.core.domain.WindField
import surf.varun.core.model.Direction
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class ParticleSystemTest {
    private val field = buildField(listOf(WindField.ScreenSample(100f, 100f, 20f, Direction.N)), 200, 200, mapLibreZoom = 5.0)

    @Test
    fun builtFieldCoversTheSpot() {
        assertThat(field.points).hasSize(1)
        assertThat(field.radius).isEqualTo(70.0)
        assertThat(field.heat.width).isEqualTo(25)
    }

    @Test
    fun northWindCarriesParticlesSouth() {
        val system = ParticleSystem(field, 50, Random(1))
        var down = 0
        var segments = 0
        system.step(1f) { _, y0, _, y1, kt ->
            segments++
            if (y1 > y0) down++
            assertThat(kt).isGreaterThan(0f)
        }
        assertThat(segments).isGreaterThan(0)
        assertThat(down).isEqualTo(segments)
    }

    @Test
    fun sheddingKeepsAFloor() {
        val system = ParticleSystem(field, 100, Random(1))
        repeat(20) { system.shed() }
        assertThat(system.size).isEqualTo(WindField.PARTICLES_MIN / 2)
    }
}
