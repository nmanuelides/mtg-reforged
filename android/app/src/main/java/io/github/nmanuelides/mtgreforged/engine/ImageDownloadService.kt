package io.github.nmanuelides.mtgreforged.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.nmanuelides.mtgreforged.MainActivity
import io.github.nmanuelides.mtgreforged.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Foreground service that keeps [ImageDownloader] running with a progress notification. */
class ImageDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ImageDownloader.cancel()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val target = intent?.getStringExtra(EXTRA_SCOPE)?.let { runCatching { ImageDownloader.Scope.valueOf(it) }.getOrNull() }
            ?: ImageDownloader.Scope.Decks
        channel()
        val first = notification(ImageDownloader.Progress(running = true, scope = target, message = "Listing cards…"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, first)
        }
        job = scope.launch {
            val updates = launch {
                val manager = getSystemService(NotificationManager::class.java)
                while (true) {
                    manager.notify(ID, notification(ImageDownloader.progress.value))
                    delay(1_000)
                }
            }
            ImageDownloader.run(applicationContext, target, scope)
            updates.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ImageDownloader.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun channel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Card image downloads", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(p: ImageDownloader.Progress): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ImageDownloadService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Downloading card images")
            .setContentText(p.message ?: "${p.done} / ${p.total} · ${p.downloaded} new")
            .setProgress(p.total.coerceAtLeast(1), p.done, p.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val ID = 42
        private const val CHANNEL = "image-downloads"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_SCOPE = "scope"

        fun start(context: Context, scope: ImageDownloader.Scope) {
            ContextCompat.startForegroundService(
                context, Intent(context, ImageDownloadService::class.java).putExtra(EXTRA_SCOPE, scope.name),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ImageDownloadService::class.java).setAction(ACTION_STOP))
        }
    }
}
