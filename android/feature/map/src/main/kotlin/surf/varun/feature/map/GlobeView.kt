package surf.varun.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.designsystem.theme.WindColors
import surf.varun.core.domain.WindClass
import surf.varun.core.domain.WindField
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The map's spots and wind field on a rotating planet (the web's `/globe`, see
 * `common/globe.js`): the sphere, land and borders, the interpolated wind wash,
 * particles riding the field, and the spots as the map's dots and cluster
 * bubbles. Drag to turn, pinch or double-tap to zoom, tap a dot to open the spot
 * and a bubble to zoom in on it; left alone it spins slowly.
 *
 * Everything is worked out in dp, which is what the web's CSS pixels are, so the
 * map constants (wind reach, particle speed, cluster radius) read the same here.
 */
@Composable
internal fun GlobeView(
    camera: GlobeCamera,
    features: List<MapSpotFeature>,
    samples: List<WindSample>,
    selectedWgId: Int?,
    windVisible: Boolean,
    markersVisible: Boolean,
    particleMode: ParticleMode,
    particleScale: Float,
    /** The idle spin is on (setting, not held back by battery saver or reduced motion). */
    spin: Boolean,
    /** What the bottom sheet covers; the globe is centred in the area above it. */
    bottomInset: Dp,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val insetDp = bottomInset.value.toDouble()
    val dark = VarunTheme.isDark
    val windColors = VarunTheme.wind
    val renderer = remember { GlobeRenderer() }
    val currentOnSelect by rememberUpdatedState(onSelect)
    var frame by remember { mutableIntStateOf(0) }
    // A finger on the globe: frames follow it at the display's full rate
    val touch = remember { BooleanArray(1) }

    var coarse by remember { mutableStateOf<GlobeOutlines?>(null) }
    var detailed by remember { mutableStateOf<GlobeOutlines?>(null) }
    LaunchedEffect(Unit) { coarse = GlobeOutlines.load(context, GlobeOutlines.COARSE) }
    val wantDetailed by remember { derivedStateOf { camera.version >= 0 && camera.leafletZoom >= DETAILED_OUTLINES_FROM_ZOOM } }
    LaunchedEffect(wantDetailed) {
        if (wantDetailed && detailed == null) detailed = GlobeOutlines.load(context, GlobeOutlines.DETAILED)
    }

    // Spins only while nothing is open: a selected spot holds the globe still under its sheet
    val spinning = spin && selectedWgId == null
    SideEffect {
        renderer.configure(
            outlines = GlobeRenderer.Outlines(coarse, detailed),
            features = features,
            samples = samples,
            selectedWgId = selectedWgId,
            windVisible = windVisible,
            markersVisible = markersVisible,
            particleMode = particleMode,
            particleScale = particleScale,
            palette = if (dark) GlobePalette.DARK else GlobePalette.LIGHT,
            windColors = windColors,
            density = density,
        )
    }

    LaunchedEffect(renderer, camera, spinning) {
        var last = 0L
        while (isActive) {
            if (!renderer.needsFrames(camera, spinning)) {
                // Nothing moves on its own: sleep until the camera or the data changes
                val seen = camera.version to renderer.inputs
                snapshotFlow { camera.version to renderer.inputs }.first { it != seen }
                last = 0L
            }
            withFrameNanos { now ->
                // Left to itself (spin, particles) a 90/120 Hz display would rebuild the globe
                // twice as often for nothing; a drag, fling or flight keeps the full rate
                val paced = !touch[0] && !camera.animating
                if (paced && last != 0L && now - last < MIN_IDLE_STEP_NANOS) return@withFrameNanos
                val elapsedMs = if (last == 0L) 16.67 else (now - last) / 1_000_000.0
                last = now
                if (renderer.update(camera, now / 1_000_000, elapsedMs, spinning)) frame++
            }
        }
    }

    val label = stringResource(R.string.map_globe)
    Canvas(
        modifier
            .semantics { contentDescription = label }
            .onSizeChanged { size ->
                renderer.resize(size.width, size.height)
                camera.setSize(size.width / density.toDouble(), size.height / density.toDouble() - insetDp)
                camera.introduce(nowMs())
            }
            .pointerInput(camera, density) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touch[0] = true
                    try {
                        camera.stop(nowMs())
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var travelled = Offset.Zero
                        var dragging = false
                        var pointers = 1
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (!dragging) {
                                travelled += pan
                                dragging = travelled.getDistance() > viewConfiguration.touchSlop || abs(zoom - 1f) > 0.02f
                            }
                            if (!dragging) continue
                            if (pressed.size != pointers) {
                                // The centroid jumps when a finger joins or leaves: start the fling afresh
                                tracker.resetTracking()
                                pointers = pressed.size
                            }
                            camera.pan(pan.x / density.toDouble(), pan.y / density.toDouble(), nowMs())
                            if (zoom != 1f) camera.zoomBy(ln(zoom.toDouble()) / ln(2.0), nowMs())
                            tracker.addPosition(pressed.first().uptimeMillis, event.calculateCentroid())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (dragging && pointers == 1) {
                            val velocity = tracker.calculateVelocity()
                            camera.fling(velocity.x / density.toDouble(), velocity.y / density.toDouble(), nowMs())
                        }
                    } finally {
                        touch[0] = false
                    }
                }
            }
            .pointerInput(camera, density) {
                detectTapGestures(
                    onDoubleTap = { camera.zoomBy(1.0, nowMs()) },
                    onTap = { offset ->
                        val cluster = renderer.targetAt(offset.x / density, offset.y / density)
                        val single = cluster?.single
                        when {
                            cluster == null -> currentOnSelect(null)
                            single != null -> currentOnSelect(single.wgId)
                            else -> camera.fit(
                                cluster.members.map { it.lat to it.lon },
                                GlobeCamera.CLUSTER_FIT_PADDING,
                                GlobeCamera.CLUSTER_FIT_MAX_ZOOM,
                                nowMs(),
                                // Spots sharing one position have no extent to fit: two levels in
                                coincidentZoom = camera.leafletZoom + 2,
                                minLeafletZoom = camera.leafletZoom + 1,
                            )
                        }
                    },
                )
            },
    ) {
        // Reading the frame counter is what redraws the globe after every update
        if (frame >= 0) drawIntoCanvas { renderer.draw(it.nativeCanvas) }
    }
}

