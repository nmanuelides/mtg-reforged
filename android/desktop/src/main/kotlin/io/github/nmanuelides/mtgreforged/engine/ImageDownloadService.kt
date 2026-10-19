package io.github.nmanuelides.mtgreforged.engine

import io.github.nmanuelides.mtgreforged.platform.AppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs [ImageDownloader] in the background for as long as the app is open (Android uses a
 * foreground service with a notification; here the dialog's own progress is enough).
 */
object ImageDownloadService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Synchronized
    fun start(context: AppContext, target: ImageDownloader.Scope) {
        if (job?.isActive == true) return
        job = scope.launch { ImageDownloader.run(context, target, this) }
    }

    fun stop(context: AppContext) = ImageDownloader.cancel()
}
