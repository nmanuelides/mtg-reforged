package io.github.nmanuelides.mtgreforged.engine

import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.os.SystemClock
import forge.sound.IAudioClip
import java.io.File

/**
 * One of Forge's sound effects, played through a shared [SoundPool] (low latency, many
 * short clips at once). Forge picks which clip to play for each game event.
 */
class AndroidAudioClip private constructor(private val soundId: Int, private val durationMs: Long) : IAudioClip {

    @Volatile private var streamId = 0
    @Volatile private var endsAt = 0L

    override fun play(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        streamId = pool.play(soundId, volume, volume, 1, 0, 1f)
        endsAt = SystemClock.uptimeMillis() + durationMs
    }

    override fun isDone() = SystemClock.uptimeMillis() >= endsAt

    override fun stop() {
        if (streamId != 0) pool.stop(streamId)
        endsAt = 0
    }

    override fun loop() {
        streamId = pool.play(soundId, 1f, 1f, 1, -1, 1f)
        endsAt = Long.MAX_VALUE
    }

    override fun dispose() {
        stop()
        pool.unload(soundId)
    }

    companion object {
        private val pool: SoundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()

        fun load(file: File): AndroidAudioClip? {
            if (!file.isFile) return null
            val duration = runCatching {
                MediaMetadataRetriever().use {
                    it.setDataSource(file.path)
                    it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }
            }.getOrNull() ?: 1000L
            return AndroidAudioClip(pool.load(file.path, 1), duration)
        }
    }
}
