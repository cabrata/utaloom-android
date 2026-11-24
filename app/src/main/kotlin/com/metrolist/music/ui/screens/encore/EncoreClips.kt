/*
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord:
 * the "Most replayed" best part and the two-deck clip player of its Replay year story.
 */
package com.metrolist.music.ui.screens.encore

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** YouTube's "Most replayed" curve for a video: how much each [segmentMs] stretch is replayed, 0..1. */
internal data class Heatmap(val segmentMs: Long, val intensity: List<Float>) {
    val durationMs: Long get() = segmentMs * intensity.size
}

/**
 * Where a song's best part starts, from what listeners do with it rather than from its audio:
 * not where it is loudest but where people keep dragging the scrubber back to.
 */
internal object EncoreBestPart {

    /** Nothing in a song's opening seconds counts: everyone "replays" the start. */
    private const val SKIP_HEAD_MS = 5_000L

    /** A best part needs some song left after it to be a clip at all. */
    private const val SKIP_TAIL_MS = 30_000L

    /** Interludes and skits have no best part to skip to. */
    private const val MIN_SONG_MS = 60_000L

    /** Fewer markers than this is a video with too few views to chart. */
    private const val MIN_MARKERS = 10

    /** How far, as a share, the peak has to stand above the trend around it… */
    private const val MIN_RISE = 0.08f

    /** …and at least this much in absolute terms, since curves are 0..1. */
    private const val MIN_LIFT = 0.03f

    /** One hot second shouldn't beat a hot chorus. */
    private const val SMOOTH_HALF_MS = 4_500.0

    /** The trend around a moment is the median of the curve this far either side. */
    private const val TREND_HALF_MS = 45_000.0

    /** So a near-silent stretch can't make any bump look huge. */
    private const val TREND_FLOOR = 0.05f

    private const val FALLBACK_SHARE = 0.30
    private const val FALLBACK_MIN_MS = 20_000L
    private const val FALLBACK_UNKNOWN_MS = 45_000L

    /** A clip opens this long before the moment it is chosen for, so it doesn't start mid-word. */
    const val LEAD_MS = 1_000L

    private const val LOOKUP_TIMEOUT_MS = 8_000L

    // shortcut: kept in memory only, so each app run asks YouTube again; store on disk if that shows in data use.
    private val starts = ConcurrentHashMap<String, Long>()

    /** What is already known about [videoId], without asking anything. */
    fun known(videoId: String): Long? = starts[videoId]

    /** Where [videoId]'s clip should start. Always returns: at worst a fixed point past the intro. */
    suspend fun startFor(videoId: String): Long {
        starts[videoId]?.let { return it }
        val answer = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { YouTube.webNext(videoId).getOrNull() }
        val start = pick(answer?.let(::parseHeatmap))
        // No answer is not an answer about the video: the network may be back next time.
        if (answer != null) starts[videoId] = start
        return start
    }

    fun pick(heat: Heatmap?): Long =
        heat?.let { peakMs(it) }?.let { (it - LEAD_MS).coerceAtLeast(0) } ?: fallbackMs(heat?.durationMs ?: 0)

    /** Somewhere past the intro for a song nothing else could place. */
    fun fallbackMs(durationMs: Long): Long = when {
        durationMs <= 0 -> FALLBACK_UNKNOWN_MS
        durationMs < MIN_SONG_MS -> 0L
        else -> maxOf(FALLBACK_MIN_MS, (durationMs * FALLBACK_SHARE).toLong()).coerceAtMost(durationMs - SKIP_TAIL_MS)
    }

