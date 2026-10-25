package surf.varun.feature.map

import surf.varun.core.domain.WindField
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The globe's geometry, ported from the web's `common/globe.js`: an orthographic
 * projection, land clipped at the horizon, the wind field sampled on the sphere
 * and spots merged into clusters by angle. Pure Kotlin, in dp - the web works in
 * CSS pixels and every borrowed map constant is read in the same units.
 */

internal const val DEG = PI / 180

/** Leaflet's world is 256 px wide at zoom 0; the globe compares zooms through it. */
internal const val LEAFLET_TILE_SIZE = 256.0

internal fun normalizeLon(lon: Double): Double = ((lon % 360) + 540) % 360 - 180

/** The Leaflet zoom at which the flat map's world is as wide as a globe of radius [r] is round. */
internal fun equivalentZoom(r: Double): Double = ln(2 * PI * r / LEAFLET_TILE_SIZE) / ln(2.0)

/** The globe radius whose [equivalentZoom] is [leafletZoom]. */
internal fun radiusForZoom(leafletZoom: Double): Double = LEAFLET_TILE_SIZE * 2.0.pow(leafletZoom) / (2 * PI)

/**
 * Earth-fixed unit vectors: x towards (0°, 0°), y towards (0°, 90°E), z the north
 * pole. Outlines are stored like this, so projecting a point is a rotation.
 */
internal fun unitVector(lat: Double, lon: Double, out: DoubleArray) {
    val phi = lat * DEG
    val lambda = lon * DEG
    out[0] = cos(phi) * cos(lambda)
    out[1] = cos(phi) * sin(lambda)
    out[2] = sin(phi)
}

/**
 * The orthographic view of a globe of radius [r] centred on screen at ([cx], [cy])
 * and looking at ([lat], [lon]), north up.
 */
internal class GlobeProjection(
    val cx: Double,
    val cy: Double,
    val r: Double,
    val lat: Double,
    val lon: Double,
) {
    // Rows of the rotation: the view's east, north and towards-the-viewer axes
    private val e0 = -sin(lon * DEG)
    private val e1 = cos(lon * DEG)
    private val n0 = -sin(lat * DEG) * cos(lon * DEG)
    private val n1 = -sin(lat * DEG) * sin(lon * DEG)
    private val n2 = cos(lat * DEG)
    private val c0 = cos(lat * DEG) * cos(lon * DEG)
    private val c1 = cos(lat * DEG) * sin(lon * DEG)
    private val c2 = sin(lat * DEG)

    val leafletZoom: Double = equivalentZoom(r)

    /** Screen pixels per degree of a great circle. */
    val pxPerDeg: Double get() = r * DEG

    /** Rotate an Earth-fixed unit vector into view space: (east, north, depth); depth > 0 faces the viewer. */
    fun view(x: Double, y: Double, z: Double, out: DoubleArray) {
        out[0] = e0 * x + e1 * y
        out[1] = n0 * x + n1 * y + n2 * z
        out[2] = c0 * x + c1 * y + c2 * z
    }

    private val scratch = DoubleArray(3)

    /**
     * Project a point into [out] = (x, y, depth).
     * @return false when it is on the far side of the globe
     */
    fun project(lat: Double, lon: Double, out: DoubleArray): Boolean {
        unitVector(lat, lon, scratch)
        view(scratch[0], scratch[1], scratch[2], out)
        val depth = out[2]
        out[0] = cx + r * out[0]
        out[1] = cy - r * out[1]
        out[2] = depth
        return depth > 0
    }

    /**
     * The point under a screen position into [out] = (lat, lon).
     * @return false off the globe
     */
    fun unproject(x: Double, y: Double, out: DoubleArray): Boolean {
        val px = (x - cx) / r
        val py = (cy - y) / r
        val rhoSq = px * px + py * py
        if (rhoSq >= 1) return false
        val pz = sqrt(1 - rhoSq)
        // The inverse rotation is the transpose
        val ex = e0 * px + n0 * py + c0 * pz
        val ey = e1 * px + n1 * py + c1 * pz
        val ez = n2 * py + c2 * pz
        out[0] = asin(ez.coerceIn(-1.0, 1.0)) / DEG
        out[1] = atan2(ey, ex) / DEG
        return true
    }

    fun onScreen(x: Double, y: Double, width: Double, height: Double, margin: Double): Boolean =
        x > -margin && x < width + margin && y > -margin && y < height + margin
}

