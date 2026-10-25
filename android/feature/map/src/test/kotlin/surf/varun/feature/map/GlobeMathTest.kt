package surf.varun.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GlobeMathTest {
    private val coarse = File("src/main/assets/globe/outlines-110m.bin").inputStream().use(GlobeOutlines::read)

    /** Collects clipped subpaths as polygons, for a point-in-polygon check. */
    private class Recorder : PathSink {
        val polygons = ArrayList<FloatArray>()
        private var current = ArrayList<Float>()

        override fun moveTo(x: Float, y: Float) {
            flush()
            current.add(x)
            current.add(y)
        }

        override fun lineTo(x: Float, y: Float) {
            current.add(x)
            current.add(y)
        }

        override fun close() = flush()

        private fun flush() {
            if (current.size >= 6) polygons += current.toFloatArray()
            current = ArrayList()
        }

        /** Non-zero winding, the fill rule the globe draws land with. */
        fun contains(x: Double, y: Double): Boolean {
            var winding = 0
            for (p in polygons) {
                val n = p.size / 2
                for (i in 0 until n) {
                    val x0 = p[i * 2].toDouble()
                    val y0 = p[i * 2 + 1].toDouble()
                    val x1 = p[(i + 1) % n * 2].toDouble()
                    val y1 = p[(i + 1) % n * 2 + 1].toDouble()
                    val side = (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0)
                    if (y0 <= y && y1 > y && side > 0) winding++
                    if (y0 > y && y1 <= y && side < 0) winding--
                }
            }
            return winding != 0
        }
    }

    private fun land(centerLat: Double, centerLon: Double): Pair<GlobeProjection, Recorder> {
        val projection = GlobeProjection(500.0, 500.0, 400.0, centerLat, centerLon)
        val recorder = Recorder()
        HorizonClipper().polygons(coarse.land, projection, recorder, minStep = 0f)
        return projection to recorder
    }

    private fun isLand(view: Pair<GlobeProjection, Recorder>, lat: Double, lon: Double): Boolean {
        val point = DoubleArray(3)
        assertThat(view.first.project(lat, lon, point)).isTrue()
        return view.second.contains(point[0], point[1])
    }

    @Test
    fun `land and sea come out right with Africa in the middle`() {
        val view = land(0.0, 20.0)
        assertThat(isLand(view, 20.0, 10.0)).isTrue() // Sahara
        assertThat(isLand(view, 60.0, 100.0)).isTrue() // Siberia, close to the rim
        assertThat(isLand(view, -30.0, -10.0)).isFalse() // South Atlantic
        assertThat(isLand(view, -20.0, 70.0)).isFalse() // Indian Ocean
    }

    @Test
    fun `a continent wrapped round the far side does not fill the disc`() {
        // Afro-Eurasia holds the antipode of the mid-Pacific
        val view = land(0.0, -150.0)
        assertThat(isLand(view, 0.0, -150.0)).isFalse()
        assertThat(isLand(view, 20.0, -170.0)).isFalse()
        assertThat(isLand(view, 40.0, -100.0)).isTrue() // the USA
    }

    @Test
    fun `europe from the default view`() {
        val view = land(GlobeCamera.DEFAULT_LAT, GlobeCamera.DEFAULT_LON)
        assertThat(isLand(view, 46.5, 10.0)).isTrue() // Alps
        assertThat(isLand(view, 55.75, 37.6)).isTrue() // Moscow
        assertThat(isLand(view, 35.0, 18.0)).isFalse() // Ionian Sea
        assertThat(isLand(view, 40.0, -30.0)).isFalse() // Atlantic
    }

    @Test
    fun `a place is land or sea whichever way the globe is turned`() {
        // Joining coasts along the wrong stretch of the rim shows up as land that
        // depends on the view; look at the same places from many directions
        val random = Random(7)
        val places = List(60) { random.nextDouble(-60.0, 70.0) to random.nextDouble(-180.0, 180.0) }
        val truth = places.map { (lat, lon) -> isLand(land(lat, lon), lat, lon) }
        repeat(40) {
            val view = land(random.nextDouble(-75.0, 75.0), random.nextDouble(-180.0, 180.0))
            val point = DoubleArray(3)
            places.forEachIndexed { i, (lat, lon) ->
                // Away from the rim, where the projection squeezes everything together
                if (view.first.project(lat, lon, point) && point[2] > 0.3) {
                    assertThat(view.second.contains(point[0], point[1])).isEqualTo(truth[i])
                }
            }
        }
    }

    @Test
    fun `unproject inverts project`() {
        val projection = GlobeProjection(200.0, 300.0, 150.0, 41.0, -12.0)
        val point = DoubleArray(3)
        val back = DoubleArray(2)
        assertThat(projection.project(48.0, 3.0, point)).isTrue()
        assertThat(projection.unproject(point[0], point[1], back)).isTrue()
        assertThat(back[0]).isWithin(1e-9).of(48.0)
        assertThat(back[1]).isWithin(1e-9).of(3.0)
        assertThat(projection.project(-41.0, 168.0, point)).isFalse()
    }

    @Test
    fun `equivalent zoom matches the flat map's world width`() {
        assertThat(equivalentZoom(radiusForZoom(6.5))).isWithin(1e-9).of(6.5)
        // A globe one 256 px world round is Leaflet zoom 0
        assertThat(equivalentZoom(256 / (2 * Math.PI))).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `spots close on screen merge and stand apart once zoomed in`() {
        val spots = listOf(feature(1, 54.6, 18.8, 20), feature(2, 54.7, 18.6, 8), feature(3, 36.0, -5.6, null))
        val far = clusterSpots(spots, 60.0, 300.0, equivalentZoom(300.0))
        assertThat(far).hasSize(2)
        val bubble = far.first { it.single == null }
        assertThat(bubble.members.map { it.wgId }).containsExactly(1, 2)
        assertThat(bubble.maxWind).isEqualTo(20)

        val near = clusterSpots(spots, 60.0, radiusForZoom(11.0), 11.0)
        assertThat(near.map { it.single?.wgId }).containsExactly(1, 2, 3)
    }

    @Test
    fun `wind field reaches only as far as a spot does`() {
        val samples = listOf(GlobeSample.of(WindSample(54.0, 18.0, 20.0, surf.varun.core.model.Direction.W)))
        val projection = GlobeProjection(0.0, 0.0, radiusForZoom(6.0), 54.0, 18.0)
        val index = FieldIndex.build(samples, projection)
        val out = DoubleArray(5)
        assertThat(index.sample(18.0, 54.0, out)).isTrue()
        assertThat(out[0]).isWithin(1e-9).of(20.0)
        // A westerly blows towards the east
        assertThat(out[1]).isGreaterThan(19.9)
        assertThat(out[2]).isWithin(1e-9).of(0.0)
        assertThat(index.sample(18.0, 54.0 + index.radiusDeg * 1.5, out)).isFalse()
    }

    @Test
    fun `spherical frame finds the middle and the spread`() {
        val frame = sphericalFrame(listOf(10.0 to 0.0, -10.0 to 0.0))!!
        assertThat(frame.lat).isWithin(1e-9).of(0.0)
        assertThat(frame.lon).isWithin(1e-9).of(0.0)
        assertThat(frame.spread).isWithin(1e-9).of(10.0)
        assertThat(sphericalFrame(emptyList())).isNull()
    }

    private fun feature(id: Int, lat: Double, lon: Double, wind: Int?) =
        MapSpotFeature(id, "spot $id", lat, lon, wind, null, null, MapFeatures.windClassKey(wind?.toDouble()), false)
}
