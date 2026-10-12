package io.github.nmanuelides.mtgreforged.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import forge.deck.Deck
import forge.game.GameType
import forge.gamemodes.match.GameLobby
import forge.gamemodes.match.LobbySlotType
import forge.gamemodes.net.ChatMessage
import forge.gamemodes.net.IOnlineChatInterface
import forge.gamemodes.net.IOnlineLobby
import forge.gamemodes.net.IRemote
import forge.gamemodes.net.NetConnectUtil
import forge.gamemodes.net.client.FGameClient
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent
import forge.gamemodes.net.server.FServerManager
import forge.gui.interfaces.ILobbyView
import forge.interfaces.IPlayerChangeListener
import forge.localinstance.properties.ForgeConstants
import forge.localinstance.properties.ForgeNetPreferences
import forge.model.FModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-network play on top of Forge's own server / client (Netty, port 36743): one device
 * hosts a lobby, friends on the same Wi-Fi join with its address, everyone picks a deck and
 * readies up, and the host starts. The match itself then runs through the usual
 * [io.github.nmanuelides.mtgreforged.match.ComposeGuiGame] on every device.
 */
object Online {
    private const val TAG = "Online"

    enum class Role { Host, Guest }

    data class Seat(
        val index: Int,
        val name: String,
        val kind: LobbySlotType,
        val ready: Boolean,
        val deck: String?,
        val mine: Boolean,
    )

    data class Lobby(
        val role: Role,
        val format: Engine.Format,
        val seats: List<Seat> = emptyList(),
        /** Addresses friends can type to join (host only). */
        val addresses: List<String> = emptyList(),
        val messages: List<String> = emptyList(),
        val error: String? = null,
    )

    private val _lobby = MutableStateFlow<Lobby?>(null)
    val lobby: StateFlow<Lobby?> = _lobby.asStateFlow()

    private var gameLobby: GameLobby? = null
    private var changes: IPlayerChangeListener? = null
    private var client: FGameClient? = null
    private val main = Handler(Looper.getMainLooper())

    private val view = object : ILobbyView {
        override fun setPlayerChangeListener(listener: IPlayerChangeListener) { changes = listener }
        override fun update(fullUpdate: Boolean) = publish()
        override fun update(slot: Int, type: LobbySlotType) = publish()
    }

    private val onlineLobby = object : IOnlineLobby {
        override fun setLobby(lobby: GameLobby): ILobbyView { gameLobby = lobby; return view }
        override fun setClient(client: FGameClient) { this@Online.client = client }
        override fun closeConn(msg: String) {
            _lobby.value = _lobby.value?.copy(error = msg)
        }
    }

    private val chat = object : IOnlineChatInterface {
        override fun setGameClient(iRemote: IRemote) {}
        override fun addMessage(message: ChatMessage) {
            val text = listOfNotNull(message.source, message.message).joinToString(": ")
            _lobby.value = _lobby.value?.let { it.copy(messages = (it.messages + text).takeLast(30)) }
        }
    }

    val port: Int get() = FModel.getNetPreferences().getPrefInt(ForgeNetPreferences.FNetPref.NET_PORT)

    /** Opens a lobby on this device. Call off the main thread. */
    fun host(format: Engine.Format, playerName: String) {
        leave()
        rename(playerName)
        _lobby.value = Lobby(Role.Host, format)
        NetConnectUtil.host(onlineLobby, chat)
        gameLobby?.let { lobby ->
            if (format == Engine.Format.Commander) lobby.applyVariant(GameType.Commander)
        }
        val addresses = runCatching { FServerManager.getAllLocalAddresses().values }.getOrDefault(emptyList())
            .filter { '.' in it && !it.startsWith("127.") }
            .map { "$it:$port" }
        _lobby.value = _lobby.value?.copy(addresses = addresses)
        publish()
    }