/** Where clipped outlines go: an android.graphics.Path on screen, a list of polygons in tests. */
internal interface PathSink {
    fun moveTo(x: Float, y: Float)

    fun lineTo(x: Float, y: Float)

    fun close()
}

/**
 * Clips outlines at the horizon, what d3's `clipAngle(90)` does for the web globe.
 *
 * Lines are cut where they go over the horizon. Land is harder: a coast running
 * behind the globe has to come back along the rim, or a continent half turned
 * away would be filled as a sliver - or, wrapped round the far side, as the whole
 * disc. Each ring is cut into its visible stretches, and the stretches are joined
 * along the rim the way d3's rejoin does: from where one leaves the disc, clockwise
 * to the nearest place another comes back in. That direction is what keeps a
 * clockwise ring's inside (d3's winding, kept by the outline converter) on the
 * same side of the rim as it is of the coast.
 */
internal class HorizonClipper {
    private var vx = DoubleArray(1024)
    private var vy = DoubleArray(1024)
    private var vz = DoubleArray(1024)
    private val point = DoubleArray(3)
    private val segments = ArrayList<Segment>()

    private class Segment(val points: FloatArray, val size: Int, val entry: Double, val exit: Double)

    private class Points {
        var data = FloatArray(256)
        var size = 0

        fun clear() {
            size = 0
        }

        fun add(x: Float, y: Float) {
            if (size + 2 > data.size) data = data.copyOf(data.size * 2)
            data[size++] = x
            data[size++] = y
        }

        val lastX: Float get() = data[size - 2]
        val lastY: Float get() = data[size - 1]
    }

    private val current = Points()

    /**
     * Rings are Earth-fixed unit vectors, xyz interleaved, closed (last == first).
     * [minStep] drops points closer than this to the last one kept.
     */
    fun polygons(rings: List<FloatArray>, projection: GlobeProjection, sink: PathSink, minStep: Float = 0.5f) {
        segments.clear()
        for (ring in rings) clipRing(ring, projection, sink, minStep)
        rejoin(projection, sink)
    }

    /** Open polylines, cut where they cross the horizon. */
    fun lines(lines: List<FloatArray>, projection: GlobeProjection, sink: PathSink, minStep: Float = 0.5f) {
        for (line in lines) {
            val n = line.size / 3
            if (n < 2) continue
            viewAll(line, n, projection)
            var open = false
            var lastX = 0f
            var lastY = 0f
            for (i in 0 until n) {
                val visible = vz[i] > 0
                if (i > 0 && visible != (vz[i - 1] > 0)) {
                    val (x, y) = crossing(i - 1, i, projection)
                    if (open) sink.lineTo(x, y) else sink.moveTo(x, y)
                    open = !open
                    lastX = x
                    lastY = y
                }
                if (!visible) continue
                val x = screenX(i, projection)
                val y = screenY(i, projection)
                if (!open) {
                    sink.moveTo(x, y)
                    open = true
                } else if (i == n - 1 || hypot(x - lastX, y - lastY) >= minStep) {
                    sink.lineTo(x, y)
                } else {
                    continue
                }
                lastX = x
                lastY = y
            }
        }
    }

    private fun viewAll(coords: FloatArray, n: Int, projection: GlobeProjection) {
        if (vx.size < n) {
            vx = DoubleArray(n * 2)
            vy = DoubleArray(n * 2)
            vz = DoubleArray(n * 2)
        }
        for (i in 0 until n) {
            projection.view(coords[i * 3].toDouble(), coords[i * 3 + 1].toDouble(), coords[i * 3 + 2].toDouble(), point)
            vx[i] = point[0]
            vy[i] = point[1]
            vz[i] = point[2]
        }
    }

    private fun screenX(i: Int, p: GlobeProjection) = (p.cx + p.r * vx[i]).toFloat()

    private fun screenY(i: Int, p: GlobeProjection) = (p.cy - p.r * vy[i]).toFloat()

    /** Where the great circle arc from point [a] to [b] meets the horizon, on screen. */
    private fun crossing(a: Int, b: Int, p: GlobeProjection): Pair<Float, Float> {
        val t = vz[a] / (vz[a] - vz[b])
        var x = vx[a] + (vx[b] - vx[a]) * t
        var y = vy[a] + (vy[b] - vy[a]) * t
        val length = hypot(x, y)
        if (length > 1e-12) {
            x /= length
            y /= length
        }
        return (p.cx + p.r * x).toFloat() to (p.cy - p.r * y).toFloat()
    }