private fun nowMs(): Long = System.nanoTime() / 1_000_000

/** Shorter than a 60 Hz frame with room for jitter, longer than a 120 Hz one. */
private const val MIN_IDLE_STEP_NANOS = 10_000_000L

/** From this equivalent zoom on the 1:110m coastlines look blocky and the 1:50m set is used. */
private const val DETAILED_OUTLINES_FROM_ZOOM = 3.5

/** The web globe's two palettes; the sphere follows the app theme, the markers keep the wind colours. */
internal enum class GlobePalette(
    val oceanInner: Int,
    val oceanOuter: Int,
    val land: Int,
    val landStroke: Int,
    val borders: Int,
    val graticule: Int,
    val atmosphere: Int,
    val limb: Int,
    val highlight: Int,
) {
    DARK(
        oceanInner = 0xFF123049.toInt(),
        oceanOuter = 0xFF07121D.toInt(),
        land = 0xFF26323B.toInt(),
        landStroke = 0x1AFFFFFF,
        borders = 0x1AFFFFFF,
        graticule = 0x0DFFFFFF,
        atmosphere = 0x22C3E6,
        limb = 0x73000000,
        highlight = 0x0FFFFFFF,
    ),
    LIGHT(
        oceanInner = 0xFFD6ECF6.toInt(),
        oceanOuter = 0xFFA9D1E4.toInt(),
        land = 0xFFF5F2EA.toInt(),
        landStroke = 0x1F000000,
        borders = 0x1F000000,
        graticule = 0x0F000000,
        atmosphere = 0x0891B2,
        limb = 0x2E0F283C,
        highlight = 0x59FFFFFF,
    ),
}

