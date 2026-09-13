package com.promptforge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps PromptForge alive in the BACKGROUND and shows
 * live progress in the status bar while a model downloads, and an
 * "AI working on-device" notice while inference tasks run.
 * Two independent modes; the service only dies when BOTH are idle.
 */
class DownloadService : Service() {

    companion object {
        private const val CH_DOWNLOADS = "promptforge_downloads"
        private const val CH_INFERENCE = "promptforge_inference"
        private const val NOTIF_DL = 41
        private const val NOTIF_INF = 42
        const val ACTION_DOWNLOAD = "com.promptforge.action.DOWNLOAD"
        const val ACTION_INFERENCE = "com.promptforge.action.INFERENCE"
        const val ACTION_STOP_INFERENCE = "com.promptforge.action.STOP_INFERENCE"
        const val EXTRA_TITLE = "title"

        /** Starts/resumes a foreground-shown model download. */
        fun startDownload(c: Context, title: String) {
            runCatching {
                c.startForegroundService(
                    Intent(c, DownloadService::class.java).setAction(ACTION_DOWNLOAD).putExtra(EXTRA_TITLE, title)
                )
            }
        }

        /** Shows an ongoing "AI working" notice while on-device tasks run. */
        fun startInference(c: Context, title: String) {
            runCatching {
                c.startForegroundService(
                    Intent(c, DownloadService::class.java).setAction(ACTION_INFERENCE).putExtra(EXTRA_TITLE, title)
                )
            }
        }

        /** Inference finished — the service stays alive only if downloads still run. */
        fun stopInference(c: Context) {
            runCatching {
                c.startService(Intent(c, DownloadService::class.java).setAction(ACTION_STOP_INFERENCE))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wake: PowerManager.WakeLock? = null
    private var observing = false
    private var downloadActive = false
    private var inferenceActive = false
    private var lastDownloadNotif: Notification? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Watchdog: if neither flag is active (e.g. the app was killed while
        // the service survived), tear everything down and sweep the notice.
        if (!downloadActive && !inferenceActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                downloadActive = true
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.service_download)
                createChannels()
                startAsForeground(NOTIF_DL, downloadNotif(title, 0, indeterminate = true))
                holdWake()
                observeDownloads()
            }
            ACTION_INFERENCE -> {
                inferenceActive = true
                val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.service_inference)
                createChannels()
                startAsForeground(NOTIF_INF, inferenceNotif(title))
                holdWake()
            }
            ACTION_STOP_INFERENCE -> {
                inferenceActive = false
                val nm = getSystemService(NotificationManager::class.java)
                nm.cancel(NOTIF_INF) // stale "AI working" notice: gone immediately
                if (downloadActive) {
                    lastDownloadNotif?.let { startAsForeground(NOTIF_DL, it) }
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            else -> {
                inferenceActive = false
                if (!downloadActive) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Mirrors the downloader's live progress into the status-bar notification. */
    private fun observeDownloads() {
        if (observing) return
        observing = true
        val container = (application as PromptForgeApp).container
        val nm = getSystemService(NotificationManager::class.java)
        scope.launch {
            container.deviceDownloader.progress.collect { map ->
                val running = map.entries.firstOrNull { it.value.running }
                if (running == null) {
                    downloadActive = false
                    if (!inferenceActive) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else nm.cancel(NOTIF_DL)
                } else {
                    val label = com.promptforge.data.DeviceModelDownloader.CATALOG
                        .firstOrNull { it.id == running.key }?.fileName ?: running.key
                    val n = downloadNotif(label, running.value.percent, indeterminate = false)
                    lastDownloadNotif = n
                    nm.notify(NOTIF_DL, n)
                }
            }
        }
    }

    private fun startAsForeground(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(id, n)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        listOf(
            CH_DOWNLOADS to (R.string.notif_channel_downloads to NotificationManager.IMPORTANCE_LOW),
            CH_INFERENCE to (R.string.notif_channel_inference to NotificationManager.IMPORTANCE_LOW),
        ).forEach { (id, pair) ->
            val ch = NotificationChannel(id, getString(pair.first), pair.second)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun downloadNotif(title: String, percent: Int, indeterminate: Boolean, failed: Boolean = false): Notification =
        NotificationCompat.Builder(this, CH_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(
                if (indeterminate) getString(R.string.service_download_starting)
                else getString(R.string.service_download_progress, percent)
            )
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun inferenceNotif(title: String): Notification =
        NotificationCompat.Builder(this, CH_INFERENCE)
            .setSmallIcon(R.drawable.ic_sparkles)
            .setContentTitle(title)
            .setContentText(getString(R.string.service_inference_body))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun holdWake() {
        if (wake != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PromptForge:BackgroundWork").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // hard cap 6h (also the platform dataSync budget)
        }
    }

    override fun onDestroy() {
        // Hard sweep: never leave a ghost notification behind.
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIF_DL)
            nm.cancel(NOTIF_INF)
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        wake?.let { runCatching { it.release() } }
        wake = null
        scope.cancel()
        super.onDestroy()
    }
}
