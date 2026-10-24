package surf.varun.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import surf.varun.core.domain.WindField
import surf.varun.core.model.Direction
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** A spot's wind at the selected hour, before projection. */
data class WindSample(val lat: Double, val lon: Double, val wind: Double, val direction: Direction?)

/** How the particles behave; decided by the screen from settings, battery saver and reduced motion. */
enum class ParticleMode { OFF, STATIC, ANIMATED }

/**
 * The wind field overlay (spec 6.4): a colour wash by speed plus particles riding
 * the interpolated wind, drawn above the map with the maths ported from the web's
 * `map.js` ([WindField]).
 *
 * Everything is computed in dp-space so radii and speeds match the web's CSS pixels;
 * drawing scales by density. Like the web, the field is rebuilt when the camera
 * settles and on every hour change, and hidden while the camera moves (a frozen
 * field would slide off the coastline).
 */
@Composable
fun WindFieldOverlay(
    controller: MapController?,
    samples: List<WindSample>,
    /** Bumped on every camera idle; a new value means "re-project". */
    cameraVersion: Int,
    moving: Boolean,
    particleMode: ParticleMode,
    /** 0..1 share of the particle budget (quality setting). */
    particleScale: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    var field by remember { mutableStateOf<BuiltField?>(null) }

    LaunchedEffect(controller, samples, cameraVersion, size) {
        if (controller == null || size == IntSize.Zero) return@LaunchedEffect
        // Projection reads the map's camera: main thread only
        val zoom = controller.zoom
        val projected = samples.map { s ->
            val p = controller.project(s.lat, s.lon)
            WindField.ScreenSample(p.x / density, p.y / density, s.wind.toFloat(), s.direction)
        }
        field = withContext(Dispatchers.Default) {
            buildField(projected, (size.width / density).toInt(), (size.height / density).toInt(), zoom)
        }
    }

    val built = field
    Canvas(modifier.onSizeChanged { size = it }) {
        if (moving || built == null) return@Canvas
        drawImage(
            built.heat,
            dstSize = IntSize((built.heat.width * built.heatStep * density).toInt(), (built.heat.height * built.heatStep * density).toInt()),
            dstOffset = IntOffset.Zero,
            filterQuality = FilterQuality.Low,
        )
    }

    if (built != null && !moving && particleMode != ParticleMode.OFF && built.points.isNotEmpty()) {
        Particles(built, density, particleMode, particleScale, modifier)
    }
}

/** A field ready to draw: the wash as a cell bitmap, the vector field and the spots that seed particles. */
internal class BuiltField(
    val heat: androidx.compose.ui.graphics.ImageBitmap,
    val heatStep: Int,
    val vectors: WindField.VectorField,
    val points: List<WindField.VelocityPoint>,
    val radius: Double,
    val widthDp: Int,
    val heightDp: Int,
)

internal fun buildField(samples: List<WindField.ScreenSample>, widthDp: Int, heightDp: Int, mapLibreZoom: Double): BuiltField {
    val radius = WindField.radius(WindField.leafletZoom(mapLibreZoom))
    val step = WindField.HEAT_STEP
    val cells = WindField.heatCells(samples, widthDp, heightDp, radius, step)
    val cols = (widthDp + step - 1) / step
    val rows = (heightDp + step - 1) / step
    val bitmap = Bitmap.createBitmap(max(cols, 1), max(rows, 1), Bitmap.Config.ARGB_8888)
    if (cols > 0 && rows > 0) bitmap.setPixels(cells, 0, cols, 0, 0, cols, rows)
    val points = WindField.velocityPoints(samples, widthDp, heightDp, radius)
    return BuiltField(
        heat = bitmap.asImageBitmap(),
        heatStep = step,
        vectors = WindField.vectorField(points, widthDp, heightDp, radius),
        points = points,
        radius = radius,
        widthDp = widthDp,
        heightDp = heightDp,
    )
}

/**
 * Particles advected along the field, leaving fading trails on an offscreen bitmap.
 * Spawned inside some spot's radius (not uniformly - most of the map is empty
 * water); the budget scales with the spots in view and shrinks when frames run long.
 */