    private fun angleOf(x: Float, y: Float, p: GlobeProjection): Double = atan2(y - p.cy, x - p.cx)

    private fun clipRing(ring: FloatArray, p: GlobeProjection, sink: PathSink, minStep: Float) {
        // The closing point repeats the first one
        val n = ring.size / 3 - 1
        if (n < 3) return
        viewAll(ring, n, p)

        var firstHidden = -1
        for (i in 0 until n) {
            if (vz[i] <= 0) {
                firstHidden = i
                break
            }
        }
        if (firstHidden == -1) {
            // Wholly in view: drawn as it is
            sink.moveTo(screenX(0, p), screenY(0, p))
            var lastX = screenX(0, p)
            var lastY = screenY(0, p)
            for (i in 1 until n) {
                val x = screenX(i, p)
                val y = screenY(i, p)
                if (hypot(x - lastX, y - lastY) < minStep) continue
                sink.lineTo(x, y)
                lastX = x
                lastY = y
            }
            sink.close()
            return
        }

        // Walk round from a hidden point, so every stretch starts where the ring comes into view
        var open = false
        var entry = 0.0
        current.clear()
        for (k in 1..n) {
            val a = (firstHidden + k - 1) % n
            val b = (firstHidden + k) % n
            val aVisible = vz[a] > 0
            val bVisible = vz[b] > 0
            when {
                !aVisible && bVisible -> {
                    val (x, y) = crossing(a, b, p)
                    current.clear()
                    current.add(x, y)
                    current.add(screenX(b, p), screenY(b, p))
                    entry = angleOf(x, y, p)
                    open = true
                }
                aVisible && bVisible && open -> {
                    val x = screenX(b, p)
                    val y = screenY(b, p)
                    if (hypot(x - current.lastX, y - current.lastY) >= minStep) current.add(x, y)
                }
                aVisible && !bVisible && open -> {
                    val (x, y) = crossing(a, b, p)
                    current.add(x, y)
                    segments += Segment(current.data.copyOf(current.size), current.size, entry, angleOf(x, y, p))
                    open = false
                }
            }
        }
    }

    private fun rejoin(p: GlobeProjection, sink: PathSink) {
        val count = segments.size
        if (count == 0) return
        val used = BooleanArray(count)
        for (start in 0 until count) {
            if (used[start]) continue
            val first = segments[start]
            sink.moveTo(first.points[0], first.points[1])
            var cur = start
            while (true) {
                used[cur] = true
                val segment = segments[cur]
                var i = 2
                while (i < segment.size) {
                    sink.lineTo(segment.points[i], segment.points[i + 1])
                    i += 2
                }
                // Clockwise along the rim to the nearest place a stretch comes back in
                var next = -1
                var bestSweep = Double.MAX_VALUE
                for (k in 0 until count) {
                    if (used[k] && k != start) continue
                    var sweep = (segments[k].entry - segment.exit) % (2 * PI)
                    if (sweep < 0) sweep += 2 * PI
                    if (sweep < bestSweep) {
                        bestSweep = sweep
                        next = k
                    }
                }
                if (next == -1) break
                rimArc(segment.exit, bestSweep, p, sink)
                if (next == start) break
                cur = next
            }
            sink.close()
        }
    }

    private fun rimArc(from: Double, sweep: Double, p: GlobeProjection, sink: PathSink) {
        val steps = max(1, ceil(sweep / RIM_STEP).toInt())
        for (s in 1..steps) {
            val angle = from + sweep * s / steps
            sink.lineTo((p.cx + p.r * cos(angle)).toFloat(), (p.cy + p.r * sin(angle)).toFloat())
        }
    }

    private companion object {
        const val RIM_STEP = 4 * DEG
    }
}

/** Graticule every 10°, like d3's `geoGraticule10`: parallels to ±80°, meridians to ±80° (every 90° to the poles). */
internal fun graticuleLines(): List<FloatArray> {
    val lines = ArrayList<FloatArray>()
    val v = DoubleArray(3)
    fun line(points: List<Pair<Double, Double>>) {
        val out = FloatArray(points.size * 3)
        points.forEachIndexed { i, (lat, lon) ->
            unitVector(lat, lon, v)
            out[i * 3] = v[0].toFloat()
            out[i * 3 + 1] = v[1].toFloat()
            out[i * 3 + 2] = v[2].toFloat()
        }
        lines += out
    }
    for (lat in -80..80 step 10) line((-180..180 step 2).map { lat.toDouble() to it.toDouble() })
    for (lon in -180 until 180 step 10) {
        val reach = if (lon % 90 == 0) 90 else 80
        line((-reach..reach step 2).map { it.toDouble() to lon.toDouble() })
    }
    return lines
}