    /** Joins a friend's lobby ("192.168.1.20" or "192.168.1.20:36743"). Call off the main thread. */
    fun join(address: String, format: Engine.Format, playerName: String) {
        leave()
        rename(playerName)
        _lobby.value = Lobby(Role.Guest, format)
        val target = address.trim().let { if (':' in it) it else "$it:$port" }
        val result = runCatching { NetConnectUtil.join(target, onlineLobby, chat) }.getOrElse {
            ChatMessage(null, ForgeConstants.CONN_ERROR_PREFIX + (it.message ?: "connection failed"))
        }
        val text = result.message.orEmpty()
        when {
            text == ForgeConstants.INVALID_HOST_COMMAND -> fail("That address doesn't look right.")
            text.startsWith(ForgeConstants.CONN_ERROR_PREFIX) -> fail(text.removePrefix(ForgeConstants.CONN_ERROR_PREFIX))
            else -> publish()
        }
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        client = null
        gameLobby = null
        _lobby.value = _lobby.value?.copy(error = message)
    }

    private fun rename(name: String) {
        if (name.isBlank()) return
        FModel.getPreferences().setPref(forge.localinstance.properties.ForgePreferences.FPref.PLAYER_NAME, name.trim())
    }

    /** My seat: the host is always seat 0; a guest gets whichever seat the host assigned. */
    private fun mySeat(): Int {
        val lobby = gameLobby ?: return -1
        return (0 until lobby.numberOfSlots).firstOrNull { lobby.mayEdit(it) && lobby.getSlot(it).type != LobbySlotType.AI } ?: -1
    }

    fun chooseDeck(deck: Deck) {
        val seat = mySeat().takeIf { it >= 0 } ?: return
        changes?.update(seat, UpdateLobbyPlayerEvent.deckUpdate(deck))
    }

    fun setReady(ready: Boolean) {
        val seat = mySeat().takeIf { it >= 0 } ?: return
        changes?.update(seat, UpdateLobbyPlayerEvent.isReadyUpdate(ready))
    }

    /** Host: one more seat, open for a friend. */
    fun addSeat() {
        val lobby = gameLobby ?: return
        if (lobby.numberOfSlots < 4) lobby.addSlot()
        publish()
    }

    fun removeSeat(index: Int) {
        val lobby = gameLobby ?: return
        if (index > 0 && lobby.mayRemove(index)) lobby.removeSlot(index)
        publish()
    }

    /** Host: turn an open seat into an AI opponent with [deck], or back into an open seat. */
    fun setAi(index: Int, deck: Deck?) {
        val listener = changes ?: return
        if (deck == null) {
            listener.update(index, UpdateLobbyPlayerEvent.create(LobbySlotType.OPEN, "", -1, -1, index, false, false, emptySet(), null))
        } else {
            listener.update(index, UpdateLobbyPlayerEvent.create(LobbySlotType.AI, "AI ${index + 1}", -1, -1, index, false, false, emptySet(), null))
            listener.update(index, UpdateLobbyPlayerEvent.deckUpdate(deck))
            listener.update(index, UpdateLobbyPlayerEvent.isReadyUpdate(true))
        }
        publish()
    }

    /** Host: starts the match once everyone is ready (Forge reports what's missing otherwise). */
    fun start() {
        val lobby = gameLobby ?: return
        Thread {
            val run = runCatching { lobby.startGame() }.onFailure { Log.w(TAG, "start failed", it) }.getOrNull()
            if (run != null) main.post(run)
        }.start()
    }

    fun leave() {
        runCatching { client?.close() }
        if (_lobby.value?.role == Role.Host) runCatching { FServerManager.getInstance().stopServer() }
        client = null
        gameLobby = null
        changes = null
        _lobby.value = null
    }

    private fun publish() {
        val lobby = gameLobby ?: return
        val me = mySeat()
        val seats = (0 until lobby.numberOfSlots).map { i ->
            val slot = lobby.getSlot(i)
            Seat(
                index = i,
                name = slot.name?.takeIf { it.isNotBlank() } ?: if (slot.type == LobbySlotType.OPEN) "Open seat" else "Player ${i + 1}",
                kind = slot.type,
                ready = slot.isReady,
                deck = slot.deck?.name,
                mine = i == me,
            )
        }
        _lobby.value = _lobby.value?.copy(seats = seats)
    }
}
