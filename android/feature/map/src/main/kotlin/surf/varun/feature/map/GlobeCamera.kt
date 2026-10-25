package surf.varun.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Where the globe looks and how it gets there: drag, pinch, fling, fly-to and the
 * idle spin, with the web globe's tuning. The zoom is kept as the equivalent
 * Leaflet zoom ([equivalentZoom]) - a MapLibre zoom `z` is Leaflet `z + 1` - so the
 * map hands its camera over and takes it back without a jump.
 *
 * Plain state, driven by the screen's frame loop; [version] changes with every
 * move, which is what tells the renderer to re-project.
 */
internal class GlobeCamera(lat: Double, lon: Double, leafletZoom: Double) {
    var lat = lat.coerceIn(-MAX_TILT, MAX_TILT)
        private set
    var lon = normalizeLon(lon)
        private set
    var leafletZoom = leafletZoom
        private set

    /** Bumped on every change of the view, observable from composition. */
    var version by mutableIntStateOf(0)
        private set

    /** The area the globe is centred in, in dp: the screen above the bottom sheet's peek. */
    private var width = 0.0
    private var height = 0.0

    var reduceMotion = false

    private var fly: Fly? = null
    private var inertiaLon = 0.0
    private var inertiaLat = 0.0
    private var lastInteractionMs = 0L

    private class Fly(
        val start: Long,
        val fromLat: Double,
        val fromLon: Double,
        val fromZoom: Double,
        val toLat: Double,
        val toZoom: Double,
        val dLon: Double,
    )

    val hasSize: Boolean get() = width > 0 && height > 0

    val animating: Boolean get() = fly != null || inertiaLon != 0.0 || inertiaLat != 0.0

    fun setSize(widthDp: Double, heightDp: Double) {
        if (widthDp == width && heightDp == height) return
        width = widthDp
        height = heightDp
        leafletZoom = leafletZoom.coerceIn(minZoom(), MAX_ZOOM)
        changed()
    }

    /** Globe radius at zoom 1: a fraction of the shorter side, like the web. */
    fun baseRadius(): Double = max(1.0, min(width, height) * BASE_RADIUS_FRACTION)

    /** The whole planet in view, with room round it. */
    fun wholeGlobeZoom(): Double = equivalentZoom(baseRadius())

    private fun minZoom(): Double = equivalentZoom(baseRadius() * MIN_ZOOM_FRACTION)

    private val radius: Double get() = radiusForZoom(leafletZoom)

    /** How far in from the whole-planet view; the spin slows down by it. */
    private val zoomFactor: Double get() = radius / baseRadius()

    fun projection(): GlobeProjection = GlobeProjection(width / 2, height / 2, radius, lat, lon)

    // ---- Gestures ----------------------------------------------------------

    /** Turn the globe under a finger moved by ([dx], [dy]) dp. */
    fun pan(dx: Double, dy: Double, nowMs: Long) {
        stop(nowMs)
        val degPerPx = 1 / (radius * DEG)
        lon = normalizeLon(lon - dx * degPerPx / max(0.2, kotlin.math.cos(lat * DEG)))
        lat = (lat + dy * degPerPx).coerceIn(-MAX_TILT, MAX_TILT)
        changed()
    }

    /** Zoom by [levels] map zoom levels (pinch: log2 of the scale). */
    fun zoomBy(levels: Double, nowMs: Long) {
        stop(nowMs)
        setZoom(leafletZoom + levels)
    }

    /** A drag released with speed, in dp per second, keeps turning the globe. */
    fun fling(vxDp: Double, vyDp: Double, nowMs: Long) {
        lastInteractionMs = nowMs
        if (reduceMotion) return
        val degPerPx = 1 / (radius * DEG)
        inertiaLon = -vxDp / FRAMES_PER_SECOND * degPerPx / max(0.2, kotlin.math.cos(lat * DEG))
        inertiaLat = vyDp / FRAMES_PER_SECOND * degPerPx
    }