/** Centre of a set of points on the sphere and the largest angle (degrees) from it to any of them. */
internal data class SphericalFrame(val lat: Double, val lon: Double, val spread: Double)

internal fun sphericalFrame(points: List<Pair<Double, Double>>): SphericalFrame? {
    if (points.isEmpty()) return null
    val v = DoubleArray(3)
    var x = 0.0
    var y = 0.0
    var z = 0.0
    val vectors = points.map { (lat, lon) ->
        unitVector(lat, lon, v)
        x += v[0]
        y += v[1]
        z += v[2]
        v.copyOf()
    }
    val length = sqrt(x * x + y * y + z * z)
    // Points spread evenly round the planet have no centre to look at
    if (length < 1e-6) return null
    x /= length
    y /= length
    z /= length
    val spread = vectors.maxOf { acos((it[0] * x + it[1] * y + it[2] * z).coerceIn(-1.0, 1.0)) / DEG }
    return SphericalFrame(asin(z) / DEG, atan2(y, x) / DEG, spread)
}

// ----------------------------------------------------------------------------
// Wind field on the sphere
// ----------------------------------------------------------------------------

/** A spot's wind as east/north components in knots, travelling away from where it blows from. */
internal class GlobeSample(val lat: Double, val lon: Double, val wind: Double, val hasDirection: Boolean, val u: Double, val v: Double) {
    companion object {
        fun of(sample: WindSample): GlobeSample {
            val direction = sample.direction
            if (direction == null) return GlobeSample(sample.lat, sample.lon, sample.wind, false, 0.0, 0.0)
            val rad = ((direction.degrees + 180) % 360) * DEG
            return GlobeSample(sample.lat, sample.lon, sample.wind, true, sin(rad) * sample.wind, cos(rad) * sample.wind)
        }
    }
}

/**
 * The samples bucketed in lat/lon cells one influence radius wide, so a query only
 * looks at the spots that can reach it (`buildFieldIndex`).
 */
internal class FieldIndex(samples: List<GlobeSample>, val radiusDeg: Double, var pxPerDeg: Double, val radiusPx: Double) {
    private val cell = max(radiusDeg, 0.01)
    private val cols = ceil(360 / cell).toInt()
    private val rows = ceil(180 / cell).toInt() + 1
    private val buckets = HashMap<Int, MutableList<GlobeSample>>()

    init {
        for (s in samples) {
            val row = floor((s.lat + 90) / cell).toInt().coerceIn(0, rows - 1)
            val col = floor((normalizeLon(s.lon) + 180) / cell).toInt().coerceIn(0, cols - 1)
            buckets.getOrPut(row * cols + col) { ArrayList() } += s
        }
    }

    /**
     * Interpolate the wind at a point the way the map does at a pixel: inverse
     * distance weighting (power 2, in screen pixels) over every spot within reach,
     * fading toward the edge of the reach (`sampleField`).
     *
     * @param out (magnitude, u, v, washStrength, particleStrength)
     * @return false when no spot reaches the point
     */
    fun sample(lon: Double, lat: Double, out: DoubleArray): Boolean {
        out.fill(0.0, 0, 5)
        val lonN = normalizeLon(lon)
        val row = floor((lat + 90) / cell).toInt()
        val col = floor((lonN + 180) / cell).toInt()
        val cosLat = max(0.05, cos(lat * DEG))
        // A degree of longitude shrinks toward the poles, so the search widens
        val span = min(ceil(1 / cosLat).toInt(), ceil(cols / 2.0).toInt())
        val radiusSq = radiusDeg * radiusDeg
        val pxPerDegSq = pxPerDeg * pxPerDeg

        var weightSum = 0.0
        var magSum = 0.0
        var vectorWeight = 0.0
        var uSum = 0.0
        var vSum = 0.0
        var nearestAll = Double.MAX_VALUE
        var nearestDir = Double.MAX_VALUE

        for (r in row - 1..row + 1) {
            if (r < 0 || r >= rows) continue
            for (c in col - span..col + span) {
                val bucket = buckets[r * cols + ((c % cols) + cols) % cols] ?: continue
                for (s in bucket) {
                    val dLat = s.lat - lat
                    val dLon = normalizeLon(s.lon - lonN) * cosLat
                    val distSq = dLat * dLat + dLon * dLon
                    if (distSq < nearestAll) nearestAll = distSq
                    if (s.hasDirection && distSq < nearestDir) nearestDir = distSq
                    if (distSq > radiusSq) continue
                    val weight = 1 / (distSq * pxPerDegSq + 1)
                    weightSum += weight
                    magSum += weight * s.wind
                    if (s.hasDirection) {
                        vectorWeight += weight
                        uSum += weight * s.u
                        vSum += weight * s.v
                    }
                }
            }
        }
        if (weightSum == 0.0) return false

        val fadeSpan = radiusDeg * (1 - WindField.FADE_FROM)
        out[0] = magSum / weightSum
        out[3] = ((radiusDeg - sqrt(nearestAll)) / fadeSpan).coerceIn(0.0, 1.0)
        if (vectorWeight > 0) {
            out[1] = uSum / vectorWeight
            out[2] = vSum / vectorWeight
            out[4] = ((radiusDeg - sqrt(nearestDir)) / fadeSpan).coerceIn(0.0, 1.0)
        }
        return true
    }