@Composable
private fun Particles(field: BuiltField, density: Float, mode: ParticleMode, scale: Float, modifier: Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    val trails = remember(field) {
        Bitmap.createBitmap(
            max(1, (field.widthDp * density).toInt()),
            max(1, (field.heightDp * density).toInt()),
            Bitmap.Config.ARGB_8888,
        )
    }

    LaunchedEffect(field, mode, scale) {
        val canvas = Canvas(trails)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        val system = ParticleSystem(field, WindField.particleBudget(field.points.size, scale))
        val fade = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            alpha = (WindField.PARTICLE_TRAIL_FADE * 255).toInt()
        }
        val renderer = ParticleRenderer(density)
        if (mode == ParticleMode.STATIC) {
            // Reduced motion: one still frame of streaklines instead of an animation
            repeat(STATIC_STEPS) { system.step(1f) { x0, y0, x1, y1, kt -> renderer.segment(canvas, x0, y0, x1, y1, kt) } }
            frame++
            return@LaunchedEffect
        }
        var last = 0L
        var slowFrames = 0
        while (isActive) {
            withFrameNanos { now ->
                val dtMs = if (last == 0L) 16f else (now - last) / 1_000_000f
                last = now
                // Auto-degrade: sustained long frames shed particles (spec 6.3)
                if (dtMs > FRAME_BUDGET_MS) slowFrames++ else slowFrames = max(0, slowFrames - 1)
                if (slowFrames > SLOW_FRAMES_BEFORE_SHEDDING) {
                    system.shed()
                    slowFrames = 0
                }
                canvas.drawRect(0f, 0f, trails.width.toFloat(), trails.height.toFloat(), fade)
                system.step(min(dtMs / 16.67f, 3f)) { x0, y0, x1, y1, kt -> renderer.segment(canvas, x0, y0, x1, y1, kt) }
                frame++
            }
        }
    }

    Canvas(modifier) {
        // Reading the frame counter here is what redraws the trails every advected frame
        if (frame >= 0) drawIntoCanvas { it.nativeCanvas.drawBitmap(trails, 0f, 0f, null) }
    }
}

private class ParticleRenderer(private val density: Float) {
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = HALO_WIDTH * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF000000.toInt()
        alpha = (HALO_ALPHA * 255).toInt()
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = LINE_WIDTH * density
        strokeCap = Paint.Cap.ROUND
    }

    fun segment(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, knots: Float) {
        val sx0 = x0 * density
        val sy0 = y0 * density
        val sx1 = x1 * density
        val sy1 = y1 * density
        canvas.drawLine(sx0, sy0, sx1, sy1, halo)
        line.color = 0xFF000000.toInt() or WindField.particleColor(knots.toDouble())
        canvas.drawLine(sx0, sy0, sx1, sy1, line)
    }

    companion object {
        const val LINE_WIDTH = 1.4f
        const val HALO_WIDTH = 3f
        const val HALO_ALPHA = 0.35f
    }
}

/** Particle state in flat arrays (dp coordinates) - no allocation per frame. */
internal class ParticleSystem(private val field: BuiltField, budget: Int, private val random: Random = Random.Default) {
    private var count = budget
    private val x = FloatArray(budget)
    private val y = FloatArray(budget)
    private val age = IntArray(budget)
    private val sample = FloatArray(3)

    init {
        for (i in 0 until budget) respawn(i)
    }

    val size: Int get() = count

    fun shed() {
        count = max(WindField.PARTICLES_MIN / 2, (count * 0.75f).toInt())
    }

    /** Advance every particle by [frames] 60 fps frames, reporting each moved segment. */
    inline fun step(frames: Float, segment: (Float, Float, Float, Float, Float) -> Unit) {
        for (i in 0 until size) advance(i, frames, segment)
    }

    @PublishedApi
    internal inline fun advance(i: Int, frames: Float, segment: (Float, Float, Float, Float, Float) -> Unit) {
        val px = xAt(i)
        val py = yAt(i)
        if (!field.vectors.sample(px, py, sample) || sample[2] < WindField.MIN_FIELD || ageAt(i) > WindField.PARTICLE_LIFE) {
            respawn(i)
            return
        }
        val u = sample[0]
        val v = sample[1]
        val nx = px + u * WindField.PARTICLE_SPEED * frames
        val ny = py + v * WindField.PARTICLE_SPEED * frames
        segment(px, py, nx, ny, sqrt(u * u + v * v))
        move(i, nx, ny)
    }

    @PublishedApi internal fun xAt(i: Int) = x[i]

    @PublishedApi internal fun yAt(i: Int) = y[i]

    @PublishedApi internal fun ageAt(i: Int) = age[i]

    @PublishedApi
    internal fun move(i: Int, nx: Float, ny: Float) {
        x[i] = nx
        y[i] = ny
        age[i]++
    }

    /** Drop the particle inside a random spot's disc (square-rooted radius keeps it even). */
    @PublishedApi
    internal fun respawn(i: Int) {
        val origin = field.points[random.nextInt(field.points.size)]
        val angle = random.nextFloat() * 2 * Math.PI
        val distance = sqrt(random.nextFloat()) * field.radius
        x[i] = (origin.x + kotlin.math.cos(angle) * distance).toFloat()
        y[i] = (origin.y + kotlin.math.sin(angle) * distance).toFloat()
        age[i] = random.nextInt(WindField.PARTICLE_LIFE)
    }
}

private const val STATIC_STEPS = 26
private const val FRAME_BUDGET_MS = 20f
private const val SLOW_FRAMES_BEFORE_SHEDDING = 30
