package io.github.nmanuelides.mtgreforged.engine

import io.github.nmanuelides.mtgreforged.BuildConfig
import java.io.File
import java.util.Properties
import java.util.zip.ZipInputStream
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.assetSize
import io.github.nmanuelides.mtgreforged.platform.openAsset

/**
 * Installs the rules data (card scripts, editions, formats...) bundled with the app as
 * `engine-data.zip` into [forgeDir]/res. The data is reinstalled whenever the installed
 * copy was stamped by a different build, i.e. after every app update, so engine code
 * and data always come from the same commit. User data in [forgeDir]/data is untouched.
 */
class EngineDataInstaller(private val context: AppContext, val forgeDir: File) {

    private val resDir = File(forgeDir, "res")
    private val installedManifest = File(forgeDir, "engine-data.properties")

    /** Installed data is stamped with the app version that installed it. */
    fun isUpToDate(): Boolean {
        if (!resDir.isDirectory || !installedManifest.isFile) return false
        val installed = Properties().apply { installedManifest.inputStream().use(::load) }
        return installed.getProperty(KEY_VERSION_CODE) == BuildConfig.VERSION_CODE.toString() &&
            installed.getProperty(KEY_COMMIT) == BuildConfig.ENGINE_COMMIT &&
            // Dev builds can change the data without a new commit; the size catches that.
            installed.getProperty(KEY_SIZE) == bundledSize().toString()
    }

    /** Extracts the bundled data next to the current copy, then swaps it in. */
    fun install(onProgress: (Float) -> Unit) {
        val staging = File(forgeDir, "res.staging").apply { deleteRecursively() }
        val total = bundledSize().takeIf { it > 0 } ?: Long.MAX_VALUE

        CountingStream(context.openAsset(ASSET)).use { counting ->
            ZipInputStream(counting.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.name.startsWith("res/")) continue
                    val relative = entry.name.removePrefix("res/")
                    val target = File(staging, relative)
                    require(target.canonicalPath.startsWith(staging.canonicalPath)) { "Bad entry ${entry.name}" }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                    onProgress(counting.count.toFloat() / total)
                }
            }
        }

        val previous = File(forgeDir, "res.old").apply { deleteRecursively() }
        if (resDir.exists() && !resDir.renameTo(previous)) error("Could not move old engine data")
        if (!staging.renameTo(resDir)) error("Could not install engine data")
        previous.deleteRecursively()

        Properties().apply {
            setProperty(KEY_VERSION_CODE, BuildConfig.VERSION_CODE.toString())
            setProperty(KEY_COMMIT, BuildConfig.ENGINE_COMMIT)
            setProperty(KEY_SIZE, bundledSize().toString())
            installedManifest.outputStream().use { store(it, null) }
        }
        onProgress(1f)
    }

    private fun bundledSize(): Long = context.assetSize(ASSET)

    private class CountingStream(private val inner: java.io.InputStream) : java.io.FilterInputStream(inner) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }
    }

    companion object {
        const val ASSET = "engine-data.zip"
        private const val KEY_VERSION_CODE = "versionCode"
        private const val KEY_COMMIT = "engineCommit"
        private const val KEY_SIZE = "dataSize"
    }
}