    /**
     * The heat markers in a youtube.com `next` answer, or null when the video has none. They sit in a
     * `macroMarkersListEntity` whose path has moved more than once, so it is looked for anywhere.
     */
    fun parseHeatmap(answer: JsonObject): Heatmap? {
        val entity = findAll(answer, "macroMarkersListEntity")
            .filterIsInstance<JsonObject>()
            .firstOrNull { it.obj("markersList")?.str("markerType") == "MARKER_TYPE_HEATMAP" }
            ?: return null
        val markers = (entity.obj("markersList")?.get("markers") as? JsonArray)
            ?.mapNotNull { element ->
                val marker = element as? JsonObject ?: return@mapNotNull null
                val start = marker.str("startMillis")?.toLongOrNull() ?: return@mapNotNull null
                val length = marker.str("durationMillis")?.toLongOrNull() ?: return@mapNotNull null
                val heat = marker.str("intensityScoreNormalized")?.toFloatOrNull() ?: return@mapNotNull null
                Triple(start, length, heat.coerceIn(0f, 1f))
            }
            ?.sortedBy { it.first }
            ?: return null
        if (markers.size < MIN_MARKERS) return null
        // The median, in case the last segment is cut short.
        val segment = markers.map { it.second }.sorted()[markers.size / 2]
        if (segment <= 0) return null
        return Heatmap(segment, markers.map { it.third })
    }

    /**
     * Where the most replayed stretch begins, or null when nothing stands out. A curve mostly falls
     * from start to end as people drift off, so its maximum is nearly always the opening; what marks
     * the best part is a bump above that falling trend.
     */
    fun peakMs(heat: Heatmap): Long? {
        val duration = heat.durationMs
        val n = heat.intensity.size
        if (duration < MIN_SONG_MS || n < MIN_MARKERS) return null
        val seg = heat.segmentMs
        val half = (SMOOTH_HALF_MS / seg).roundToInt().coerceAtLeast(1)
        val smooth = FloatArray(n) { i ->
            val range = (i - half).coerceAtLeast(0)..(i + half).coerceAtMost(n - 1)
            range.sumOf { heat.intensity[it].toDouble() }.toFloat() / (range.last - range.first + 1)
        }
        val wide = (TREND_HALF_MS / seg).roundToInt().coerceAtLeast(3)
        val trend = FloatArray(n) { i ->
            // The opening segment is left out: its heat is everyone pressing play.
            val window = ((i - wide).coerceAtLeast(1)..(i + wide).coerceAtMost(n - 1)).map { smooth[it] }.sorted()
            window[window.size / 2]
        }

        fun rise(i: Int) = smooth[i] / trend[i].coerceAtLeast(TREND_FLOOR) - 1f

        val head = maxOf(SKIP_HEAD_MS, 2 * seg)
        val candidates = (0 until n).filter { i -> i * seg >= head && i * seg <= duration - SKIP_TAIL_MS }
        if (candidates.isEmpty()) return null
        val peak = candidates.maxBy { rise(it) }
        if (rise(peak) < MIN_RISE || smooth[peak] - trend[peak] < MIN_LIFT) return null

        // Back to where the heat starts climbing towards the peak: where the chorus starts, not where it's hottest.
        val floor = rise(peak) * 0.5f
        var start = peak
        while (start - 1 in candidates && rise(start - 1) >= floor) start--
        // The smoothed curve climbs gradually; the raw segments say where it jumps.
        val jump = (start..peak).firstOrNull { heat.intensity[it] / trend[it].coerceAtLeast(TREND_FLOOR) - 1f >= floor } ?: start
        return jump * seg
    }

    private fun findAll(element: JsonElement, key: String): List<JsonElement> = buildList {
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) -> if (k == key) add(v) else walk(v) }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    // YouTube sends the times as strings and the heat as a number.
    private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
}

/**
 * The story's soundtrack: one short clip per story, faded across from one to the next.
 *
 * Its own players, not the app's: through the main player the clips would land in the queue and
 * the history and count towards next year's Encore. Two of them, so the next story's clip is
 * already loaded at its start point while this one plays, and the reveal it is timed to doesn't
 * land in silence. Audio focus is not asked for; the story pauses the app's music itself.
 *
 * Main thread only, like ExoPlayer.
 */
internal class EncoreClipPlayer(private val context: Context, private val scope: CoroutineScope) {

    /** [songId] from [startMs]. [key] tells one clip from another across stories. */
    class Clip(val songId: String, val startMs: Long) {
        val key = "$songId@$startMs"
    }

    private class Deck(val player: ExoPlayer) {
        var key: String? = null
        var fade: Job? = null
        var load: Job? = null
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val streams = HashMap<String, Uri>()
    private val decks = Array(2) { Deck(newPlayer()) }
    private var live = 0
    private var held = false

