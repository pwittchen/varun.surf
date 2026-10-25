package surf.varun.feature.spot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter
import surf.varun.core.model.Spot
import surf.varun.core.ui.Intents
import surf.varun.core.ui.MapStyles
import surf.varun.core.ui.isCompactHeight
import timber.log.Timber

/**
 * Static map of the spot (spec 2.2: MapSnapshotter image instead of a live map),
 * rendered once per spot from satellite imagery and cached as a PNG - a spot doesn't move.
 * Tapping it hands over to a maps app. Without an image (offline on first open,
 * no GL) the dot still marks the place on a plain tile.
 */
@Composable
internal fun MiniMap(spot: Spot) {
    if (spot.coordinates == null) return
    val context = LocalContext.current
    val image = rememberSpotMapImage(spot)

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                // Sideways it sits in a half-width column with little height to spare
                .aspectRatio(if (isCompactHeight()) 2.6f else 2f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { Intents.navigate(context, spot) },
            contentAlignment = Alignment.Center,
        ) {
            image?.let {
                Image(it, contentDescription = stringResource(R.string.mini_map_description), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // The white ring keeps the dot readable on dark water and red rooftops alike
            Box(
                Modifier
                    .size(14.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .padding(2.dp)
                    .background(Color(0xFFE53935), CircleShape),
            )
        }
        Text(
            stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The satellite snapshot centred on the spot, null until it is read or rendered (and for
 * good without coordinates or GL). Shared by the location section and the top bar of a
 * spot without a photo, so both read the same cached PNG.
 */
@Composable
internal fun rememberSpotMapImage(spot: Spot?): ImageBitmap? {
    val coordinates = spot?.coordinates ?: return null
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var image by remember(spot.wgId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(spot.wgId) {
        val file = File(context.cacheDir, "minimap/${spot.wgId}-satellite.png")
        image = withContext(Dispatchers.IO) { readCached(file) }
            ?: snapshot(context, coordinates.lat, coordinates.lon, density)?.also { bitmap ->
                withContext(Dispatchers.IO) { writeCache(file, bitmap) }
            }?.asImageBitmap()
    }
    return image
}

private val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(30)

private fun readCached(file: File): ImageBitmap? {
    if (!file.exists() || System.currentTimeMillis() - file.lastModified() > CACHE_TTL_MS) return null
    return runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
}

private fun writeCache(file: File, bitmap: Bitmap) {
    runCatching {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.onFailure { Timber.w(it, "Mini map not cached") }
}

/** Render the map off screen; null when the style or tiles can't be loaded. Must start on the main thread. */
private suspend fun snapshot(context: Context, lat: Double, lon: Double, density: Float): Bitmap? =
    withTimeoutOrNull(20_000) {
        withContext(Dispatchers.Main) {
            try {
                MapLibre.getInstance(context.applicationContext)
                val options = MapSnapshotter.Options(WIDTH, HEIGHT)
                    .withStyleJson(MapStyles.satelliteJson)
                    .withCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(13.0).build())
                    .withPixelRatio(density)
                    .withLogo(false)
                    .withAttribution(false)
                val snapshotter = MapSnapshotter(context.applicationContext, options)
                suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { snapshotter.cancel() }
                    snapshotter.start(
                        { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
                        { error ->
                            Timber.w("Mini map snapshot failed: %s", error)
                            if (cont.isActive) cont.resume(null)
                        },
                    )
                }
            } catch (e: Throwable) {
                // No native library or GL context: the plain pin is enough
                Timber.w(e, "Mini map unavailable")
                null
            }
        }
    }

private const val WIDTH = 360
private const val HEIGHT = 180
