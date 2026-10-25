package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.Direction

class WindFieldTest {
    @Test
    fun colourStopsMatchWeb() {
        assertThat(WindField.color(2.0)).isEqualTo(0x64748B)
        assertThat(WindField.color(12.0)).isEqualTo(0x2EE66D)
        assertThat(WindField.color(40.0)).isEqualTo(0xB0142A)
        // halfway between 18 (amber) and 25 (red) on each channel, JS rounding
        assertThat(WindField.color(21.5)).isEqualTo((245 shl 16) or (115 shl 8) or 43)
    }

    @Test
    fun cachedParticleColourMatchesTheExactOne() {
        for (quarter in 0..160) {
            val knots = quarter / 4.0
            assertThat(WindField.particleColorCached(knots)).isEqualTo(WindField.particleColor(knots))
        }
        assertThat(WindField.particleColorCached(-3.0)).isEqualTo(WindField.particleColor(0.0))
        assertThat(WindField.particleColorCached(80.0)).isEqualTo(WindField.particleColor(80.0))
    }

    @Test
    fun radiusGrowsAboveReferenceZoomAndIsCapped() {
        assertThat(WindField.radius(5.0)).isEqualTo(70.0)
        assertThat(WindField.radius(9.0)).isEqualTo(140.0)
        assertThat(WindField.radius(20.0)).isEqualTo(560.0)
        assertThat(WindField.leafletZoom(7.0)).isEqualTo(8.0)
    }

    @Test
    fun windFromNorthPushesParticlesSouth() {
        val samples = listOf(WindField.ScreenSample(50f, 50f, 20f, Direction.N))
        val points = WindField.velocityPoints(samples, 100, 100, 70.0)
        assertThat(points.single().v).isGreaterThan(19f) // +y is down the screen
        val field = WindField.vectorField(points, 100, 100, 70.0)
        val out = FloatArray(3)
        assertThat(field.sample(50f, 50f, out)).isTrue()
        assertThat(out[1]).isWithin(0.01f).of(20f)
        assertThat(out[2]).isGreaterThan(0.9f)
        assertThat(field.sample(-1f, 50f, out)).isFalse()
    }

    @Test
    fun heatCellsAreTransparentFarFromSpots() {
        val cells = WindField.heatCells(listOf(WindField.ScreenSample(4f, 4f, 20f, Direction.N)), 400, 16, 70.0)
        assertThat(cells.first() ushr 24).isGreaterThan(0)
        assertThat(cells.last()).isEqualTo(0)
    }
}