    var muted: Boolean = false
        set(value) {
            field = value
            val deck = decks[live]
            if (deck.key != null) fade(deck, if (value) 0f else 1f, MUTE_FADE_MS)
        }

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
        repeatMode = Player.REPEAT_MODE_OFF
        volume = 0f
    }

    /** Loads [clip] on the waiting deck, so [play] can start it without a gap. */
    fun prime(clip: Clip?) {
        if (clip == null || decks[live].key == clip.key) return
        val deck = decks[1 - live]
        if (deck.key == clip.key) return
        // The waiting deck is usually still fading the last story out; loading over it would cut that dead.
        val fading = deck.fade
        scope.launch {
            fading?.join()
            if (decks[live] !== deck && deck.key != clip.key) load(deck, clip)
        }
    }

    /** Fades the clip being heard out and [clip] in. Null fades to silence. Already playing [clip] carries on. */
    fun play(clip: Clip?) {
        val current = decks[live]
        if (clip != null && current.key == clip.key) {
            if (!held) current.player.play()
            return
        }
        stop(current, FADE_OUT_MS)
        if (clip == null) return
        val next = decks[1 - live]
        if (next.key != clip.key) load(next, clip)
        live = 1 - live
        next.player.volume = 0f
        next.player.playWhenReady = !held
        fade(next, if (muted) 0f else 1f, FADE_IN_MS)
    }

    /** A finger is on the story, the share sheet is up, or the app is away: everything holds. */
    fun hold(holding: Boolean) {
        if (held == holding) return
        held = holding
        val deck = decks[live]
        if (deck.key != null) deck.player.playWhenReady = !holding
    }

    /**
     * Fades out and lets go of both players, on a scope of its own: this runs as the story leaves,
     * which is when the story's scope is cancelled, and a fade there would never release its player.
     */
    fun release() {
        val after = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        decks.forEach { deck ->
            deck.fade?.cancel()
            deck.load?.cancel()
            val player = deck.player
            if (!player.isPlaying) {
                player.release()
                return@forEach
            }
            after.launch {
                try {
                    ramp(player, 0f, FADE_OUT_MS)
                } finally {
                    player.release()
                }
            }
        }
    }

    private fun load(deck: Deck, clip: Clip) {
        deck.fade?.cancel()
        deck.load?.cancel()
        deck.player.volume = 0f
        deck.player.stop()
        deck.key = clip.key
        deck.load = scope.launch {
            val uri = streams[clip.songId]
                ?: resolveEncoreStream(context, connectivity, clip.songId)?.also { streams[clip.songId] = it }
                ?: return@launch
            if (deck.key != clip.key) return@launch
            deck.player.setMediaItem(MediaItem.fromUri(uri), clip.startMs)
            deck.player.prepare()
        }
    }

    private fun stop(deck: Deck, millis: Long) {
        if (deck.key == null) return
        deck.fade?.cancel()
        deck.load?.cancel()
        deck.key = null
        deck.fade = scope.launch {
            ramp(deck.player, 0f, millis)
            deck.player.pause()
            deck.player.stop()
        }
    }

    private fun fade(deck: Deck, to: Float, millis: Long) {
        deck.fade?.cancel()
        deck.fade = scope.launch { ramp(deck.player, to, millis) }
    }

    private suspend fun ramp(player: ExoPlayer, to: Float, millis: Long) {
        val from = player.volume
        val steps = (millis / STEP_MS).coerceAtLeast(1)
        for (i in 1..steps) {
            delay(STEP_MS)
            val t = i.toFloat() / steps
            // Rising fast and falling late: straight lines both ways sound like a dip mid-crossfade.
            val eased = if (to > from) kotlin.math.sqrt(t) else t * t
            player.volume = from + (to - from) * eased
        }
        player.volume = to
    }

    private companion object {
        const val FADE_IN_MS = 500L
        const val FADE_OUT_MS = 420L
        const val MUTE_FADE_MS = 200L
        const val STEP_MS = 16L
    }
}
