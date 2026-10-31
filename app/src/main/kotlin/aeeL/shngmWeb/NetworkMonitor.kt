package aeeL.shngmWeb

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NetworkMonitor(context: Context) {

  private val connectivityManager =
    context.applicationContext.getSystemService(ConnectivityManager::class.java)
      ?: error("ConnectivityManager tidak tersedia")

  val networkStatus: Flow<Boolean> = callbackFlow {
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        trySend(isConnected())
      }

      override fun onLost(network: Network) {
        trySend(isConnected())
      }

      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
      }
    }

    try {
      // Tracks the default network, so a lost secondary network cannot report the app offline.
      connectivityManager.registerDefaultNetworkCallback(callback)
    } catch (e: Exception) {
      close(e)
      return@callbackFlow
    }

    trySend(isConnected())

    awaitClose {
      connectivityManager.unregisterNetworkCallback(callback)
    }
  }

  fun isConnected(): Boolean {
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}
