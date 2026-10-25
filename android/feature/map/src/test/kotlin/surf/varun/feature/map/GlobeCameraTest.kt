package surf.varun.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlobeCameraTest {
    private fun camera() = GlobeCamera(48.0, 10.0, 4.3).apply { setSize(400.0, 700.0) }

    /** Run the frame loop until the camera stops moving on its own. */
    private fun GlobeCamera.settle(from: Long): Long {
        var now = from
        repeat(600) {
            now += 16
            tick(now, 16.0, 1.0, spin = false)
        }
        return now
    }

    @Test
    fun `whole globe zoom fits the planet in the shorter side`() {
        val camera = camera()
        assertThat(radiusForZoom(camera.wholeGlobeZoom())).isWithin(1e-6).of(400 * 0.42)
    }

    @Test
    fun `zoom is clamped between a little past the whole planet and the deepest level`() {
        val camera = camera()
        camera.zoomBy(-20.0, 0)
        assertThat(radiusForZoom(camera.leafletZoom)).isWithin(1e-6).of(400 * 0.42 * 0.8)
        camera.zoomBy(40.0, 0)
        assertThat(camera.leafletZoom).isEqualTo(GlobeCamera.MAX_ZOOM)
    }

    @Test
    fun `dragging turns the globe under the finger and never tips past the tilt limit`() {
        val camera = GlobeCamera(0.0, 0.0, 2.0).apply { setSize(400.0, 700.0) }
        val before = camera.version
        // Finger moving right drags the planet east: the view turns west
        camera.pan(50.0, 0.0, 0)
        assertThat(camera.lon).isLessThan(0.0)
        assertThat(camera.version).isGreaterThan(before)
        camera.pan(0.0, 100_000.0, 0)
        assertThat(camera.lat).isEqualTo(GlobeCamera.MAX_TILT)
    }

    @Test
    fun `a fly lands where it was sent`() {
        val camera = camera()
        camera.flyTo(-33.9, 18.4, 8.0, 0)
        assertThat(camera.animating).isTrue()
        camera.settle(0)
        assertThat(camera.animating).isFalse()
        assertThat(camera.lat).isWithin(1e-9).of(-33.9)
        assertThat(camera.lon).isWithin(1e-9).of(18.4)
        assertThat(camera.leafletZoom).isWithin(1e-9).of(8.0)
    }

    @Test
    fun `reduced motion jumps instead of flying`() {
        val camera = camera().apply { reduceMotion = true }
        camera.flyTo(10.0, 20.0, 5.0, 0)
        assertThat(camera.animating).isFalse()
        assertThat(camera.lat).isEqualTo(10.0)
        camera.fling(1000.0, 0.0, 0)
        assertThat(camera.animating).isFalse()
    }

    @Test
    fun `fit frames close points closely and spread ones as the whole planet`() {
        val camera = camera()
        camera.fit(listOf(54.6 to 18.8, 54.8 to 18.4), GlobeCamera.FILTER_FIT_PADDING, GlobeCamera.FILTER_FIT_MAX_ZOOM, 0)
        camera.settle(0)
        assertThat(camera.leafletZoom).isWithin(1e-9).of(GlobeCamera.FILTER_FIT_MAX_ZOOM)

        camera.fit(listOf(54.0 to 18.0, -34.0 to 18.0, 20.0 to -157.0), GlobeCamera.FILTER_FIT_PADDING, GlobeCamera.FILTER_FIT_MAX_ZOOM, 0)
        camera.settle(0)
        assertThat(camera.leafletZoom).isWithin(1e-9).of(camera.wholeGlobeZoom())
    }

    @Test
    fun `a tapped bubble opens up even when its spots spread over half the planet`() {
        val camera = camera()
        camera.jumpTo(40.0, 10.0, camera.wholeGlobeZoom())
        val before = camera.leafletZoom
        camera.fit(listOf(28.0 to -16.0, 41.0 to 29.0, 64.0 to -22.0, 30.0 to 33.0), 40.0, 12.0, 0, minLeafletZoom = before + 1)
        camera.settle(0)
        assertThat(camera.leafletZoom).isAtLeast(before + 1)
    }

    @Test
    fun `the idle spin waits after a touch and turns east`() {
        val camera = camera()
        camera.stop(1_000)
        val lon = camera.lon
        camera.tick(2_000, 16.0, 1.0, spin = true)
        assertThat(camera.lon).isEqualTo(lon)
        camera.tick(6_000, 16.0, 1.0, spin = true)
        assertThat(camera.lon).isGreaterThan(lon)
    }

    @Test
    fun `switching over from the map flies out to the whole planet once`() {
        val camera = GlobeCamera(48.0, 10.0, 4.3).apply { introPending = true }
        camera.introduce(0)
        assertThat(camera.animating).isFalse() // no size yet
        camera.setSize(400.0, 700.0)
        camera.introduce(0)
        camera.settle(0)
        assertThat(camera.leafletZoom).isWithin(1e-9).of(camera.wholeGlobeZoom())
        assertThat(camera.introPending).isFalse()
    }
}