/**
 * Draws the globe and keeps its derived state: clipped outlines, the wash, the
 * clusters and the particles. Rebuilt when the camera moves; the particles are
 * advanced every frame on a trail bitmap, as on the map.
 */
internal class GlobeRenderer(private val random: Random = Random.Default) {
    class Outlines(val coarse: GlobeOutlines?, val detailed: GlobeOutlines?)

    // ---- Inputs ------------------------------------------------------------

    private var outlines = Outlines(null, null)
    private var features: List<MapSpotFeature> = emptyList()
    private var samples: List<GlobeSample> = emptyList()
    private var sampleSource: List<WindSample>? = null
    private var selectedWgId: Int? = null
    private var windVisible = true
    private var markersVisible = true
    private var particleMode = ParticleMode.ANIMATED
    private var particleScale = 1f
    private var palette = GlobePalette.DARK
    private var windColors: WindColors? = null
    private var density = 1f

    /** Bumped when an input changes, so an idle frame loop wakes up. */
    var inputs by mutableIntStateOf(0)
        private set

    fun configure(
        outlines: Outlines,
        features: List<MapSpotFeature>,
        samples: List<WindSample>,
        selectedWgId: Int?,
        windVisible: Boolean,
        markersVisible: Boolean,
        particleMode: ParticleMode,
        particleScale: Float,
        palette: GlobePalette,
        windColors: WindColors,
        density: Float,
    ) {
        var changed = false
        if (outlines.coarse !== this.outlines.coarse || outlines.detailed !== this.outlines.detailed) {
            this.outlines = outlines
            changed = true
        }
        if (features != this.features) {
            this.features = features
            clusterZoom = Double.NaN
            changed = true
        }
        if (samples !== sampleSource) {
            sampleSource = samples
            this.samples = samples.map(GlobeSample::of)
            fieldIndex = null
            // Particles were spawned round the old samples: start them afresh
            particleCount = 0
            clearTrails = true
            changed = true
        }
        if (selectedWgId != this.selectedWgId || markersVisible != this.markersVisible) {
            this.selectedWgId = selectedWgId
            this.markersVisible = markersVisible
            changed = true
        }
        if (windVisible != this.windVisible || particleMode != this.particleMode || particleScale != this.particleScale) {
            this.windVisible = windVisible
            this.particleMode = particleMode
            this.particleScale = particleScale
            particleCount = 0
            clearTrails = true
            changed = true
        }
        if (palette != this.palette || windColors != this.windColors || density != this.density) {
            val densityChanged = density != this.density
            this.palette = palette
            this.windColors = windColors
            this.density = density
            this.markerColors = MarkerColors.of(windColors)
            shadersFor = null
            if (densityChanged && widthPx > 0) allocateTrails()
            changed = true
        }
        if (changed) {
            dirty = true
            inputs++
        }
    }

    // ---- View state --------------------------------------------------------

    private var widthPx = 0
    private var heightPx = 0
    private val widthDp: Double get() = widthPx / density.toDouble()
    private val heightDp: Double get() = heightPx / density.toDouble()

    /** Pixels per dp of the trail bitmap, capped like the map's (see [trailDensity]). */
    private var trailScale = 1f
    private var dirty = true
    private var drawnVersion = -1
    private var projection: GlobeProjection? = null

    private val clipper = HorizonClipper()
    private val landPath = Path()
    private val bordersPath = Path()
    private val graticulePath = Path()
    private val graticule = graticuleLines()
    private val graticuleCaps = HorizonClipper.caps(graticule)
    private var hasOutlines = false

    private var fieldIndex: FieldIndex? = null
    private var wash: Bitmap? = null
    private var washPixels = IntArray(0)
    private val washRect = RectF()
    private val sampleOut = DoubleArray(5)
    private val point = DoubleArray(3)
    private val latLon = DoubleArray(2)

    private var clusters: List<GlobeCluster> = emptyList()
    private var clusterZoom = Double.NaN

    private class Target(val x: Float, val y: Float, val radius: Float, val cluster: GlobeCluster)

    private val targets = ArrayList<Target>()

