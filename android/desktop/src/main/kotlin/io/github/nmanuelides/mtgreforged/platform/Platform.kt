package io.github.nmanuelides.mtgreforged.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import forge.sound.AudioClip
import forge.sound.IAudioClip
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.WeakHashMap
import javax.swing.SwingUtilities
import javax.swing.Timer

/*
 * The Windows side of the platform layer: the same names the Android app defines
 * (android/app/.../platform), so the shared code in android/shared runs unchanged.
 */

typealias AppContext = DesktopContext
/** A bundled image or font: its path on the classpath (see R.kt). */
typealias ResId = String

const val IS_DESKTOP = true
const val PLATFORM_NAME = "Windows"

/**
 * Where the app keeps its files, like an Android app's private storage: card data, images,
 * decks, saves and settings, under %LOCALAPPDATA%\MTG Reforged.
 */
class DesktopContext(val root: File) {
    val filesDir: File = File(root, "files").apply { mkdirs() }
    val cacheDir: File = File(root, "cache").apply { mkdirs() }
    val applicationContext: DesktopContext get() = this

    /** Engine data and other files installed next to the app (compose.application.resources.dir). */
    val resourcesDir: File? = System.getProperty("compose.application.resources.dir")?.let(::File)

    internal val prefsDir = File(root, "prefs").apply { mkdirs() }
    internal val prefs = HashMap<String, Prefs>()

    companion object {
        fun default(): DesktopContext {
            val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), "AppData/Local")
            return DesktopContext(File(base, "MTG Reforged").apply { mkdirs() })
        }
    }
}

fun AppContext.prefs(name: String): Prefs = synchronized(prefs) { prefs.getOrPut(name) { Prefs(File(prefsDir, "$name.properties")) } }

/** Files the user can reach from Explorer: save copies, and import-save.json to bring one in. */
fun AppContext.publicDir(): File? = root

fun AppContext.openAsset(name: String): InputStream =
    resourcesDir?.let { File(it, name) }?.takeIf { it.isFile }?.let(::FileInputStream)
        ?: error("$name is missing from the installation")

fun AppContext.assetSize(name: String): Long = resourcesDir?.let { File(it, name) }?.takeIf { it.isFile }?.length() ?: -1L

fun AppContext.copyToClipboard(text: String?) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text.orEmpty()), null) }
}

fun AppContext.openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure { Log.w("Platform", "Couldn't open $url", it) }
}

/** Forge only uses this to size its own (unused) skin; Compose handles the real scaling. */
val AppContext.screenScale: Float get() = runCatching { Toolkit.getDefaultToolkit().screenResolution / 96f }.getOrDefault(1f)

/** Desktops don't pay by the megabyte: downloads run on any connection. */
fun AppContext.isUnmeteredNetwork(): Boolean = true

fun AppContext.showToast(text: String) = Toasts.show(text)

/** No share sheet on Windows: the text goes to the clipboard and a toast says so. */
fun AppContext.shareText(text: String) {
    copyToClipboard(text)
    showToast("Copied to the clipboard: paste it wherever you want to share it.")
}

/** Logcat stand-in: stderr plus a small rolling file next to the app's data. */
object Log {
    private val file: File by lazy { File(DesktopContext.default().root, "log.txt") }
    private val stamp = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS")

    @Synchronized
    private fun write(level: Char, tag: String, message: String, error: Throwable?) {
        val line = "${stamp.format(java.util.Date())} $level/$tag: $message" + (error?.let { "\n" + it.stackTraceToString() } ?: "")
        System.err.println(line)
        runCatching {
            if (file.length() > 2_000_000) file.renameTo(File(file.parentFile, "log.old.txt").also { it.delete() })
            file.appendText(line + "\n")
        }
    }

