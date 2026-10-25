package surf.varun.feature.map

import android.content.Context
import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.offline.OfflineManager
import timber.log.Timber

/** Camera position worth keeping across configuration changes. */
data class SavedCamera(val lat: Double, val lon: Double, val zoom: Double)

/**
 * MapLibre Native's `MapView` hosted in Compose.
 *
 * Deliberately the platform SDK in an `AndroidView` rather than the
 * `maplibre-compose` bindings the spec names: the wind field overlay projects
 * every spot through the raw `MapLibreMap.projection` on each rebuild and camera
 * event, which the Compose bindings don't expose in a stable form, while this SDK
 * API has been stable for years. Lifecycle events are forwarded to the view.
 */
@Composable
fun MapLibreView(
    initialCamera: SavedCamera,
    onReady: (MapController) -> Unit,
    onCameraMoveStarted: () -> Unit,
    onCameraIdle: (SavedCamera) -> Unit,
    onTap: (PointF) -> Unit,
    attributionMarginBottomPx: Int,
    modifier: Modifier = Modifier,
    /** Called once, the first time the map has finished drawing what it was asked to. */
    onFirstRender: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnReady by rememberUpdatedState(onReady)
    val currentMoveStarted by rememberUpdatedState(onCameraMoveStarted)
    val currentIdle by rememberUpdatedState(onCameraIdle)
    val currentTap by rememberUpdatedState(onTap)
    val currentFirstRender by rememberUpdatedState(onFirstRender)

    val mapView = remember {
        ensureMapLibre(context)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(
                CameraPosition.Builder()
                    .target(LatLng(initialCamera.lat, initialCamera.lon))
                    .zoom(initialCamera.zoom)
                    .build(),
            )
            .attributionEnabled(true)
            .logoEnabled(false)
            .compassEnabled(true)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
        MapView(context, options).apply { onCreate(Bundle()) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        var rendered = false
        // Removed on dispose rather than from inside the callback, while MapLibre iterates its listeners
        val firstRender = MapView.OnDidFinishRenderingMapListener {
            if (!rendered) {
                rendered = true
                currentFirstRender()
            }
        }
        mapView.addOnDidFinishRenderingMapListener(firstRender)
        mapView.getMapAsync { map: MapLibreMap ->
            map.uiSettings.apply {
                isAttributionEnabled = true
                // OSM / provider attribution must stay visible (licence requirement)
                attributionGravity = Gravity.BOTTOM or Gravity.START
                setAttributionMargins(24, 0, 0, attributionMarginBottomPx)
                compassGravity = Gravity.TOP or Gravity.END
            }
            map.addOnCameraMoveStartedListener { currentMoveStarted() }
            map.addOnCameraIdleListener {
                val camera = map.cameraPosition
                val target = camera.target
                if (target != null) currentIdle(SavedCamera(target.latitude, target.longitude, camera.zoom))
            }
            map.addOnMapClickListener { latLng ->
                currentTap(map.projection.toScreenLocation(latLng))
                true
            }
            currentOnReady(MapController(map, mapView, context))
        }
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.removeOnDidFinishRenderingMapListener(firstRender)
            // Leaving the screen: stop and destroy the GL surface
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private var mapLibreReady = false

/** MapLibre must be initialised before any MapView; also sizes the ambient tile cache (spec 5.4). */
internal fun ensureMapLibre(context: Context) {
    if (mapLibreReady) return
    MapLibre.getInstance(context.applicationContext)
    OfflineManager.getInstance(context.applicationContext).setMaximumAmbientCacheSize(
        AMBIENT_CACHE_BYTES,
        object : OfflineManager.FileSourceCallback {
            override fun onSuccess() = Unit

            override fun onError(message: String) {
                Timber.w("Ambient cache size not set: %s", message)
            }
        },
    )
    mapLibreReady = true
}

private const val AMBIENT_CACHE_BYTES = 100L * 1024 * 1024
