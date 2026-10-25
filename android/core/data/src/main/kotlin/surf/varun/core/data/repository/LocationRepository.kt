package surf.varun.core.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import surf.varun.core.model.Coordinates
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Coarse device location for "nearby" sorting and distances, from the platform
 * LocationManager so the app needs no Google Play Services. Never leaves the device.
 */
interface LocationRepository {
    fun hasPermission(): Boolean

    suspend fun currentLocation(): Coordinates?
}

@Singleton
class PlatformLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationRepository {
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var last: Coordinates? = null

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Coordinates? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return last
        val lastKnown = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
        // A fix from a moment ago is as good as a new one, and there is no waiting for it
        val age = lastKnown?.let { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos }
        val location = if (age != null && age < RECENT_FIX_NANOS) lastKnown else freshLocation(manager) ?: lastKnown
        location?.let { last = Coordinates(it.latitude, it.longitude) }
        return last
    }

    /** One fix from the quickest enabled provider: fused (wifi and cell) first, GPS last. */
    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(manager: LocationManager): Location? {
        val provider = PROVIDERS.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } catch (_: SecurityException) {
                    // GPS needs the fine permission before Android 12
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private companion object {
        val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        const val RECENT_FIX_NANOS = 2 * 60 * 1_000_000_000L
        const val FIX_TIMEOUT_MS = 8_000L
    }
}
