package surf.varun.core.domain

import surf.varun.core.model.Direction
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The wind field overlay's maths, ported from the web's `map.js` so both draw the
 * same picture: inverse-distance weighting (power 2) over the spots in screen
 * space, a colour wash by speed, and a velocity field the particles ride on.
 *
 * Everything works in screen pixels; the caller projects spots with the map's
 * camera. Zoom levels are Leaflet ones - MapLibre's 512 px tiles put a MapLibre
 * zoom `z` at Leaflet zoom `z + 1`, see [leafletZoom].
 */
object WindField {
    const val MAX_DIST = 70.0
    const val FADE_FROM = 0.5
    private const val REFERENCE_ZOOM = 8.0
    private const val MAX_GROWTH = 8.0

    const val HEAT_STEP = 8
    const val HEAT_BASE_ALPHA = 0.6f

    const val GRID_STEP = 16
    const val MIN_FIELD = 0.05f
    const val PARTICLE_SPEED = 0.09f
    const val PARTICLE_LIFE = 60
    const val PARTICLE_TRAIL_FADE = 0.12f
    const val PARTICLES_PER_SPOT = 18
    const val PARTICLES_MIN = 40
    const val PARTICLES_MAX = 1400
    private const val PARTICLE_LIGHTEN = 0.65

    private val GRAY = intArrayOf(100, 116, 139)
    private val STOPS = listOf(
        5.0 to intArrayOf(100, 116, 139),
        8.0 to intArrayOf(59, 130, 246),
        11.0 to intArrayOf(34, 211, 238),
        12.0 to intArrayOf(46, 230, 109),
        18.0 to intArrayOf(245, 158, 11),
        25.0 to intArrayOf(245, 72, 74),
        35.0 to intArrayOf(176, 20, 42),
    )

    fun leafletZoom(mapLibreZoom: Double): Double = mapLibreZoom + 1

    /** Influence radius of one spot in pixels at a Leaflet zoom (`windFieldRadius`). */
    fun radius(leafletZoom: Double): Double {
        if (leafletZoom.isNaN() || leafletZoom <= REFERENCE_ZOOM) return MAX_DIST
        return MAX_DIST * min(MAX_GROWTH, 2.0.pow(leafletZoom - REFERENCE_ZOOM))
    }

    /** `windFieldColor`: 0xRRGGBB for a speed in knots. */
    fun color(knots: Double): Int {
        if (knots < STOPS.first().first) return rgb(GRAY)
        if (knots >= STOPS.last().first) return rgb(STOPS.last().second)
        for (i in 1 until STOPS.size) {
            val (bKt, b) = STOPS[i]
            if (knots <= bKt) {
                val (aKt, a) = STOPS[i - 1]
                val f = (knots - aKt) / (bKt - aKt)
                return rgb(IntArray(3) { (a[it] + (b[it] - a[it]) * f).roundToInt() })
            }
        }
        return rgb(STOPS.last().second)
    }

    /** `windParticleColor`: the field colour pushed toward white. */
    fun particleColor(knots: Double): Int {
        val c = color(knots)
        val channels = intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        return rgb(IntArray(3) { (channels[it] + (255 - channels[it]) * PARTICLE_LIGHTEN).roundToInt() })
    }

    private fun rgb(c: IntArray) = (c[0] shl 16) or (c[1] shl 8) or c[2]

    /** A spot projected to the screen. */
    class ScreenSample(val x: Float, val y: Float, val wind: Float, val direction: Direction?)

    /**
     * Colour wash: one cell per [HEAT_STEP] px, ARGB (0 = transparent), row-major.
     * Same blend as `createWindHeatLayer`: averaged magnitude, faded to nothing
     * past [FADE_FROM] of the radius from the nearest spot.
     */
    fun heatCells(samples: List<ScreenSample>, width: Int, height: Int, radius: Double, step: Int = HEAT_STEP): IntArray {
        val cols = (width + step - 1) / step
        val rows = (height + step - 1) / step
        val out = IntArray(cols * rows)
        val maxSq = radius * radius
        val fadeFrom = radius * FADE_FROM
        val pts = samples.filter {
            it.x > -radius && it.x < width + radius && it.y > -radius && it.y < height + radius
        }
        if (pts.isEmpty()) return out
        for (row in 0 until rows) {
            val py = row * step + step / 2f
            for (col in 0 until cols) {
                val px = col * step + step / 2f
                var wsum = 0.0
                var vsum = 0.0
                var nearestSq = Double.MAX_VALUE
                for (p in pts) {
                    val dx = p.x - px
                    val dy = p.y - py
                    val dSq = (dx * dx + dy * dy).toDouble()
                    if (dSq < nearestSq) nearestSq = dSq
                    if (dSq > maxSq) continue
                    val w = 1 / (dSq + 1)
                    wsum += w
                    vsum += w * p.wind
                }
                if (wsum == 0.0) continue
                val edge = ((radius - sqrt(nearestSq)) / (radius - fadeFrom)).coerceIn(0.0, 1.0)
                val alpha = HEAT_BASE_ALPHA * edge
                if (alpha <= 0.01) continue
                out[row * cols + col] = ((alpha * 255).roundToInt() shl 24) or color(vsum / wsum)
            }
        }
        return out
    }

