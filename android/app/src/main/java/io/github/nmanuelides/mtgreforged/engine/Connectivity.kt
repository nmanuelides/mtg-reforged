package io.github.nmanuelides.mtgreforged.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import forge.ImageKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bumps [online] each time the device gets a network back. Image loaders key on it, so
 * cards that failed while offline load again exactly once when connectivity returns,
 * with no polling or retry loops.
 */
object Connectivity {
    private val _online = MutableStateFlow(0)
    val online: StateFlow<Int> = _online.asStateFlow()

    fun watch(context: Context) {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            private var first = true
            override fun onAvailable(network: Network) {
                // The first callback just reports the current network; only real reconnects count.
                if (first) { first = false; return }
                CardImages.forgetMissing()
                ArtImages.forgetMissing()
                synchronized(ImageKeys::class.java) { ImageKeys.clearMissingCards() }
                _online.value++
            }

            override fun onLost(network: Network) {
                first = false
            }
        })
    }
}
