/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.lastfm.LastFM
import com.metrolist.lastfm.LastFM.ScrobbleResult
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min

class ScrobbleManager(
    private val scope: CoroutineScope,
    private val context: Context,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 50
) {
    @Serializable
    data class PendingScrobble(
        val artist: String,
        val track: String,
        val timestamp: Long,
        val album: String? = null,
        val duration: Int? = null,
    )

    /** Pure timer/threshold math, split out so it is testable without Android. */
    internal object ScrobbleTiming {
        fun initialDelayMillis(
            durationSeconds: Int,
            thresholdPercent: Float,
            maxDelaySeconds: Int,
        ): Long {
            val threshold = durationSeconds * 1000L * thresholdPercent
            return min(threshold.toLong(), maxDelaySeconds * 1000L)
        }

        suspend fun drainQueue(
            queue: List<PendingScrobble>,
            send: suspend (PendingScrobble) -> ScrobbleResult,
            persist: (List<PendingScrobble>) -> Unit,
        ) {
            queue.forEachIndexed { index, entry ->
                if (send(entry) == ScrobbleResult.Retry) return
                persist(queue.subList(index + 1, queue.size))
            }
        }
    }

    private var scrobbleJob: Job? = null
    private var scrobbleRemainingMillis: Long = 0L
    private var scrobbleTimerStartedAt: Long = 0L
    private var songStartedAt: Long = 0L
    private var songStarted = false
    var useNowPlaying = true

    // ponytail: fixed-size file queue, oldest dropped past 500 entries; swap for Room if queued history ever matters
    private val pendingStore = context.getSharedPreferences("lastfm_pending_scrobbles", Context.MODE_PRIVATE)
    private val queueMutex = Mutex()

    init {
        scope.launch(Dispatchers.IO) {
            NetworkConnectivityObserver(context.applicationContext).networkStatus
                .collect { if (it) flush() }
        }
        scope.launch(Dispatchers.IO) { flush() }
    }

    fun destroy() {
        scrobbleJob?.cancel()
        scrobbleRemainingMillis = 0L
        scrobbleTimerStartedAt = 0L
        songStartedAt = 0L
        songStarted = false
    }

    fun onSongStart(metadata: MediaMetadata?, duration: Long? = null) {
        if (metadata == null) return
        songStartedAt = System.currentTimeMillis() / 1000
        songStarted = true
        startScrobbleTimer(metadata, duration)
        if (useNowPlaying) {
            updateNowPlaying(metadata)
        }
    }

    fun onSongResume(metadata: MediaMetadata) {
        resumeScrobbleTimer(metadata)
    }

    fun onSongPause() {
        pauseScrobbleTimer()
    }

    fun onSongStop() {
        stopScrobbleTimer()
        songStarted = false
    }

    private fun startScrobbleTimer(metadata: MediaMetadata, duration: Long? = null) {
        scrobbleJob?.cancel()
        val duration = duration?.toInt()?.div(1000) ?: metadata.duration

        if (duration <= minSongDuration) return

        scrobbleRemainingMillis =
            ScrobbleTiming.initialDelayMillis(duration, scrobbleDelayPercent, scrobbleDelaySeconds)

        if (scrobbleRemainingMillis <= 0) {
            scrobbleSong(metadata)
            return
        }
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob = scope.launch {
            delay(scrobbleRemainingMillis)
            scrobbleSong(metadata)
            scrobbleJob = null
        }
    }

    private fun pauseScrobbleTimer() {
        scrobbleJob?.cancel()
        if (scrobbleTimerStartedAt != 0L) {
            val elapsed = System.currentTimeMillis() - scrobbleTimerStartedAt
            scrobbleRemainingMillis -= elapsed
            if (scrobbleRemainingMillis < 0) scrobbleRemainingMillis = 0
            scrobbleTimerStartedAt = 0L
        } else {
        }
    }

    private fun resumeScrobbleTimer(metadata: MediaMetadata) {
        if (scrobbleRemainingMillis <= 0) return
        scrobbleJob?.cancel()
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob = scope.launch {
            delay(scrobbleRemainingMillis)
            scrobbleSong(metadata)
            scrobbleJob = null
        }
    }

    private fun stopScrobbleTimer() {
        scrobbleJob?.cancel()
        scrobbleJob = null
        scrobbleRemainingMillis = 0
    }

    private fun scrobbleSong(metadata: MediaMetadata) {
        val entry =
            PendingScrobble(
                artist = metadata.artists.joinToString { it.name },
                track = metadata.title,
                timestamp = songStartedAt,
                album = metadata.album?.title,
                duration = metadata.duration,
            )
        scope.launch {
            when (send(entry)) {
                ScrobbleResult.Sent -> Unit
                ScrobbleResult.Retry -> enqueue(entry)
                ScrobbleResult.Failed -> Unit
            }
        }
    }

    private suspend fun send(entry: PendingScrobble): ScrobbleResult {
        // LastFM.scrobble force-unwraps sessionKey, and a failed call would queue + crash on this path.
        if (LastFM.sessionKey == null || !LastFM.isInitialized()) return ScrobbleResult.Retry
        return LastFM.scrobble(
            artist = entry.artist,
            track = entry.track,
            duration = entry.duration,
            timestamp = entry.timestamp,
            album = entry.album,
        )
    }

    private suspend fun enqueue(entry: PendingScrobble) = queueMutex.withLock {
        val queue = readQueue()
        queue.add(entry)
        writeQueue(queue.takeLast(MAX_PENDING))
    }

    private fun flush() {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                ScrobbleTiming.drainQueue(readQueue(), ::send, ::writeQueue)
            }
        }
    }

    private fun readQueue(): MutableList<PendingScrobble> =
        pendingStore.getString(KEY_QUEUE, null)
            ?.let { runCatching { Json.decodeFromString<List<PendingScrobble>>(it) }.getOrNull() }
            ?.toMutableList()
            ?: mutableListOf()

    private fun writeQueue(queue: List<PendingScrobble>) {
        pendingStore.edit().putString(KEY_QUEUE, Json.encodeToString(queue)).apply()
    }

    private companion object {
        const val KEY_QUEUE = "queue"
        const val MAX_PENDING = 500
    }

    private fun updateNowPlaying(metadata: MediaMetadata) {
        scope.launch {
            LastFM.updateNowPlaying(
                artist = metadata.artists.joinToString { it.name },
                track = metadata.title,
                album = metadata.album?.title,
                duration = metadata.duration
            )
        }
    }

    fun onPlayerStateChanged(isPlaying: Boolean, metadata: MediaMetadata?, duration: Long? = null) {
        if (metadata == null) return
        if (isPlaying) {
            if (!songStarted) {
                onSongStart(metadata, duration)
            } else {
                onSongResume(metadata)
            }
        } else {
            onSongPause()
        }
    }
}