    /** Look at a point at once, without flying - the map's camera handed over. */
    fun jumpTo(lat: Double, lon: Double, leafletZoom: Double) {
        stop(0)
        this.lat = lat.coerceIn(-MAX_TILT, MAX_TILT)
        this.lon = normalizeLon(lon)
        this.leafletZoom = if (hasSize) leafletZoom.coerceIn(minZoom(), MAX_ZOOM) else leafletZoom
        changed()
    }

    /** Set when the screen switches over from the map: the first sight flies out to the whole planet. */
    var introPending = false

    /** Once there is a size: from whatever the map was looking at, out to the whole planet. */
    fun introduce(nowMs: Long) {
        if (!introPending || !hasSize) return
        introPending = false
        if (leafletZoom > wholeGlobeZoom()) flyTo(lat, lon, wholeGlobeZoom(), nowMs)
    }

    /** A touch holds everything still and resets the idle spin's wait. */
    fun stop(nowMs: Long) {
        fly = null
        inertiaLon = 0.0
        inertiaLat = 0.0
        lastInteractionMs = nowMs
    }

    private fun setZoom(next: Double) {
        val clamped = next.coerceIn(minZoom(), MAX_ZOOM)
        if (clamped != leafletZoom) {
            leafletZoom = clamped
            changed()
        }
    }

    // ---- Framing -----------------------------------------------------------

    fun flyTo(lat: Double, lon: Double, leafletZoom: Double, nowMs: Long) {
        inertiaLon = 0.0
        inertiaLat = 0.0
        lastInteractionMs = nowMs
        val toLat = lat.coerceIn(-MAX_TILT, MAX_TILT)
        val toLon = normalizeLon(lon)
        val toZoom = leafletZoom.coerceIn(minZoom(), MAX_ZOOM)
        if (reduceMotion || !hasSize) {
            fly = null
            this.lat = toLat
            this.lon = toLon
            this.leafletZoom = toZoom
            changed()
            return
        }
        // Turn the short way round
        fly = Fly(nowMs, this.lat, this.lon, this.leafletZoom, toLat, toZoom, normalizeLon(toLon - this.lon))
    }

    /** Back to the whole planet the globe opens on, zooming out as far as it takes. */
    fun reset(nowMs: Long) = flyTo(DEFAULT_LAT, DEFAULT_LON, wholeGlobeZoom(), nowMs)

    /** Fly to a point keeping the zoom, or to [leafletZoom] when closer than now. */
    fun centreOn(lat: Double, lon: Double, leafletZoom: Double, nowMs: Long) =
        flyTo(lat, lon, max(this.leafletZoom, leafletZoom), nowMs)

    /**
     * Fly to a set of points: their centre, zoomed in until the furthest one sits
     * [paddingDp] from the edge of the shorter side - the globe's fitBounds.
     * Points spread over too much of the planet get the whole globe; points on
     * one spot (no extent to fit) get [coincidentZoom] when given. Never closer
     * than [maxLeafletZoom], never further out than [minLeafletZoom] - a tapped
     * bubble always opens up, however far its spots spread.
     */
    fun fit(
        points: List<Pair<Double, Double>>,
        paddingDp: Double,
        maxLeafletZoom: Double,
        nowMs: Long,
        coincidentZoom: Double? = null,
        minLeafletZoom: Double = Double.NEGATIVE_INFINITY,
    ) {
        val frame = sphericalFrame(points)
        when {
            frame == null -> flyTo(DEFAULT_LAT, DEFAULT_LON, wholeGlobeZoom(), nowMs)
            frame.spread > WHOLE_GLOBE_SPREAD_DEG -> flyTo(frame.lat, frame.lon, max(wholeGlobeZoom(), minLeafletZoom), nowMs)
            frame.spread < 1e-4 && coincidentZoom != null -> flyTo(frame.lat, frame.lon, coincidentZoom, nowMs)
            else -> {
                val available = max(40.0, min(width, height) / 2 - paddingDp)
                val spread = max(frame.spread, 1e-3) * DEG
                val fitRadius = available / sin(min(spread, PI / 2))
                val zoom = equivalentZoom(fitRadius.coerceIn(baseRadius(), radiusForZoom(maxLeafletZoom)))
                flyTo(frame.lat, frame.lon, max(zoom, minLeafletZoom), nowMs)
            }
        }
    }