    companion object {
        /** The map's reach in screen pixels at this zoom, turned into degrees on the globe. */
        fun build(samples: List<GlobeSample>, projection: GlobeProjection): FieldIndex {
            val radiusPx = WindField.radius(projection.leafletZoom)
            return FieldIndex(samples, radiusPx / projection.pxPerDeg, projection.pxPerDeg, radiusPx)
        }
    }
}

// ----------------------------------------------------------------------------
// Clusters
// ----------------------------------------------------------------------------

/** A marker on the globe: one spot ([single]) or a bubble of several. */
internal class GlobeCluster(
    val lat: Double,
    val lon: Double,
    val members: List<MapSpotFeature>,
    /** Strongest wind among the members, as the map's cluster bubbles are coloured; null when none has data. */
    val maxWind: Int?,
) {
    val single: MapSpotFeature? = members.singleOrNull()

    /** What the marker shows: a spot's knots or a bubble's count, made once rather than every frame. */
    val label: String? = if (single != null) single.wind?.toString() else members.size.toString()
}

/**
 * Merge spots that sit within the map's cluster radius of each other on screen -
 * greedy, seeded in spot order, like the map - but on the sphere: the radius is an
 * angle, so clusters depend only on the zoom and never shuffle while the globe
 * turns. Past the map's cluster zoom every spot stands on its own.
 */
internal fun clusterSpots(spots: List<MapSpotFeature>, radiusPx: Double, globeRadius: Double, leafletZoom: Double): List<GlobeCluster> {
    if (leafletZoom > GLOBE_CLUSTER_MAX_ZOOM) return spots.map { GlobeCluster(it.lat, it.lon, listOf(it), it.wind) }
    val v = DoubleArray(3)
    val vectors = spots.map {
        unitVector(it.lat, it.lon, v)
        v.copyOf()
    }
    val threshold = cos(min(PI, radiusPx / globeRadius))
    val grouped = BooleanArray(spots.size)
    val clusters = ArrayList<GlobeCluster>()
    for (i in spots.indices) {
        if (grouped[i]) continue
        grouped[i] = true
        val seed = vectors[i]
        val members = arrayListOf(i)
        for (j in i + 1 until spots.size) {
            if (grouped[j]) continue
            val other = vectors[j]
            if (seed[0] * other[0] + seed[1] * other[1] + seed[2] * other[2] >= threshold) {
                grouped[j] = true
                members += j
            }
        }
        if (members.size == 1) {
            clusters += GlobeCluster(spots[i].lat, spots[i].lon, listOf(spots[i]), spots[i].wind)
            continue
        }
        var x = 0.0
        var y = 0.0
        var z = 0.0
        members.forEach {
            x += vectors[it][0]
            y += vectors[it][1]
            z += vectors[it][2]
        }
        val length = sqrt(x * x + y * y + z * z).takeIf { it > 0 } ?: 1.0
        val features = members.map { spots[it] }
        clusters += GlobeCluster(
            lat = asin(z / length) / DEG,
            lon = atan2(y, x) / DEG,
            members = features,
            maxWind = features.mapNotNull { it.wind }.maxOrNull(),
        )
    }
    return clusters
}

/** Leaflet zoom from which every spot is drawn on its own (the web's CLUSTER_MAX_ZOOM). */
internal const val GLOBE_CLUSTER_MAX_ZOOM = 10.0