    /** Interpolated velocity field (`buildWindField`). */
    class VectorField(val cols: Int, val rows: Int, val u: FloatArray, val v: FloatArray, val strength: FloatArray) {
        /** Bilinear sample into [out] = (u, v, strength); false outside the field. */
        fun sample(x: Float, y: Float, out: FloatArray): Boolean {
            val gx = x / GRID_STEP
            val gy = y / GRID_STEP
            val col = gx.toInt()
            val row = gy.toInt()
            if (gx < 0 || gy < 0 || col + 1 >= cols || row + 1 >= rows) return false
            val fx = gx - col
            val fy = gy - row
            val i00 = row * cols + col
            val i10 = i00 + 1
            val i01 = i00 + cols
            val i11 = i01 + 1
            val w00 = (1 - fx) * (1 - fy)
            val w10 = fx * (1 - fy)
            val w01 = (1 - fx) * fy
            val w11 = fx * fy
            out[0] = u[i00] * w00 + u[i10] * w10 + u[i01] * w01 + u[i11] * w11
            out[1] = v[i00] * w00 + v[i10] * w10 + v[i01] * w01 + v[i11] * w11
            out[2] = strength[i00] * w00 + strength[i10] * w10 + strength[i01] * w01 + strength[i11] * w11
            return true
        }
    }

    /** A spot's wind as a screen-space velocity: travelling *away* from where it blows from. */
    class VelocityPoint(val x: Float, val y: Float, val u: Float, val v: Float)

    fun velocityPoints(samples: List<ScreenSample>, width: Int, height: Int, radius: Double): List<VelocityPoint> =
        samples.mapNotNull { s ->
            val direction = s.direction ?: return@mapNotNull null
            if (s.x < -radius || s.x > width + radius || s.y < -radius || s.y > height + radius) return@mapNotNull null
            val rad = Math.toRadians(((direction.degrees + 180) % 360).toDouble())
            VelocityPoint(s.x, s.y, (sin(rad) * s.wind).toFloat(), (-cos(rad) * s.wind).toFloat())
        }

    fun vectorField(points: List<VelocityPoint>, width: Int, height: Int, radius: Double): VectorField {
        val cols = (width + GRID_STEP - 1) / GRID_STEP + 1
        val rows = (height + GRID_STEP - 1) / GRID_STEP + 1
        val u = FloatArray(cols * rows)
        val v = FloatArray(cols * rows)
        val strength = FloatArray(cols * rows)
        val maxSq = radius * radius
        val fadeSpan = radius * (1 - FADE_FROM)
        for (row in 0 until rows) {
            val py = (row * GRID_STEP).toFloat()
            for (col in 0 until cols) {
                val px = (col * GRID_STEP).toFloat()
                var wsum = 0.0
                var usum = 0.0
                var vsum = 0.0
                var nearestSq = Double.MAX_VALUE
                for (p in points) {
                    val dx = p.x - px
                    val dy = p.y - py
                    val dSq = (dx * dx + dy * dy).toDouble()
                    if (dSq < nearestSq) nearestSq = dSq
                    if (dSq > maxSq) continue
                    val w = 1 / (dSq + 1)
                    wsum += w
                    usum += w * p.u
                    vsum += w * p.v
                }
                if (wsum == 0.0) continue
                val i = row * cols + col
                u[i] = (usum / wsum).toFloat()
                v[i] = (vsum / wsum).toFloat()
                strength[i] = ((radius - sqrt(nearestSq)) / fadeSpan).coerceIn(0.0, 1.0).toFloat()
            }
        }
        return VectorField(cols, rows, u, v, strength)
    }

    fun particleBudget(spotsInView: Int, qualityScale: Float = 1f): Int =
        (max(PARTICLES_MIN, min(PARTICLES_MAX, spotsInView * PARTICLES_PER_SPOT)) * qualityScale).toInt()
}
