package io.github.nmanuelides.mtgreforged.engine

import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.Log
import io.github.nmanuelides.mtgreforged.platform.Prefs
import io.github.nmanuelides.mtgreforged.platform.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.thread

/**
 * Online play through the relay (the Worker in /relay): Forge's own server and client stay as
 * they are and talk to 127.0.0.1, while this tunnels each connection over a WebSocket. The host
 * opens a room and gets a 5-letter code; friends type it and their Forge client connects to a
 * local port that leads to the host's server. Hosting needs this device activated with an
 * access code; joining doesn't.
 */
object Relay {
    private const val TAG = "Relay"

    /** The deployed Worker (see relay/README.md); blank until it's deployed. */
    private const val RELAY_URL = ""

    val configured: Boolean get() = RELAY_URL.isNotBlank()
    private val wsBase get() = RELAY_URL.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")

    private lateinit var prefs: Prefs
    private val _activated = MutableStateFlow(false)
    /** This device may host online games. */
    val activated: StateFlow<Boolean> = _activated.asStateFlow()

    private val open = CopyOnWriteArraySet<Closeable>()
    private val sockets = CopyOnWriteArraySet<WebSocketClient>()
    private var keepAlive: Thread? = null

    fun init(context: AppContext) {
        if (::prefs.isInitialized) return
        prefs = context.prefs("online")
        _activated.value = token() != null
    }

    private fun token(): String? = if (::prefs.isInitialized) prefs.getString("token", null) else null

    /** Trades an access code for this device's token. Returns an error to show, or null. Call off the main thread. */
    fun activate(code: String, deviceName: String): String? {
        if (!configured) return "Online play isn't available in this build."
        val connection = URL("$RELAY_URL/redeem").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "MTGReforged/${BuildConfig.VERSION_NAME}")
            connection.outputStream.use { it.write(JSONObject().put("code", code.trim()).put("name", deviceName).toString().toByteArray()) }
            val ok = connection.responseCode == 200
            val body = (if (ok) connection.inputStream else connection.errorStream)?.use { String(it.readBytes()) }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            val token = json?.optString("token").orEmpty()
            if (ok && token.isNotBlank()) {
                prefs.edit().putString("token", token).apply()
                _activated.value = true
                null
            } else json?.optString("error")?.ifBlank { null } ?: "Couldn't activate (error ${connection.responseCode})."
        } catch (e: Exception) {
            Log.w(TAG, "activate failed", e)
            "Couldn't reach the online service. Check your connection."
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Host: opens a room for the Forge server on [localPort] and returns its code. [onLost] is
     * called if the room drops. Throws with a message to show if it can't. Call off the main thread.
     */
    fun openRoom(localPort: Int, onLost: (String) -> Unit): String {
        val token = token() ?: error("This device isn't activated for online play.")
        val control = connectOrExplain("$wsBase/host?token=${enc(token)}&v=${BuildConfig.VERSION_CODE}")
        val room = control.read()?.text?.let { runCatching { JSONObject(it).optString("room") }.getOrNull() }
            ?.takeIf { it.isNotBlank() } ?: run { control.close(); error("The online service didn't open a room.") }
        track(control)
        thread(name = "relay-control", isDaemon = true) {
            try {
                while (true) {
                    val text = control.read()?.text ?: break
                    val id = runCatching { JSONObject(text).optString("open") }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue
                    // A friend joined: bridge their tunnel to the local server.
                    thread(name = "relay-accept", isDaemon = true) {
                        runCatching {
                            val tunnel = WebSocketClient.connect("$wsBase/accept/$room?id=${enc(id)}&token=${enc(token)}")
                            pipe(Socket(InetAddress.getLoopbackAddress(), localPort), tunnel)
                        }.onFailure { Log.w(TAG, "accept failed", it) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "control lost", e)
            }
            if (control in sockets) onLost("Lost the connection to the online service; friends can't join now.")
            untrack(control)
        }
        return room
    }

    /**
     * Friend: checks [room] exists and opens a local port that leads to it, for Forge's client
     * to connect to. Throws with a message to show if it can't. Call off the main thread.
     */
    fun listen(room: String): Int {
        val code = room.trim().uppercase()
        // The first tunnel opens now, so a wrong code or version shows before Forge connects.
        var first: WebSocketClient? = openTunnel(code)
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        open += server
        thread(name = "relay-listen", isDaemon = true) {
            try {
                while (true) {
                    val local = server.accept()
                    val tunnel = first ?: runCatching { openTunnel(code) }.getOrNull()
                    first = null
                    if (tunnel == null) local.close() else pipe(local, tunnel)
                }
            } catch (_: Exception) {
            }
            open -= server
        }
        return server.localPort
    }

    private fun openTunnel(room: String): WebSocketClient {
        val tunnel = connectOrExplain("$wsBase/join/$room?v=${BuildConfig.VERSION_CODE}")
        // Wait for the host's end before any game data goes out.
        while (true) {
            val message = tunnel.read() ?: run { tunnel.close(); error("The host closed the game.") }
            if (message.text?.contains("ready") == true) return tunnel
        }
    }

    /** Closes every room, tunnel and port this device has open. */
    fun stop() {
        sockets.toList().forEach { untrack(it); it.close() }
        open.toList().forEach { open -= it; runCatching { it.close() } }
    }

    private fun connectOrExplain(url: String): WebSocketClient {
        if (!configured) error("Online play isn't available in this build.")
        return try {
            WebSocketClient.connect(url)
        } catch (e: WebSocketClient.Refused) {
            error(runCatching { JSONObject(e.body).optString("error") }.getOrNull()?.ifBlank { null } ?: "The online service refused (error ${e.status}).")
        } catch (e: Exception) {
            Log.w(TAG, "connect failed", e)
            error("Couldn't reach the online service. Check your connection.")
        }
    }

    /** Copies bytes both ways between a local socket and a tunnel until either side closes. */
    private fun pipe(local: Socket, tunnel: WebSocketClient) {
        local.tcpNoDelay = true
        track(tunnel)
        open += local
        val done = {
            untrack(tunnel)
            open -= local
            tunnel.close()
            runCatching { local.close() }
        }
        thread(name = "relay-up", isDaemon = true) {
            val buffer = ByteArray(16 * 1024)
            runCatching {
                val input = local.getInputStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    tunnel.sendBinary(buffer, 0, n)
                }
            }
            done()
        }
        thread(name = "relay-down", isDaemon = true) {
            runCatching {
                val output = local.getOutputStream()
                while (true) {
                    val message = tunnel.read() ?: break
                    message.data?.let { output.write(it); output.flush() }
                }
            }
            done()
        }
    }

    // Idle connections get dropped along the way; a ping every 25 s keeps them open.
    private fun track(socket: WebSocketClient) {
        sockets += socket
        synchronized(this) {
            if (keepAlive?.isAlive == true) return
            keepAlive = thread(name = "relay-ping", isDaemon = true) {
                while (sockets.isNotEmpty()) {
                    Thread.sleep(25_000)
                    sockets.forEach { runCatching { it.ping() } }
                }
            }
        }
    }

    private fun untrack(socket: WebSocketClient) {
        sockets -= socket
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
