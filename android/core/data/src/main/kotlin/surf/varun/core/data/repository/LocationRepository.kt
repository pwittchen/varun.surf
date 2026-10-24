package surf.varun.core.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val fresh = provider?.let {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = androidx.core.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(manager, it, signal, executor) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                }
            }
        }
        val location = fresh ?: manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        location?.let { last = Coordinates(it.latitude, it.longitude) }
        return last
    }
}
