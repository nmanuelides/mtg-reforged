package io.github.nmanuelides.mtgreforged.engine

import forge.ImageKeys
import io.github.nmanuelides.mtgreforged.platform.AppContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface

/**
 * Bumps [online] each time the PC gets a network back. Image loaders key on it, so cards
 * that failed while offline load again exactly once when connectivity returns. Windows has
 * no callback Java can hear, so a background thread looks at the network adapters now and then.
 */
object Connectivity {
    private val _online = MutableStateFlow(0)
    val online: StateFlow<Int> = _online.asStateFlow()

    fun watch(context: AppContext) {
        Thread({
            var was = connected()
            while (true) {
                try { Thread.sleep(15_000) } catch (e: InterruptedException) { return@Thread }
                val now = connected()
                if (now && !was) {
                    CardImages.forgetMissing()
                    ArtImages.forgetMissing()
                    synchronized(ImageKeys::class.java) { ImageKeys.clearMissingCards() }
                    _online.value++
                }
                was = now
            }
        }, "Connectivity").apply { isDaemon = true; start() }
    }

    private fun connected(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().any { nic ->
            nic.isUp && !nic.isLoopback && !nic.isVirtual && nic.inetAddresses.toList().isNotEmpty()
        }
    }.getOrDefault(true)
}
