package surf.varun.core.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

interface ConnectivityMonitor {
    val isOnline: Flow<Boolean>
}

@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityMonitor {
    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(true)
            close()
            return@callbackFlow
        }
        val networks = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networks += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                networks -= network
                trySend(networks.isNotEmpty())
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        manager.registerNetworkCallback(request, callback)
        trySend(
            manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        )
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}
