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

    /** Every command a clipper sends, in order. */
    private class Commands : PathSink {
        val list = ArrayList<Triple<Char, Float, Float>>()

        override fun moveTo(x: Float, y: Float) {
            list += Triple('M', x, y)
        }

        override fun lineTo(x: Float, y: Float) {
            list += Triple('L', x, y)
        }

        override fun close() {
            list += Triple('Z', 0f, 0f)
        }
    }

    @Test
    fun `culling what faces away leaves the outlines exactly as they were`() {
        val landCaps = HorizonClipper.caps(coarse.land)
        val borderCaps = HorizonClipper.caps(coarse.borders)
        val random = Random(7)
        repeat(60) {
            val p = GlobeProjection(200.0, 380.0, 160.0, random.nextDouble(-75.0, 75.0), random.nextDouble(-180.0, 180.0))
            val plain = Commands()
            val culled = Commands()
            HorizonClipper().polygons(coarse.land, p, plain)
            HorizonClipper().lines(coarse.borders, p, plain)
            HorizonClipper().polygons(coarse.land, p, culled, caps = landCaps)
            HorizonClipper().lines(coarse.borders, p, culled, caps = borderCaps)
            assertThat(culled.list).isEqualTo(plain.list)
        }
    }

    @Test
    fun `culling what is off screen only drops points outside it`() {
        val landCaps = HorizonClipper.caps(coarse.land)
        val borderCaps = HorizonClipper.caps(coarse.borders)
        val random = Random(11)
        repeat(60) {
            val p = GlobeProjection(200.0, 380.0, radiusForZoom(random.nextDouble(3.0, 7.0)), random.nextDouble(-60.0, 60.0), random.nextDouble(-180.0, 180.0))
            val plain = Commands()
            val culled = Commands()
            HorizonClipper().polygons(coarse.land, p, plain)
            HorizonClipper().lines(coarse.borders, p, plain)
            HorizonClipper().polygons(coarse.land, p, culled, caps = landCaps, width = 400.0, height = 760.0)
            HorizonClipper().lines(coarse.borders, p, culled, caps = borderCaps, width = 400.0, height = 760.0)
            fun onScreen(c: Triple<Char, Float, Float>) = c.first != 'Z' && c.second in 0f..400f && c.third in 0f..760f
            assertThat(culled.list.filter(::onScreen)).containsExactlyElementsIn(plain.list.filter(::onScreen))
            assertThat(culled.list.size).isAtMost(plain.list.size)
        }
    }

    @Test
    fun `field index matches interpolating over every spot`() {
        val random = Random(3)
        val samples = List(400) {
            GlobeSample.of(
                WindSample(
                    random.nextDouble(-60.0, 70.0),
                    random.nextDouble(-180.0, 180.0),
                    random.nextDouble(0.0, 35.0),
                    if (random.nextInt(5) == 0) null else surf.varun.core.model.Direction.entries.random(random),
                ),
            )
        }
        val index = FieldIndex(samples, radiusDeg = 6.0, pxPerDeg = 12.0, radiusPx = 72.0)
        val out = DoubleArray(5)
        repeat(2_000) {
            val lat = random.nextDouble(-70.0, 80.0)
            val lon = random.nextDouble(-200.0, 200.0)
            val expected = bruteForce(samples, lat, lon, 6.0, 12.0)
            assertThat(index.sample(lon, lat, out)).isEqualTo(expected != null)
            if (expected != null) for (i in 0 until 5) assertThat(out[i]).isWithin(1e-9).of(expected[i])
        }
    }

    /** [FieldIndex.sample] written out over every spot, no buckets. */
    private fun bruteForce(samples: List<GlobeSample>, lat: Double, lon: Double, radiusDeg: Double, pxPerDeg: Double): DoubleArray? {
        val cosLat = maxOf(0.05, kotlin.math.cos(lat * DEG))
        var weightSum = 0.0
        var magSum = 0.0
        var vectorWeight = 0.0
        var uSum = 0.0
        var vSum = 0.0
        var nearestAll = Double.MAX_VALUE
        var nearestDir = Double.MAX_VALUE
        for (s in samples) {
            val dLat = s.lat - lat
            val dLon = normalizeLon(s.lon - lon) * cosLat
            val distSq = dLat * dLat + dLon * dLon
            nearestAll = minOf(nearestAll, distSq)
            if (s.hasDirection) nearestDir = minOf(nearestDir, distSq)
            if (distSq > radiusDeg * radiusDeg) continue
            val weight = 1 / (distSq * pxPerDeg * pxPerDeg + 1)
            weightSum += weight
            magSum += weight * s.wind
            if (s.hasDirection) {
                vectorWeight += weight
                uSum += weight * s.u
                vSum += weight * s.v
            }
        }
        if (weightSum == 0.0) return null
        val fadeSpan = radiusDeg * (1 - surf.varun.core.domain.WindField.FADE_FROM)
        val out = DoubleArray(5)
        out[0] = magSum / weightSum
        out[3] = ((radiusDeg - kotlin.math.sqrt(nearestAll)) / fadeSpan).coerceIn(0.0, 1.0)
        if (vectorWeight > 0) {
            out[1] = uSum / vectorWeight
            out[2] = vSum / vectorWeight
            out[4] = ((radiusDeg - kotlin.math.sqrt(nearestDir)) / fadeSpan).coerceIn(0.0, 1.0)
        }
        return out
    }

    private fun feature(id: Int, lat: Double, lon: Double, wind: Int?) =
        MapSpotFeature(id, "spot $id", lat, lon, wind, null, null, MapFeatures.windClassKey(wind?.toDouble()), false)
}