    fun resize(width: Int, height: Int) {
        if (width == widthPx && height == heightPx) return
        widthPx = width
        heightPx = height
        allocateTrails()
        dirty = true
    }

    private fun allocateTrails() {
        trailScale = trailDensity(density)
        val width = (widthDp * trailScale).toInt()
        val height = (heightDp * trailScale).toInt()
        if (trails?.width == width && trails?.height == height) return
        trails?.recycle()
        trails = if (width > 0 && height > 0) Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) else null
        trailsCanvas = trails?.let(::Canvas)
        clearTrails = true
    }


    fun needsFrames(camera: GlobeCamera, spin: Boolean): Boolean =
        dirty || camera.version != drawnVersion || camera.animating || spin ||
            (windVisible && particleMode == ParticleMode.ANIMATED && particleCount > 0)

    /**
     * One frame: move the camera, rebuild what the move made stale, advance the
     * particles. Returns whether anything needs drawing.
     */
    fun update(camera: GlobeCamera, nowMs: Long, elapsedMs: Double, spin: Boolean): Boolean {
        if (widthPx == 0 || !camera.hasSize) return false
        // Frame-rate independent, clamped so a stalled frame doesn't teleport the particles
        val frames = elapsedMs.coerceIn(8.0, 50.0) / 16.67
        val moving = camera.tick(nowMs, elapsedMs, frames, spin)
        var drew = false
        if (dirty || camera.version != drawnVersion) {
            dirty = false
            drawnVersion = camera.version
            rebuild(camera.projection())
            drew = true
        }
        val trailCanvas = trailsCanvas
        if (clearTrails && trailCanvas != null) {
            trailCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
            clearTrails = false
        }
        if (windVisible && particleMode == ParticleMode.ANIMATED && trailCanvas != null) {
            if (elapsedMs > FRAME_BUDGET_MS) slowFrames++ else slowFrames = max(0, slowFrames - 1)
            if (slowFrames > SLOW_FRAMES_BEFORE_SHEDDING) {
                // Sustained long frames shed particles (spec 6.3)
                shed = max(0.25f, shed * 0.75f)
                slowFrames = 0
            }
            stepParticles(trailCanvas, frames, fadeFast = moving, allowRespawn = true)
            drew = true
        }
        return drew
    }

    private fun rebuild(p: GlobeProjection) {
        projection = p
        val detailed = outlines.detailed
        val set = if (p.leafletZoom >= DETAILED_OUTLINES_FROM_ZOOM && detailed != null) detailed else outlines.coarse

        landPath.rewind()
        bordersPath.rewind()
        graticulePath.rewind()
        hasOutlines = set != null
        clipper.lines(graticule, p, PathSink(graticulePath), minStep = 1f, caps = graticuleCaps, width = widthDp, height = heightDp)
        if (set != null) {
            clipper.polygons(set.land, p, PathSink(landPath), caps = set.landCaps, width = widthDp, height = heightDp)
            clipper.lines(set.borders, p, PathSink(bordersPath), caps = set.borderCaps, width = widthDp, height = heightDp)
        }

        updateFieldIndex(p)
        if (clusterZoom.isNaN() || abs(clusterZoom - p.leafletZoom) > 0.05) {
            clusterZoom = p.leafletZoom
            clusters = clusterSpots(features, CLUSTER_RADIUS_DP, p.r, p.leafletZoom)
        }
        if (windVisible && fieldIndex != null) buildWash(p) else wash = null
        shaders(p)
        layoutMarkers(p)
        updateVisibleSamples(p)
        fillParticles()
        if (windVisible && particleMode == ParticleMode.STATIC) drawStaticParticles()
    }

    // ---- Wind field --------------------------------------------------------

    /** Rebuilt only when the reach really changed, which keeps a slow zoom from rebuilding it every frame. */
    private fun updateFieldIndex(p: GlobeProjection) {
        if (samples.isEmpty()) {
            fieldIndex = null
            return
        }
        val current = fieldIndex
        val radiusDeg = WindField.radius(p.leafletZoom) / p.pxPerDeg
        if (current != null && abs(current.radiusDeg - radiusDeg) / radiusDeg < 0.03) {
            current.pxPerDeg = p.pxPerDeg
            return
        }
        fieldIndex = FieldIndex.build(samples, p)
    }

    /** The wash: the projection inverted every few dp, the field interpolated there and stretched over the globe. */
    private fun buildWash(p: GlobeProjection) {
        val index = fieldIndex ?: return
        val left = max(0.0, floor(p.cx - p.r))
        val top = max(0.0, floor(p.cy - p.r))
        val right = min(widthDp, ceil(p.cx + p.r))
        val bottom = min(heightDp, ceil(p.cy + p.r))
        val cols = ceil((right - left) / WASH_STEP).toInt()
        val rows = ceil((bottom - top) / WASH_STEP).toInt()
        if (cols <= 0 || rows <= 0) {
            wash = null
            return
        }
        if (washPixels.size != cols * rows) washPixels = IntArray(cols * rows) else washPixels.fill(0)
        for (row in 0 until rows) {
            val y = top + (row + 0.5) * WASH_STEP
            for (col in 0 until cols) {
                val x = left + (col + 0.5) * WASH_STEP
                if (!p.unproject(x, y, latLon)) continue
                if (!index.sample(latLon[1], latLon[0], sampleOut)) continue
                val alpha = WindField.HEAT_BASE_ALPHA * sampleOut[3]
                if (alpha <= 0.01) continue
                washPixels[row * cols + col] = ((alpha * 255).roundToInt() shl 24) or WindField.colorCached(sampleOut[0])
            }
        }
        val bitmap = wash?.takeIf { it.width == cols && it.height == rows }
            ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(washPixels, 0, cols, 0, 0, cols, rows)
        wash = bitmap
        washRect.set(left.toFloat(), top.toFloat(), (left + cols * WASH_STEP).toFloat(), (top + rows * WASH_STEP).toFloat())
    }

    // ---- Particles ---------------------------------------------------------

    private var trails: Bitmap? = null
    private var trailsCanvas: Canvas? = null
    private var clearTrails = false
    private var visibleSamples: List<GlobeSample> = emptyList()
    private var particleCount = 0
    private var pLat = DoubleArray(0)
    private var pLon = DoubleArray(0)
    private var pAge = IntArray(0)
    private var pDead = BooleanArray(0)
    private var slowFrames = 0
    private var shed = 1f
    private val from = DoubleArray(3)
    private val to = DoubleArray(3)

    private val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    /** The spots with a bearing that can reach the screen - the map's "points in view". */
    private fun updateVisibleSamples(p: GlobeProjection) {
        val margin = fieldIndex?.radiusPx ?: WindField.MAX_DIST
        visibleSamples = samples.filter {
            it.hasDirection && p.project(it.lat, it.lon, point) && p.onScreen(point[0], point[1], widthDp, heightDp, margin)
        }
    }

    /** The map's budget: per spot in view, scaled by how far a spot reaches. */
    private fun particleBudget(): Int {
        val index = fieldIndex
        if (index == null || visibleSamples.isEmpty() || !windVisible || particleMode == ParticleMode.OFF) return 0
        val perSpot = WindField.PARTICLES_PER_SPOT * (index.radiusPx / WindField.MAX_DIST)
        val budget = (visibleSamples.size * perSpot).roundToInt().coerceIn(WindField.PARTICLES_MIN, WindField.PARTICLES_MAX)
        return (budget * particleScale * shed).toInt()
    }

    private fun fillParticles() {
        val target = particleBudget()
        if (pLat.size < target) {
            pLat = pLat.copyOf(target)
            pLon = pLon.copyOf(target)
            pAge = pAge.copyOf(target)
            pDead = pDead.copyOf(target)
        }
        for (i in particleCount until target) respawn(i)
        particleCount = target
    }

    /** Dropped somewhere in a random spot's reach (square-rooted distance keeps the spread even), as on the map. */
    private fun respawn(i: Int) {
        val index = fieldIndex
        if (visibleSamples.isEmpty() || index == null) {
            pDead[i] = true
            return
        }
        val origin = visibleSamples[random.nextInt(visibleSamples.size)]
        val angle = random.nextDouble() * 2 * PI
        val distance = sqrt(random.nextDouble()) * index.radiusDeg
        pLat[i] = (origin.lat + sin(angle) * distance).coerceIn(-89.0, 89.0)
        pLon[i] = origin.lon + cos(angle) * distance / max(0.05, cos(origin.lat * DEG))
        pAge[i] = random.nextInt(WindField.PARTICLE_LIFE)
        pDead[i] = false
    }

    /**
     * Advance every particle one step and draw the segment it travelled: the map's
     * [WindField.PARTICLE_SPEED] dp per knot per 60 fps frame, turned into degrees
     * at the current scale, so a streak crosses a spot's reach at the map's pace.
     */
    private fun stepParticles(canvas: Canvas, frames: Double, fadeFast: Boolean, allowRespawn: Boolean) {
        // Erase part of the last frame to leave trails; cut short while the globe turns under them
        fade.alpha = ((if (fadeFast) MOVING_TRAIL_FADE else WindField.PARTICLE_TRAIL_FADE) * 255).toInt()
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), fade)
        val p = projection ?: return
        val index = fieldIndex ?: return
        if (!windVisible || particleCount == 0) return

        val degPerPx = 1 / p.pxPerDeg
        val d = trailScale
        halo.strokeWidth = HALO_WIDTH * d
        streak.strokeWidth = LINE_WIDTH * d
        for (i in 0 until particleCount) {
            if (pDead[i]) {
                if (allowRespawn) respawn(i)
                continue
            }
            val inside = index.sample(pLon[i], pLat[i], sampleOut)
            val strength = sampleOut[4]
            // A steady heading spirals into the pole on a sphere; nobody kites up there, so particles retire first
            if (!inside || strength < WindField.MIN_FIELD || pAge[i]++ > WindField.PARTICLE_LIFE || abs(pLat[i]) > MAX_PARTICLE_LAT) {
                if (allowRespawn) respawn(i) else pDead[i] = true
                continue
            }
            val u = sampleOut[1]
            val v = sampleOut[2]
            val step = WindField.PARTICLE_SPEED * frames * degPerPx
            val nextLat = (pLat[i] + v * step).coerceIn(-89.0, 89.0)
            val nextLon = pLon[i] + u * step / max(0.05, cos(pLat[i] * DEG))
            val visible = p.project(pLat[i], pLon[i], from) && p.project(nextLat, nextLon, to)
            pLat[i] = nextLat
            pLon[i] = nextLon
            if (!visible || !p.onScreen(from[0], from[1], widthDp, heightDp, 10.0)) continue

            val speed = sqrt(u * u + v * v)
            val edge = min(1.0, strength) * (from[2] * 4).coerceIn(0.0, 1.0)
            val x0 = (from[0] * d).toFloat()
            val y0 = (from[1] * d).toFloat()
            val x1 = (to[0] * d).toFloat()
            val y1 = (to[1] * d).toFloat()
            halo.color = ((HALO_ALPHA * edge * 255).toInt() shl 24) or HALO_RGB
            canvas.drawLine(x0, y0, x1, y1, halo)
            streak.color = (((0.35 + 0.5 * edge) * 255).toInt() shl 24) or WindField.particleColorCached(speed)
            canvas.drawLine(x0, y0, x1, y1, streak)
        }
    }

    /** Reduced motion: the streaklines drawn once whenever the view settles, no animation. */
    private fun drawStaticParticles() {
        val canvas = trailsCanvas ?: return
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        for (i in 0 until particleCount) respawn(i)
        repeat(STATIC_STEPS) { stepParticles(canvas, 1.0, fadeFast = false, allowRespawn = false) }
    }

    // ---- Markers -----------------------------------------------------------

    private class Placed(val x: Float, val y: Float, val alpha: Float, val cluster: GlobeCluster)

    private val placed = ArrayList<Placed>()

    private fun layoutMarkers(p: GlobeProjection) {
        placed.clear()
        targets.clear()
        if (!markersVisible) return
        for (cluster in clusters) {
            if (!p.project(cluster.lat, cluster.lon, point) || !p.onScreen(point[0], point[1], widthDp, heightDp, 40.0)) continue
            val x = point[0].toFloat()
            val y = point[1].toFloat()
            // Markers fade as they slide over the horizon instead of popping out
            placed += Placed(x, y, (point[2] * 4).coerceIn(0.0, 1.0).toFloat(), cluster)
            targets += Target(x, y, markerRadius(cluster) + HIT_SLOP, cluster)
        }
    }

    /** The marker under a tap (dp), nearest first. */
    fun targetAt(x: Float, y: Float): GlobeCluster? =
        targets.filter { hypot(it.x - x, it.y - y) <= it.radius }.minByOrNull { hypot(it.x - x, it.y - y) }?.cluster

    private fun markerRadius(cluster: GlobeCluster): Float = when {
        cluster.single == null -> MapController.clusterRadiusDp(cluster.members.size)
        cluster.single?.wind != null -> MapController.DOT_RADIUS
        else -> MapController.EMPTY_DOT_RADIUS
    }

    // ---- Drawing -----------------------------------------------------------

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val washPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val trailPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RIM
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private var glow: Shader? = null
    private var ocean: Shader? = null
    private var limb: Shader? = null

    /** Centre and radius the gradients were made for; a spinning globe keeps them, a zoom does not. */
    private var shadersFor: Triple<Float, Float, Float>? = null

    private fun shaders(p: GlobeProjection) {
        val cx = p.cx.toFloat()
        val cy = p.cy.toFloat()
        val r = p.r.toFloat()
        val key = Triple(cx, cy, r)
        if (key == shadersFor) return
        shadersFor = key
        val atmosphere = palette.atmosphere
        // Atmosphere: a soft glow just outside the rim
        glow = RadialGradient(
            cx, cy, r * 1.14f,
            intArrayOf(0x59000000 or atmosphere, 0x59000000 or atmosphere, atmosphere),
            floatArrayOf(0f, 0.96f / 1.14f, 1f),
            Shader.TileMode.CLAMP,
        )
        // Ocean, lit from the upper left
        ocean = RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.35f, palette.oceanInner, palette.oceanOuter, Shader.TileMode.CLAMP)
        // Shade toward the rim, which is what makes a disc read as a ball
        limb = RadialGradient(
            cx - r * 0.3f, cy - r * 0.3f, r * 1.3f,
            intArrayOf(palette.highlight, 0, palette.limb),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun draw(canvas: Canvas) {
        val p = projection ?: return
        val cx = p.cx.toFloat()
        val cy = p.cy.toFloat()
        val r = p.r.toFloat()
        canvas.save()
        canvas.scale(density, density)

        fill.shader = glow
        canvas.drawCircle(cx, cy, r * 1.14f, fill)
        fill.shader = ocean
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null

        stroke.strokeWidth = 0.6f
        stroke.color = palette.graticule
        canvas.drawPath(graticulePath, stroke)
        if (hasOutlines) {
            fill.color = palette.land
            canvas.drawPath(landPath, fill)
            stroke.color = palette.landStroke
            canvas.drawPath(landPath, stroke)
            stroke.strokeWidth = 0.5f
            stroke.color = palette.borders
            canvas.drawPath(bordersPath, stroke)
        }

        val washBitmap = wash
        if (windVisible && washBitmap != null) canvas.drawBitmap(washBitmap, null, washRect, washPaint)

        fill.shader = limb
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null
        canvas.restore()

        if (windVisible && particleMode != ParticleMode.OFF) {
            trails?.let {
                canvas.save()
                canvas.scale(density / trailScale, density / trailScale)
                canvas.drawBitmap(it, 0f, 0f, trailPaint)
                canvas.restore()
            }
        }

        canvas.save()
        canvas.scale(density, density)
        for (marker in placed) drawMarker(canvas, marker)
        canvas.restore()
    }

    /** The map's markers: a wind-coloured dot with the knots in it, or a bubble with the count. */
    private fun drawMarker(canvas: Canvas, marker: Placed) {
        val colors = markerColors ?: return
        val cluster = marker.cluster
        val single = cluster.single
        val radius = markerRadius(cluster)
        val alpha = (marker.alpha * 255).toInt()
        val selected = single != null && single.wgId == selectedWgId

        val fillColor: Int
        val textColor: Int
        val label: String?
        if (single != null) {
            val windClass = single.windClass
            fillColor = colors.fill(windClass)
            textColor = colors.text(windClass)
            label = cluster.label
        } else {
            val wind = cluster.maxWind
            val windClass = if (wind == null) MapFeatures.NO_DATA else MapFeatures.windClassKey(wind.toDouble())
            fillColor = colors.fill(windClass)
            textColor = colors.text(windClass)
            label = cluster.label
        }

        markerShadow.color = SHADOW
        markerShadow.alpha = (0.35f * alpha).toInt()
        canvas.drawCircle(marker.x, marker.y + 1.5f, radius + if (single?.wind == null && single != null) 2f else 3f, markerShadow)
        markerFill.color = fillColor
        markerFill.alpha = alpha
        canvas.drawCircle(marker.x, marker.y, radius, markerFill)
        markerRim.strokeWidth = when {
            selected -> 3.5f
            single != null && single.wind == null -> 1.5f
            else -> 2f
        }
        markerRim.alpha = alpha
        canvas.drawCircle(marker.x, marker.y, radius, markerRim)
        if (label != null) {
            text.textSize = if (single != null) 12f else 13f
            text.color = textColor
            text.alpha = alpha
            canvas.drawText(label, marker.x, marker.y - (text.ascent() + text.descent()) / 2, text)
        }
    }

    private var markerColors: MarkerColors? = null

    /** Marker fill and text per wind class key, resolved once per palette instead of on every frame. */
    private class MarkerColors(private val fills: Map<String, Int>, private val calm: Int) {
        fun fill(windClass: String): Int = fills[windClass] ?: calm

        fun text(windClass: String): Int = if (windClass in DARK_TEXT_CLASSES) DARK_TEXT else LIGHT_TEXT

        companion object {
            fun of(colors: WindColors) = MarkerColors(
                mapOf(
                    WindClass.LIGHT.name.lowercase() to colors.light.toArgb(),
                    WindClass.MODERATE.name.lowercase() to colors.moderate.toArgb(),
                    WindClass.STRONG.name.lowercase() to colors.strong.toArgb(),
                    WindClass.EXTREME.name.lowercase() to colors.extreme.toArgb(),
                ),
                colors.calm.toArgb(),
            )
        }
    }

    private companion object {
        /** The wash is sampled every few dp and stretched with smoothing, like the web. */
        const val WASH_STEP = 6.0

        /** The map's cluster merge distance (map.js CLUSTER_RADIUS_PX, MapController.CLUSTER_RADIUS). */
        const val CLUSTER_RADIUS_DP = MapController.CLUSTER_RADIUS.toDouble()

        const val HIT_SLOP = 4f
        const val MOVING_TRAIL_FADE = 0.45f
        const val LINE_WIDTH = 1.4f
        const val HALO_WIDTH = 3f
        const val HALO_ALPHA = 0.35
        const val HALO_RGB = 0x0F172A
        const val STATIC_STEPS = 26
        const val MAX_PARTICLE_LAT = 75.0
        const val FRAME_BUDGET_MS = 20.0
        const val SLOW_FRAMES_BEFORE_SHEDDING = 30

        const val RIM = 0xFFFFFFFF.toInt()
        const val SHADOW = 0xFF000000.toInt()
        const val DARK_TEXT = 0xFF0B0F14.toInt()
        const val LIGHT_TEXT = 0xFFFFFFFF.toInt()
        val DARK_TEXT_CLASSES = setOf(
            WindClass.LIGHT.name.lowercase(),
            WindClass.MODERATE.name.lowercase(),
            WindClass.STRONG.name.lowercase(),
        )
    }
}

private fun PathSink(path: Path): PathSink = object : PathSink {
    override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)

    override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)

    override fun close() = path.close()
}