    fun d(tag: String, message: String) = write('D', tag, message, null)
    fun i(tag: String, message: String) = write('I', tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write('W', tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write('E', tag, message, error)
}

/** The UI thread: Swing's event dispatch thread, where Compose for Desktop runs. */
object MainThread {
    val isCurrent: Boolean get() = SwingUtilities.isEventDispatchThread()
    fun post(block: Runnable) = SwingUtilities.invokeLater(block)
    fun postDelayed(delayMs: Long, block: Runnable) {
        Timer(delayMs.toInt()) { block.run() }.apply { isRepeats = false; start() }
    }
}

/** Decodes an image file; with [maxWidth], halves it while it stays at least that wide (like inSampleSize). */
fun decodeImageFile(file: File, maxWidth: Int = 0): ImageBitmap? =
    runCatching { decode(file.readBytes(), maxWidth = maxWidth) }.getOrNull()

fun decodeImageStream(input: InputStream): ImageBitmap? = runCatching { decode(input.readBytes()) }.getOrNull()

private fun decode(bytes: ByteArray, maxWidth: Int = 0, sampleSize: Int = 1): ImageBitmap? {
    val image = Image.makeFromEncoded(bytes)
    var sample = sampleSize
    if (maxWidth > 0) while (image.width / (sample * 2) >= maxWidth) sample *= 2
    val w = (image.width / sample).coerceAtLeast(1)
    val h = (image.height / sample).coerceAtLeast(1)
    val bitmap = Bitmap().apply { allocN32Pixels(w, h) }
    org.jetbrains.skia.Canvas(bitmap).use {
        it.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), SamplingMode.LINEAR, null, true)
    }
    image.close()
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

fun ImageBitmap.writePng(out: OutputStream) {
    val data = Image.makeFromBitmap(asSkiaBitmap()).use { it.encodeToData(EncodedImageFormat.PNG) } ?: error("PNG encoding failed")
    out.write(data.bytes)
}

internal fun resourceBytes(id: ResId): ByteArray =
    DesktopContext::class.java.classLoader.getResourceAsStream(id)?.use { it.readBytes() } ?: error("Missing resource $id")

/** A bundled image at its own pixel size, optionally decoded at 1/[sampleSize]. */
fun AppContext.loadImageRes(id: ResId, sampleSize: Int = 1): ImageBitmap? =
    runCatching { decode(resourceBytes(id), sampleSize = sampleSize) }.getOrNull()

private val fontData = HashMap<ResId, ByteArray>()

/** One weight of a bundled variable font (its wght axis set to [weight]). */
fun fontRes(id: ResId, weight: FontWeight): Font {
    val data = synchronized(fontData) { fontData.getOrPut(id) { resourceBytes(id) } }
    return androidx.compose.ui.text.platform.Font(
        identity = "$id#${weight.weight}",
        data = data,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}

private val skiaImages = WeakHashMap<ImageBitmap, Image>()

/** [image] repeated in both directions, scaled by [scaleX]/[scaleY] and then moved by [dx]/[dy]. */
fun tiledImageShader(image: ImageBitmap, scaleX: Float, scaleY: Float, dx: Float, dy: Float): Shader {
    val skia = synchronized(skiaImages) { skiaImages.getOrPut(image) { Image.makeFromBitmap(image.asSkiaBitmap()) } }
    return skia.makeShader(
        FilterTileMode.REPEAT, FilterTileMode.REPEAT, SamplingMode.LINEAR,
        Matrix33.makeTranslate(dx, dy).makeConcat(Matrix33.makeScale(scaleX, scaleY)),
    )
}

/** Fills the whole area with [cover], casting a soft [shadow] [blurPx] wide, [offsetYPx] down. */
fun DrawScope.drawShadowedRect(cover: Color, blurPx: Float, offsetYPx: Float, shadow: Color) {
    // Android's setShadowLayer takes a blur radius; Skia wants the Gaussian's sigma.
    val sigma = if (blurPx > 0f) 0.57735f * blurPx + 0.5f else 0f
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            color = cover.toArgb()
            imageFilter = ImageFilter.makeDropShadow(0f, offsetYPx, sigma, sigma, shadow.toArgb())
        }
        canvas.nativeCanvas.drawRect(Rect.makeWH(size.width, size.height), paint)
    }
}

/** One of an image's common colours, as androidx.palette reports it. */
class Swatch(val rgb: Int, val population: Int, val hsl: FloatArray)

fun artSwatches(image: ImageBitmap, maxColors: Int): List<Swatch> = ColorQuantizer.swatches(image, maxColors)

/** Forge's own desktop sound player (software mixer, MP3 decoding), compiled in from forge-gui-desktop. */
fun createAudioClip(file: File): IAudioClip? = if (file.isFile) AudioClip(file.name) else null
