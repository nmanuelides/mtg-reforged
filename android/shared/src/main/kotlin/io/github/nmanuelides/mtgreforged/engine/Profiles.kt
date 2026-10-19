package io.github.nmanuelides.mtgreforged.engine

import androidx.compose.ui.graphics.ImageBitmap
import forge.localinstance.properties.ForgePreferences.FPref
import forge.model.FModel
import forge.player.GamePlayerUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.decodeImageFile
import io.github.nmanuelides.mtgreforged.platform.writePng

/**
 * The player's name and the round avatars for each seat: seat 0 is the player, seats 1-3 the
 * opponents in turn order. Avatars are 512px PNGs cropped by the user (from a photo or a card).
 */
object Profiles {
    const val SEATS = 4
    private lateinit var dir: File

    private val _version = MutableStateFlow(0)
    /** Bumps whenever the name or an avatar changes. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val cache = HashMap<Int, ImageBitmap?>()

    fun init(context: AppContext) {
        dir = File(context.filesDir, "avatars").apply { mkdirs() }
    }

    var name: String
        get() = runCatching { FModel.getPreferences().getPref(FPref.PLAYER_NAME) }.getOrNull().orEmpty().ifBlank { "Player" }
        set(value) {
            val clean = value.trim().ifBlank { "Player" }
            // Also renames Forge's local player, so the log and the match use it.
            GamePlayerUtil.getGuiPlayer(clean, 0, 0, true)
            _version.value++
        }

    private fun file(seat: Int) = File(dir, "seat$seat.png")

    @Synchronized
    fun avatar(seat: Int): ImageBitmap? = cache.getOrPut(seat) {
        file(seat).takeIf { ::dir.isInitialized && it.isFile }?.let { decodeImageFile(it) }
    }

    @Synchronized
    fun setAvatar(seat: Int, bitmap: ImageBitmap?) {
        val f = file(seat)
        if (bitmap == null) f.delete() else f.outputStream().use { bitmap.writePng(it) }
        cache[seat] = bitmap
        _version.value++
    }
}
