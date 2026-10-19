package io.github.nmanuelides.mtgreforged.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.palette.graphics.Palette
import forge.sound.IAudioClip
import io.github.nmanuelides.mtgreforged.engine.AndroidAudioClip
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/*
 * The Android side of the platform layer. Code shared with the Windows app (android/shared)
 * reaches the system only through these names; android/desktop defines the same ones.
 */

typealias AppContext = Context
typealias Prefs = SharedPreferences
/** A bundled image or font: R.drawable.x / R.font.x. */
typealias ResId = Int

const val IS_DESKTOP = false
const val PLATFORM_NAME = "Android"

fun AppContext.prefs(name: String): Prefs = getSharedPreferences(name, Context.MODE_PRIVATE)

/** Where files the user can reach from outside the app go (adb pull). */
fun AppContext.publicDir(): File? = getExternalFilesDir(null)

/** Bundled engine data (engine-data.zip). */
fun AppContext.openAsset(name: String): InputStream = assets.open(name)

/** Size of a bundled asset; openFd only works for uncompressed ones (.zip is stored as-is by AGP). */
fun AppContext.assetSize(name: String): Long = runCatching { assets.openFd(name).use { it.length } }.getOrDefault(-1L)

fun AppContext.copyToClipboard(text: String?) {
    MainThread.post { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Forge", text)) }
}

fun AppContext.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

val AppContext.screenScale: Float get() = resources.displayMetrics.density

fun AppContext.isUnmeteredNetwork(): Boolean =
    getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == false

fun AppContext.showToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

fun AppContext.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}

object Log {
    fun d(tag: String, message: String) = android.util.Log.d(tag, message)
    fun i(tag: String, message: String) = android.util.Log.i(tag, message)
    fun w(tag: String, message: String, error: Throwable? = null) = android.util.Log.w(tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = android.util.Log.e(tag, message, error)
}

/** The UI thread. */
object MainThread {
    private val handler = Handler(Looper.getMainLooper())
    val isCurrent: Boolean get() = Looper.myLooper() == Looper.getMainLooper()
    fun post(block: Runnable) { handler.post(block) }
    fun postDelayed(delayMs: Long, block: Runnable) { handler.postDelayed(block, delayMs) }
}

/** Decodes an image file; with [maxWidth], halves it while it stays at least that wide. */
fun decodeImageFile(file: File, maxWidth: Int = 0): ImageBitmap? {
    var sample = 1
    if (maxWidth > 0) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    }
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

fun decodeImageStream(input: InputStream): ImageBitmap? = BitmapFactory.decodeStream(input)?.asImageBitmap()

fun ImageBitmap.writePng(out: OutputStream) {
    asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
}

/** A bundled image at its own pixel size, optionally decoded at 1/[sampleSize]. */
fun AppContext.loadImageRes(id: ResId, sampleSize: Int = 1): ImageBitmap? =
    BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false; inSampleSize = sampleSize })?.asImageBitmap()

@OptIn(ExperimentalTextApi::class)
fun fontRes(id: ResId, weight: FontWeight): Font =
    Font(id, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** [image] repeated in both directions, scaled by [scaleX]/[scaleY] and then moved by [dx]/[dy]. */
fun tiledImageShader(image: ImageBitmap, scaleX: Float, scaleY: Float, dx: Float, dy: Float): Shader =
    android.graphics.BitmapShader(image.asAndroidBitmap(), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT).apply {
        setLocalMatrix(android.graphics.Matrix().apply { setScale(scaleX, scaleY); postTranslate(dx, dy) })
    }

/** Fills the whole area with [cover], casting a soft [shadow] [blurPx] wide, [offsetYPx] down. */
fun DrawScope.drawShadowedRect(cover: Color, blurPx: Float, offsetYPx: Float, shadow: Color) {
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            color = cover.toArgb()
            setShadowLayer(blurPx, 0f, offsetYPx, shadow.toArgb())
        }
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}

/** One of an image's common colours, as androidx.palette reports it. */
class Swatch(val rgb: Int, val population: Int, val hsl: FloatArray)

fun artSwatches(image: ImageBitmap, maxColors: Int): List<Swatch> =
    Palette.from(image.asAndroidBitmap()).maximumColorCount(maxColors).generate().swatches
        .map { Swatch(it.rgb, it.population, it.hsl) }

fun createAudioClip(file: File): IAudioClip? = AndroidAudioClip.load(file)