    // ---- Frame -------------------------------------------------------------

    /**
     * Advance the fly, the inertia or the idle spin by one frame.
     * @param frames frame time in 60 fps frames
     * @param spin whether the idle spin may run (setting on, nothing selected)
     * @return true while the globe moves on its own (fly or inertia), when the
     *   particle trails are cut short
     */
    fun tick(nowMs: Long, elapsedMs: Double, frames: Double, spin: Boolean): Boolean {
        val flight = fly
        if (flight != null) {
            val t = ((nowMs - flight.start) / FLY_DURATION_MS).coerceIn(0.0, 1.0)
            val e = easeInOutCubic(t)
            lon = normalizeLon(flight.fromLon + flight.dLon * e)
            lat = flight.fromLat + (flight.toLat - flight.fromLat) * e
            // Linear in the Leaflet zoom is log space in the radius, so a fly from the
            // whole planet to one bay doesn't spend the trip at the far end, and lifts
            // a little mid-flight on a long hop, the way a camera would
            val hop = min(1.0, abs(flight.dLon) / 120 + abs(flight.toLat - flight.fromLat) / 90)
            val lift = sin(PI * e) * hop * 0.35
            leafletZoom = max(minZoom(), flight.fromZoom + (flight.toZoom - flight.fromZoom) * e + ln(1 - lift) / ln(2.0))
            if (t >= 1) {
                fly = null
                lastInteractionMs = nowMs
            }
            changed()
            return true
        }
        if (inertiaLon != 0.0 || inertiaLat != 0.0) {
            lon = normalizeLon(lon + inertiaLon * frames)
            lat = (lat + inertiaLat * frames).coerceIn(-MAX_TILT, MAX_TILT)
            val decay = INERTIA_DECAY.pow(frames)
            inertiaLon *= decay
            inertiaLat *= decay
            val zoom = zoomFactor
            if (abs(inertiaLon) < 0.005 / zoom && abs(inertiaLat) < 0.005 / zoom) {
                inertiaLon = 0.0
                inertiaLat = 0.0
                lastInteractionMs = nowMs
            }
            changed()
            return true
        }
        if (spin && !reduceMotion && nowMs - lastInteractionMs > AUTO_ROTATE_RESUME_MS) {
            lon = normalizeLon(lon + AUTO_ROTATE_DEG_PER_SEC * (elapsedMs / 1000) / zoomFactor)
            changed()
        }
        return false
    }

    private fun changed() {
        version++
    }

    companion object {
        /** The view never tips far enough to put a pole in the middle: the rotation gets twitchy there. */
        const val MAX_TILT = 75.0

        /** As far in as the map's cluster zoom plus a few levels, so every cluster opens up. */
        const val MAX_ZOOM = 13.0

        /** Framing a cluster: 40 dp padding, two levels past the cluster zoom. */
        const val CLUSTER_FIT_PADDING = 40.0
        const val CLUSTER_FIT_MAX_ZOOM = GLOBE_CLUSTER_MAX_ZOOM + 2

        /** Framing a filter (country, favourites). */
        const val FILTER_FIT_PADDING = 60.0
        const val FILTER_FIT_MAX_ZOOM = 9.0

        private const val BASE_RADIUS_FRACTION = 0.42
        private const val MIN_ZOOM_FRACTION = 0.8
        private const val WHOLE_GLOBE_SPREAD_DEG = 70.0

        /** Degrees of longitude per second at zoom 1 - a turn in about 2.5 min. */
        private const val AUTO_ROTATE_DEG_PER_SEC = 2.4
        private const val AUTO_ROTATE_RESUME_MS = 4000L
        private const val INERTIA_DECAY = 0.92
        private const val FLY_DURATION_MS = 1200.0
        private const val FRAMES_PER_SECOND = 60.0

        /** Where the globe looks when there is nothing better: Europe, where most spots are. */
        const val DEFAULT_LAT = 35.0
        const val DEFAULT_LON = 10.0

        private fun easeInOutCubic(t: Double): Double =
            if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }
}
