/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.widget.PlaylistWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Runs a [PlaylistLinkImporter] import as a foreground service, so it survives leaving the screen or the app
 * and shows its progress (with Cancel) in the notification shade. One import at a time.
 */
@AndroidEntryPoint
class PlaylistImportService : Service() {
    data class Progress(val done: Int = 0, val total: Int = 0, val title: String = "")

    sealed interface Event
    data class Done(val playlistId: String, val name: String, val songs: Int, val missing: Int, val truncated: Boolean) : Event
    data class Failed(val message: String?) : Event

    @Inject lateinit var database: MusicDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancelImport()
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url.isNullOrBlank() || job?.isActive == true) return START_NOT_STICKY
                createChannel()
                if (!goForeground()) return START_NOT_STICKY
                job = scope.launch { run(url) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        _progress.value = null
        super.onDestroy()
    }

    private suspend fun run(url: String) {
        _progress.value = Progress()
        try {
            val importingJob = currentCoroutineContext()[Job]!!
            val result = PlaylistLinkImporter.import(url) { done, total, title ->
                importingJob.ensureActive()
                scope.launch {
                    if (importingJob.isActive) {
                        val previous = _progress.value?.done ?: 0
                        if (done >= previous) _progress.value = Progress(done, total, title)
                        updateNotification()
                    }
                }
            }
            val playlist = PlaylistEntity(name = result.name.take(200), bookmarkedAt = LocalDateTime.now())
            // A failed or cancelled write rolls back the whole playlist, never leaving a partial import.
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    insert(playlist)
                    result.songs.forEach { insert(it.toMediaMetadata()) }
                    val p = checkNotNull(playlistBlocking(playlist.id))
                    addSongsToPlaylist(p, result.songs.map { it.id to null })
                }
            }
            val done = Done(playlist.id, result.name, result.songs.size, result.missing.size, result.truncated)
            _result.value = done
            notifyResult(done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            val failed = Failed((e as? IllegalArgumentException)?.message)
            _result.value = failed
            notifyResult(failed)
        } finally {
            _progress.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cancelImport() {
        job?.cancel()
        _progress.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.import_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun openAppIntent(playlistId: String? = null) = PendingIntent.getActivity(
        this,
        if (playlistId == null) 0 else 1,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (playlistId != null) {
                action = MainActivity.ACTION_OPEN_WIDGET_TARGET
                putExtra(MainActivity.EXTRA_WIDGET_TARGET_TYPE, PlaylistWidgetReceiver.TARGET_TYPE_LOCAL)
                putExtra(MainActivity.EXTRA_WIDGET_TARGET_ID, playlistId)
            }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun progressNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.playlist_add)
        .setContentTitle(getString(R.string.importing_playlist))
        .apply {
            val p = _progress.value
            if (p == null || p.total == 0) {
                setProgress(0, 0, true)
            } else {
                setContentText(getString(R.string.import_from_link_progress, p.done, p.total, p.done * 100 / p.total))
                setSubText(p.title)
                setProgress(p.total, p.done, false)
            }
        }
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(openAppIntent())
        .addAction(
            0,
            getString(android.R.string.cancel),
            PendingIntent.getService(
                this, 0,
                Intent(this, PlaylistImportService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun goForeground(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PROGRESS_ID, progressNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(PROGRESS_ID, progressNotification())
        }
        true
    } catch (e: RuntimeException) {
        reportException(e)
        _progress.value = null
        _result.value = Failed(null)
        stopSelf()
        false
    }

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // Throttled: notification updates are rate-limited by the system anyway.
    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotified < 500 || !canNotify()) return
        lastNotified = now
        NotificationManagerCompat.from(this).notify(PROGRESS_ID, progressNotification())
    }

    private fun notifyResult(event: Event) {
        if (!canNotify()) return
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.playlist_add)
            .setAutoCancel(true)
        when (event) {
            is Done -> builder
                .setContentTitle(getString(R.string.import_notification_done, event.name))
                .setContentText(
                    getString(R.string.import_from_link_done, event.songs, event.missing) +
                        if (event.truncated) " " + getString(R.string.import_from_link_truncated) else "",
                )
                .setContentIntent(openAppIntent(event.playlistId))
            is Failed -> builder
                .setContentTitle(getString(R.string.import_notification_failed))
                .setContentText(event.message ?: getString(R.string.import_from_link_failed))
                .setContentIntent(openAppIntent())
        }
        NotificationManagerCompat.from(this).notify(RESULT_ID, builder.build())
    }

    companion object {
        private const val CHANNEL_ID = "playlist_import"
        private const val PROGRESS_ID = 7301
        private const val RESULT_ID = 7302
        private const val ACTION_START = "com.metrolist.music.action.IMPORT_PLAYLIST_LINK"
        private const val ACTION_CANCEL = "com.metrolist.music.action.CANCEL_IMPORT_PLAYLIST_LINK"
        private const val EXTRA_URL = "url"

        private val _progress = MutableStateFlow<Progress?>(null)
        val progress = _progress.asStateFlow()
        private val _result = MutableStateFlow<Event?>(null)
        val result = _result.asStateFlow()

        fun start(context: Context, url: String) {
            if (_progress.value != null) return
            _result.value = null
            _progress.value = Progress()
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaylistImportService::class.java).setAction(ACTION_START).putExtra(EXTRA_URL, url),
                )
            } catch (e: RuntimeException) {
                reportException(e)
                _progress.value = null
                _result.value = Failed(null)
            }
        }

        fun cancel(context: Context) {
            if (_progress.value != null) context.startService(Intent(context, PlaylistImportService::class.java).setAction(ACTION_CANCEL))
        }

        fun clearResult() { _result.value = null }
    }
}
