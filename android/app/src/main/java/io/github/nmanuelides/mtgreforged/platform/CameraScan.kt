package io.github.nmanuelides.mtgreforged.platform

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.nmanuelides.mtgreforged.engine.ScannedLine
import java.util.concurrent.Executors

/** Android can scan cards with the camera. */
const val CAN_SCAN_CARDS = true

/**
 * The back camera, reading text on the device (ML Kit) from each frame it can keep up with;
 * [onLines] gets every line read, on the main thread. Asks for the camera permission first.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraTextView(modifier: Modifier, torch: Boolean, onLines: (List<ScannedLine>) -> Unit, onDenied: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) onDenied()
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Box(modifier.background(Color.Black))
        return
    }
    val latest by rememberUpdatedState(onLines)
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var useCases by remember { mutableStateOf<Pair<Preview, ImageAnalysis>?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    // Turning the phone doesn't rebuild the screen, so tell the camera which way is up now
    // (the text reader needs frames the right way round).
    val orientation = LocalConfiguration.current.orientation
    LaunchedEffect(orientation, useCases) {
        val rotation = previewView?.display?.rotation ?: return@LaunchedEffect
        useCases?.let { (preview, analysis) -> preview.targetRotation = rotation; analysis.targetRotation = rotation }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            recognizer.close()
            executor.shutdown()
        }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx)
            previewView = view
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                // 720p is plenty for printed text and keeps each read quick.
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) { proxy.close(); return@setAnalyzer }
                    val rotation = proxy.imageInfo.rotationDegrees
                    val height = (if (rotation % 180 == 0) proxy.height else proxy.width).toFloat()
                    val width = (if (rotation % 180 == 0) proxy.width else proxy.height).toFloat()
                    // The preview fills the screen and crops the camera's picture (FILL_CENTER):
                    // only text inside that visible part counts, measured against it.
                    val viewW = view.width.toFloat()
                    val viewH = view.height.toFloat()
                    val scale = if (viewW > 0f && viewH > 0f) maxOf(viewW / width, viewH / height) else 1f
                    val visW = if (viewW > 0f) viewW / scale else width
                    val visH = if (viewH > 0f) viewH / scale else height
                    val left = (width - visW) / 2
                    val top = (height - visH) / 2
                    recognizer.process(InputImage.fromMediaImage(media, rotation))
                        .addOnSuccessListener { text ->
                            latest(text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                                val box = line.boundingBox ?: return@mapNotNull null
                                // Whole line on screen (a little slack at the edges).
                                val slack = visW * 0.02f
                                if (box.left < left - slack || box.right > left + visW + slack || box.top < top || box.bottom > top + visH) return@mapNotNull null
                                ScannedLine(line.text, (box.exactCenterY() - top) / visH, box.height() / visH, (box.exactCenterX() - left) / visW)
                            })
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    useCases = preview to analysis
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        modifier = modifier,
    )
}
