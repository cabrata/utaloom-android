/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.encore

import android.provider.Settings
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.SongWithStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** How long a story holds before the next. Every story's timeline lands well inside it. */
private const val PAGE_MS = 10_000f

/** By now every story has finished arriving; a shared picture is taken from this moment on. */
private const val SETTLED_MS = 5_500f

private const val SWIPE_PX = 120f
private const val BACK_ZONE = 1f / 3f

// ── The stories ─────────────────────────────────────────────────────────────

/**
 * The stories in the order they are told: the small numbers first, then the artist, then the song,
 * then the year laid out, and the summary last because it is the one people send.
 */
private enum class Story(val hue: Float, val revealAtMs: Long = 1_600L) {
    OPENER(0f, 1_200L), MINUTES(40f, 1_400L), BUSIEST_DAY(75f, 1_500L), CLOCK(120f), ARTIST_REVEAL(160f, 3_000L),
    TOP_ARTISTS(205f, 900L), SONG_REVEAL(235f, 2_600L), TOP_SONGS(270f, 900L), ALBUMS(300f, 900L), MONTHS(330f),
    DISCOVERY(20f, 1_200L), SUMMARY(100f, 200L),
}

/**
 * Each story's song, planned for the whole run at once: the number one is kept for its own reveal
 * and the summary, the artist stories get those artists' best songs, the top-songs story the rest
 * of the five, and every other story a different song from further down the year.
 */
private fun planClips(stories: List<Story>, state: EncoreState): Map<Story, SongWithStats?> {
    val songs = state.topSongs
    val first = songs.firstOrNull()
    val used = mutableSetOf<String>().apply { first?.let { add(it.id) } }
    fun claim(song: SongWithStats?) = song?.also { used += it.id }
    // An artist whose only song is already taken still gets it, rather than silence.
    fun by(artist: Artist?): SongWithStats? = artist?.let { a ->
        songs.firstOrNull { song -> song.id !in used && song.artists.any { it.id == a.id } }
            ?: songs.firstOrNull { song -> song.artists.any { it.id == a.id } }
    }
    val planned = HashMap<Story, SongWithStats?>()
    planned[Story.SONG_REVEAL] = first
    planned[Story.SUMMARY] = first
    planned[Story.ARTIST_REVEAL] = claim(by(state.topArtists.getOrNull(0)))
    planned[Story.TOP_ARTISTS] = claim(by(state.topArtists.getOrNull(1)))
    planned[Story.TOP_SONGS] = claim(songs.drop(1).take(4).firstOrNull { it.id !in used })
    // Below the top five first; a short year falls back to the lower end of the five.
    val rest = songs.drop(5) + songs.drop(1).take(4).reversed()
    stories.filter { it !in planned }.forEach { story ->
        planned[story] = claim(rest.firstOrNull { it.id !in used }) ?: first
    }
    return stories.associateWith { planned[it] }
}

private enum class Personality { NIGHT_OWL, EARLY_BIRD, GOLDEN_HOUR, DAYTIME, ALL_DAY }

/** A label only when a stretch of the day clearly leads; anything more even is "all day". */
private fun personalityOf(hours: List<Long>): Personality? {
    val total = hours.sum().toDouble()
    if (total <= 0) return null
    fun share(range: IntRange) = range.sumOf { hours.getOrElse(it) { 0L } } / total
    return when {
        share(22..23) + share(0..3) >= 0.30 -> Personality.NIGHT_OWL
        share(5..9) >= 0.30 -> Personality.EARLY_BIRD
        share(17..21) >= 0.40 -> Personality.GOLDEN_HOUR
        share(10..16) >= 0.50 -> Personality.DAYTIME
        else -> Personality.ALL_DAY
    }
}

private fun monthMs(state: EncoreState): LongArray =
    LongArray(12).also { months -> state.dayMs.forEach { (day, ms) -> months[day.monthValue - 1] += ms } }

private fun storiesFor(state: EncoreState): List<Story> = Story.entries.filter { story ->
    when (story) {
        Story.BUSIEST_DAY -> state.dayMs.values.any { it > 0 }
        Story.CLOCK -> personalityOf(state.hourMs) != null
        Story.ARTIST_REVEAL -> state.topArtists.isNotEmpty()
        Story.TOP_ARTISTS -> state.topArtists.size >= 3
        Story.TOP_SONGS -> state.topSongs.size >= 3
        Story.ALBUMS -> state.top5Albums.size >= 3
        Story.MONTHS -> monthMs(state).count { it > 0 } >= 2
        Story.DISCOVERY -> state.uniqueSongCount > 0
        else -> true
    }
}

/** The artwork a story's colours come from: its subject's, or one of the top songs so the run varies. */
private fun Story.artwork(state: EncoreState): String? = when (this) {
    Story.ARTIST_REVEAL, Story.TOP_ARTISTS -> state.topArtists.firstOrNull()?.thumbnailUrl
    Story.TOP_SONGS -> state.topSongs.getOrNull(1)?.thumbnailUrl
    Story.ALBUMS -> state.top5Albums.firstOrNull()?.thumbnailUrl
    Story.SONG_REVEAL, Story.SUMMARY, Story.OPENER -> null
    else -> state.topSongs.getOrNull(ordinal % state.topSongs.size.coerceAtLeast(1))?.thumbnailUrl
} ?: state.topSongs.firstOrNull()?.thumbnailUrl

// ── The clock ───────────────────────────────────────────────────────────────

/**
 * Time on the story being shown, in ms since it arrived.
 *
 * Every animation on a story is a function of this one number rather than an animation of its own,
 * so holding the story freezes the whole scene at once. It is read inside layer and draw lambdas,
 * which run again on a new frame without composing anything.
 */
private class StoryClock(val elapsed: () -> Float, val reduceMotion: Boolean)

private val LocalClock = compositionLocalOf { StoryClock({ PAGE_MS }, reduceMotion = true) }

private fun window(t: Float, start: Long, duration: Long): Float =
    ((t - start) / duration.coerceAtLeast(1)).coerceIn(0f, 1f)

private fun easeOutCubic(x: Float): Float = 1f - (1f - x).let { it * it * it }

// Material's emphasized decelerate: quick to arrive, long and soft to settle.
private val Settle = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Arrives at [start]: fades in while rising or sliding from its offset and growing from [fromScale]. */
private fun Modifier.enter(
    clock: StoryClock,
    start: Long,
    duration: Long = 700,
    fromY: Dp = 32.dp,
    fromX: Dp = 0.dp,
    fromScale: Float = 1f,
): Modifier = graphicsLayer {
    val p = window(clock.elapsed(), start, duration)
    alpha = easeOutCubic((p * 1.6f).coerceAtMost(1f))
    if (clock.reduceMotion) return@graphicsLayer
    val e = Settle.transform(p)
    translationY = fromY.toPx() * (1f - e)
    translationX = fromX.toPx() * (1f - e)
    val s = fromScale + (1f - fromScale) * e
    scaleX = s
    scaleY = s
}

/** The big reveal: settles down from [from] times its size onto its place, without a bounce. */
private fun Modifier.land(clock: StoryClock, start: Long, duration: Long = 650, from: Float = 1.4f): Modifier =
    graphicsLayer {
        val p = window(clock.elapsed(), start, duration)
        alpha = easeOutCubic((p * 2f).coerceAtMost(1f))
        if (clock.reduceMotion) return@graphicsLayer
        val s = from + (1f - from) * Settle.transform(p)
        scaleX = s
        scaleY = s
    }

// ── Type ────────────────────────────────────────────────────────────────────

private val Setup = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.W800)
private val Huge = TextStyle(fontWeight = FontWeight.W900, letterSpacing = (-3).sp, color = Color.White)
private val Detail = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.W600, color = Color.White)

private fun huge(size: TextUnit) = Huge.copy(fontSize = size, lineHeight = size * 0.95f)

/** A sentence that arrives a word at a time, each word rising a beat after the one before. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KineticText(text: String, start: Long, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.White) {
    val clock = LocalClock.current
    val words = remember(text) { text.split(' ').filter { it.isNotEmpty() } }
    // Each word is its own text, so the gap is set under a typed space to read as one.
    val gap = style.fontSize.value.dp * 0.18f
    FlowRow(
        modifier.clearAndSetSemantics {
            contentDescription = text
            heading()
        },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        words.forEachIndexed { index, word ->
            Text(word, style = style, color = color, modifier = Modifier.enter(clock, start + index * 55L, fromY = 26.dp))
        }
    }
}

@Composable
private fun SetupLine(text: String, start: Long = 120) = KineticText(text, start, Setup)

/** A number running up to [target] between [start] and [start]+[duration], fast at first and settling onto it. */
@Composable
private fun CountUp(
    target: Long,
    start: Long,
    duration: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val clock = LocalClock.current
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val shown by remember(target, start, duration, clock) {
        derivedStateOf {
            val p = if (clock.reduceMotion) 1f else easeOutCubic(window(clock.elapsed(), start, duration))
            (target * p).toLong()
        }
    }
    Text(numbers.format(shown), style = style, color = color, modifier = modifier)
}

// ── The shell ───────────────────────────────────────────────────────────────

@Composable
internal fun EncoreGlassStories(
    state: EncoreState,
    year: Int,
    currentYear: Int,
    onYear: (Int) -> Unit,
    playMusic: Boolean,
    sharing: Boolean,
    /** Shares the picture the given capture returns. */
    onShare: (suspend () -> ImageBitmap) -> Unit,
    onPlaylist: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // TalkBack users move with swipe or custom actions; stories must not advance under them.
    val autoAdvance = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled != true
    }
    val stories = remember(state) { storiesFor(state) }
    val last = stories.lastIndex
    var index by remember(year) { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var held by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var elapsed by remember(year) { mutableFloatStateOf(0f) }
    val paused by rememberUpdatedState(sharing)

    // ── The soundtrack ──
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val clips = if (playMusic) remember { EncoreClipPlayer(context.applicationContext, scope) } else null
    DisposableEffect(clips) { onDispose { clips?.release() } }
    LaunchedEffect(Unit) { playerConnection?.pause() }
    var muted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(muted) { clips?.muted = muted }
    val plan = remember(stories, state) { planClips(stories, state) }
    val starts = remember(state) { mutableStateMapOf<String, Long>() }
    // With music, the run waits for the opening story's best part (or 2.5 s) so the first reveal lands on it.
    var ready by remember(state) { mutableStateOf(clips == null) }

    fun clipOf(story: Story): EncoreClipPlayer.Clip? {
        val song = plan[story] ?: return null
        val start = starts[song.id] ?: EncoreBestPart.known(song.id) ?: EncoreBestPart.fallbackMs(0)
        // Started early, so the moment the clip was chosen for lands with the story's reveal.
        return EncoreClipPlayer.Clip(song.id, (start + EncoreBestPart.LEAD_MS - story.revealAtMs).coerceAtLeast(0))
    }

    LaunchedEffect(plan) {
        if (clips == null) return@LaunchedEffect
        // Looked up three at a time in the order the stories are told, so the first ones are ready first.
        val gate = Semaphore(3)
        plan.values.filterNotNull().distinctBy { it.id }.forEach { song ->
            EncoreBestPart.known(song.id)?.let { starts[song.id] = it }
            launch {
                if (song.id in starts) return@launch
                gate.withPermit { starts[song.id] = EncoreBestPart.startFor(song.id) }
            }
        }
        val first = plan[stories.first()]
        withTimeoutOrNull(2_500) { while (first != null && first.id !in starts) delay(50) }
        clips.prime(clipOf(stories.first()))
        ready = true
    }

    fun goTo(target: Int) {
        if (target !in 0..last || target == index) return
        forward = target > index
        // Reset in the same frame the story changes: the new one's clock reads this.
        elapsed = 0f
        index = target
    }

    fun step(ahead: Boolean) {
        if (ahead && index == last) onClose() else goTo(index + if (ahead) 1 else -1)
    }

    // Frame time, not a tween: "Remove animations" scales tweens to zero and would skip every story.
    LaunchedEffect(index, year) {
        var previous = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            if (!held && !paused && ready) elapsed = (elapsed + (now - previous)).coerceAtMost(PAGE_MS)
            previous = now
            if (elapsed >= PAGE_MS) {
                // The summary stays up: it holds the playlist and is the one people share.
                if (autoAdvance && index < last) goTo(index + 1)
                return@LaunchedEffect
            }
        }
    }

    val story = stories[index.coerceIn(0, last)]

    LaunchedEffect(index, ready) {
        if (!ready || clips == null) return@LaunchedEffect
        clips.play(clipOf(story))
        // The next story's clip loads once this one's fade has finished.
        stories.getOrNull(index + 1)?.let { next ->
            delay(1_200)
            clips.prime(clipOf(next))
        }
    }
    val onScreen by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val away = !onScreen.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(held, sharing, away) { clips?.hold(held || sharing || away) }
    val artworkColors = rememberEncoreMeshColors(story.artwork(state))
    val colors = remember(artworkColors, story) { artworkColors.map { it.hueShifted(story.hue) } }
    val accent = remember(colors) { encoreAccentOf(colors) }
    val picture = rememberGraphicsLayer()

    val previousLabel = stringResource(R.string.encore_recap_previous)
    val nextLabel = stringResource(R.string.encore_recap_next)
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                picture.record { this@drawWithContent.drawContent() }
                drawLayer(picture)
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(previousLabel) { (index > 0).also { if (it) goTo(index - 1) } },
                    CustomAccessibilityAction(nextLabel) { (index < last).also { if (it) goTo(index + 1) } },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        held = true
                        tryAwaitRelease()
                        held = false
                    },
                    // A hold only pauses; it must not also move a story on release.
                    onLongPress = {},
                    onTap = { offset -> step(ahead = offset.x >= size.width * BACK_ZONE) },
                )
            }
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragged += amount
                        change.consume()
                    },
                    onDragEnd = { if (abs(dragged) > SWIPE_PX) goTo(index + if (dragged < 0) 1 else -1) },
                )
            },
    ) {
        EncoreGlassStage(colors)

        AnimatedContent(
            targetState = index,
            transitionSpec = { transitionInto(targetState, forward, reduceMotion) },
            label = "encoreStory",
        ) { shown ->
            val live = shown == index
            val clock = remember(live, reduceMotion) {
                // A story on its way out stays as it finished rather than replaying its entrance.
                if (live) StoryClock({ elapsed }, reduceMotion) else StoryClock({ PAGE_MS }, reduceMotion)
            }
            CompositionLocalProvider(LocalClock provides clock) {
                StoryContent(stories[shown], state, accent, onPlaylist)
            }
        }

        Chrome(
            count = stories.size,
            current = index,
            progress = { elapsed / PAGE_MS },
            year = year,
            years = remember(currentYear, state.availableYears) {
                (listOf(currentYear) + state.availableYears).distinct().sortedDescending()
            },
            onYear = onYear,
            muted = muted.takeIf { clips != null },
            onMute = { muted = !muted },
            onClose = onClose,
            controls = !capturing,
        )

        if (!ready) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Color.White, strokeWidth = 3.dp)
        }

        if (!capturing) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 18.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(enabled = !sharing, role = Role.Button) {
                        onShare {
                            try {
                                capturing = true
                                elapsed = elapsed.coerceAtLeast(SETTLED_MS)
                                // One frame to lay out without the buttons, one to draw it.
                                withFrameNanos { }
                                withFrameNanos { }
                                picture.toImageBitmap()
                            } finally {
                                capturing = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (sharing) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(painterResource(R.drawable.share), stringResource(R.string.encore_recap_share), Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
    }
}

/** Not one slide for all of them: the run alternates between zooming, rising and sweeping, so it cuts rather than pages. */
private fun transitionInto(target: Int, forward: Boolean, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return fadeIn(tween(220)) togetherWith fadeOut(tween(180))
    val enter = tween<Float>(480, easing = Settle)
    val enterOffset = tween<androidx.compose.ui.unit.IntOffset>(480, easing = Settle)
    val exit = tween<Float>(300)
    val exitOffset = tween<androidx.compose.ui.unit.IntOffset>(300)
    return when (target % 3) {
        0 -> (scaleIn(enter, initialScale = if (forward) 0.88f else 1.12f) + fadeIn(enter)) togetherWith
            (scaleOut(exit, targetScale = if (forward) 1.12f else 0.9f) + fadeOut(exit))
        1 -> (slideInVertically(enterOffset) { if (forward) it / 3 else -it / 3 } + fadeIn(enter)) togetherWith
            (slideOutVertically(exitOffset) { if (forward) -it / 6 else it / 6 } + fadeOut(exit))
        else -> (slideInHorizontally(enterOffset) { if (forward) it / 2 else -it / 2 } + fadeIn(enter)) togetherWith
            (slideOutHorizontally(exitOffset) { if (forward) -it / 4 else it / 4 } + fadeOut(exit))
    }
}

/** The buttons, the progress, and the name. [controls] off leaves only the name, for the shared picture. */
@Composable
private fun Chrome(
    count: Int,
    current: Int,
    progress: () -> Float,
    year: Int,
    years: List<Int>,
    onYear: (Int) -> Unit,
    muted: Boolean?,
    onMute: () -> Unit,
    onClose: () -> Unit,
    controls: Boolean,
) {
    val hidden = if (controls) Modifier else Modifier.alpha(0f)
    var showYears by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).then(hidden),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (muted != null) {
                ChromeIcon(
                    if (muted) R.drawable.volume_off else R.drawable.volume_up,
                    stringResource(if (muted) R.string.unmute else R.string.mute),
                    onMute,
                )
                Spacer(Modifier.width(8.dp))
            }
            ChromeIcon(R.drawable.close, stringResource(R.string.encore_close), onClose)
        }
        Spacer(Modifier.height(6.dp))
        val pageLabel = stringResource(R.string.encore_recap_page, current + 1, count)
        Row(
            Modifier.fillMaxWidth().then(hidden).semantics { contentDescription = pageLabel },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(count) { i ->
                Spacer(
                    Modifier.weight(1f).height(3.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .drawBehind {
                            val fraction = when {
                                i < current -> 1f
                                i == current -> progress()
                                else -> 0f
                            }
                            drawRect(Color.White, size = Size(size.width * fraction, size.height))
                        },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                val chooseYear = stringResource(R.string.encore_recap_select_year)
                Row(
                    Modifier.clickable(enabled = controls, role = Role.Button, onClickLabel = chooseYear) { showYears = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.encore_recap_title, year),
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W700),
                        color = Color.White,
                    )
                    if (years.size > 1) {
                        Icon(painterResource(R.drawable.expand_more), null, Modifier.size(18.dp).then(hidden), tint = Color.White)
                    }
                }
                DropdownMenu(expanded = showYears, onDismissRequest = { showYears = false }) {
                    years.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.toString()) },
                            onClick = {
                                onYear(item)
                                showYears = false
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.encore_recap_brand),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W800, letterSpacing = 2.sp),
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun ChromeIcon(icon: Int, description: String, onClick: () -> Unit) {
    Icon(
        painterResource(icon),
        description,
        tint = Color.White,
        modifier = Modifier.size(34.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(5.dp),
    )
}

@Composable
private fun StoryContent(story: Story, state: EncoreState, accent: Color, onPlaylist: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 102.dp, bottom = 76.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            when (story) {
                Story.OPENER -> Opener(state, accent)
                Story.MINUTES -> Minutes(state, accent)
                Story.BUSIEST_DAY -> BusiestDay(state, accent)
                Story.CLOCK -> Clock(state, accent)
                Story.ARTIST_REVEAL -> ArtistReveal(state, accent)
                Story.TOP_ARTISTS -> RankList(
                    stringResource(R.string.encore_story_top_artists),
                    state.topArtists.take(5).mapIndexed { i, artist ->
                        RankItem(i + 1, artist.title, minutesLabel(artist.timeListened), artist.thumbnailUrl)
                    },
                    circular = true,
                    accent = accent,
                )
                Story.SONG_REVEAL -> SongReveal(state, accent)
                Story.TOP_SONGS -> RankList(
                    stringResource(R.string.encore_story_top_songs),
                    state.topSongs.drop(1).take(4).mapIndexed { i, song ->
                        RankItem(i + 2, song.title, song.artists.joinToString { it.name }.ifBlank { song.artistName.orEmpty() }, song.thumbnailUrl)
                    },
                    circular = false,
                    accent = accent,
                )
                Story.ALBUMS -> RankList(
                    stringResource(R.string.encore_story_albums),
                    state.top5Albums.take(5).mapIndexed { i, album ->
                        RankItem(i + 1, album.title, album.artists.joinToString { it.name }, album.thumbnailUrl)
                    },
                    circular = false,
                    accent = accent,
                )
                Story.MONTHS -> Months(state, accent)
                Story.DISCOVERY -> Discovery(state, accent)
                Story.SUMMARY -> Summary(state, accent, onPlaylist)
            }
        }
    }
}

@Composable
private fun minutesLabel(ms: Long?): String =
    stringResource(R.string.encore_recap_artist_detail, NumberFormat.getIntegerInstance().format((ms ?: 0L) / 60_000))

@Composable
private fun Cover(url: String?, size: Dp, shape: Shape, modifier: Modifier = Modifier, elevated: Boolean = false) {
    Box(
        modifier
            .size(size)
            .then(if (elevated) Modifier.shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.music_note), null, Modifier.size(size / 3), tint = Color.White.copy(alpha = 0.7f))
        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun ColumnScope.Opener(state: EncoreState, accent: Color) {
    val clock = LocalClock.current
    val reveal = 1_200L
    SetupLine(stringResource(R.string.encore_story_opener, state.year))
    Spacer(Modifier.height(12.dp))
    // The year, set down a digit at a time onto a wall of the year's covers drifting past behind it.
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        CoverWall(state, start = 60L, modifier = Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.68f),
                    0.7f to Color.Black.copy(alpha = 0.3f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Row(Modifier.clearAndSetSemantics { contentDescription = state.year.toString() }) {
            state.year.toString().forEachIndexed { index, digit ->
                Text(
                    digit.toString(),
                    style = huge(112.sp).copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 6f), blurRadius = 28f)),
                    modifier = Modifier.land(clock, start = 380L + index * 170L, from = 1.8f),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.encore_story_opener_detail),
        style = Detail.copy(fontWeight = FontWeight.W500),
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.enter(clock, reveal + 300),
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * The year's covers as a tilted wall: three rows drifting in turn left and right, wider than the
 * story so they run off both edges, faded out top and bottom. Each row repeats the same few covers,
 * so it loops without a seam. All movement is read in layers off the story's clock.
 */
@Composable
private fun CoverWall(state: EncoreState, start: Long, modifier: Modifier) {
    val clock = LocalClock.current
    val rows = remember(state) {
        val art = (state.topSongs.mapNotNull { it.thumbnailUrl } + state.top5Albums.mapNotNull { it.thumbnailUrl })
            .distinct()
            .take(WALL_ROWS * 7)
        if (art.isEmpty()) {
            emptyList()
        } else {
            List(WALL_ROWS) { r ->
                art.filterIndexed { i, _ -> i % WALL_ROWS == r }.ifEmpty { art.drop(r % art.size) + art.take(r % art.size) }
            }
        }
    }
    if (rows.isEmpty()) return
    Box(
        modifier
            // Wider than the story by a margin each side, so the tilt never shows a corner.
            .layout { measurable, constraints ->
                val bleed = WALL_BLEED.roundToPx()
                val width = constraints.maxWidth + bleed * 2
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(WALL_GAP, Alignment.CenterVertically),
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = -9f
                    if (clock.reduceMotion) {
                        alpha = 0.6f
                        return@graphicsLayer
                    }
                    val p = Settle.transform(window(clock.elapsed(), start, 1_600))
                    val s = 1.15f - 0.15f * p
                    scaleX = s
                    scaleY = s
                    alpha = 0.6f * easeOutCubic((p * 1.8f).coerceAtMost(1f))
                },
        ) {
            rows.forEachIndexed { r, covers ->
                val leftward = r % 2 == 0
                val shown = remember(covers) { List(covers.size + 10) { covers[it % covers.size] } }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WALL_GAP),
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .graphicsLayer {
                            val stepPx = (WALL_COVER + WALL_GAP).toPx()
                            val run = covers.size * stepPx
                            val t = clock.elapsed()
                            val drift = if (clock.reduceMotion) 0f else (t * WALL_SPEED_DP * density / 1000f) % run
                            // Rows sweep in from their own side, then keep drifting the same way.
                            val sweep = if (clock.reduceMotion) 0f else (1f - Settle.transform(window(t, start + r * 120L, 1_300))) * 340.dp.toPx()
                            translationX = if (leftward) sweep - drift - stepPx else drift - run - sweep
                        },
                ) {
                    shown.forEach { url -> Cover(url, WALL_COVER, RoundedCornerShape(10.dp)) }
                }
            }
        }
    }
}

private val WALL_COVER = 96.dp
private val WALL_GAP = 10.dp
private val WALL_BLEED = 90.dp
private const val WALL_ROWS = 3
private const val WALL_SPEED_DP = 16f

@Composable
private fun ColumnScope.Minutes(state: EncoreState, accent: Color) {
    val clock = LocalClock.current
    val reveal = 1_400L
    val countFor = 1_900L
    val landed = reveal + countFor
    val minutes = state.totalMinutes
    SetupLine(stringResource(R.string.encore_story_minutes_start))
    Spacer(Modifier.weight(0.7f))
    CountUp(
        target = minutes,
        start = reveal,
        duration = countFor,
        style = huge(if (minutes >= 1_000_000) 84.sp else 104.sp),
        modifier = Modifier.enter(clock, reveal, duration = 500, fromY = 0.dp, fromScale = 0.7f),
    )
    Text(
        pluralStringResource(R.plurals.encore_story_minutes_word, minutes.toInt().coerceAtLeast(0)),
        style = huge(40.sp),
        color = accent,
        modifier = Modifier.enter(clock, reveal + 250),
    )
    Spacer(Modifier.weight(1f))
    val numbers = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 } }
    val days = minutes / 1_440.0
    KineticText(
        if (days >= 1) {
            stringResource(R.string.encore_story_minutes_days, numbers.format(days))
        } else {
            stringResource(R.string.encore_story_minutes_hours, numbers.format(minutes / 60))
        },
        start = landed + 300,
        style = Setup.copy(fontSize = 26.sp, lineHeight = 30.sp),
    )
}

@Composable
private fun ColumnScope.BusiestDay(state: EncoreState, accent: Color) {
    val clock = LocalClock.current
    val context = LocalContext.current
    val reveal = 1_500L
    val (busiest, busiestMs) = remember(state) { state.dayMs.maxBy { it.value }.toPair() }
    val dayFormat = remember(context) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMd"))
    }
    SetupLine(stringResource(R.string.encore_story_busiest_start))
    Spacer(Modifier.height(10.dp))
    Text(
        busiest.format(dayFormat),
        style = huge(58.sp),
        color = accent,
        modifier = Modifier.land(clock, reveal, from = 1.5f),
    )
    Spacer(Modifier.height(18.dp))
    YearGrid(state, busiest, accent, reveal, Modifier.fillMaxWidth().weight(1f))
    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.encore_story_busiest_minutes, NumberFormat.getIntegerInstance().format(busiestMs / 60_000)),
        style = Detail,
        modifier = Modifier.enter(clock, reveal + 500),
    )
}

/** The whole year as twelve rows of days, each lit by how much was played on it, drawing in a month at a time. */
@Composable
private fun YearGrid(state: EncoreState, busiest: LocalDate, accent: Color, reveal: Long, modifier: Modifier) {
    val clock = LocalClock.current
    val cells = remember(state) {
        val max = state.dayMs.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        Array(12) { m ->
            val month = java.time.YearMonth.of(state.year, m + 1)
            FloatArray(31) { d ->
                if (d >= month.lengthOfMonth()) -1f else (state.dayMs[month.atDay(d + 1)] ?: 0L).toFloat() / max
            }
        }
    }
    val monthLabels = remember { Month.entries.map { it.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()) } }
    Row(modifier) {
        Column(Modifier.padding(end = 6.dp)) {
            monthLabels.forEachIndexed { m, label ->
                Text(
                    label,
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.W700),
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f).enter(clock, 200L + m * 70L, fromY = 0.dp, fromX = (-12).dp),
                )
            }
        }
        Spacer(
            Modifier.weight(1f).fillMaxSize().drawBehind {
                val t = clock.elapsed()
                val rowH = size.height / 12f
                val cell = minOf(size.width / 31f, rowH)
                val box = cell * 0.82f
                for (m in 0 until 12) {
                    val rowIn = easeOutCubic(window(t, 200L + m * 70L, 420))
                    if (rowIn <= 0f) continue
                    val y = m * rowH + (rowH - box) / 2f
                    for (d in 0 until 31) {
                        val v = cells[m][d]
                        if (v < 0f) continue
                        val lit = if (v > 0f) 0.25f + 0.75f * v else 0.08f
                        drawRoundRect(
                            color = (if (v > 0f) accent else Color.White).copy(alpha = lit * rowIn),
                            topLeft = Offset(d * cell, y),
                            size = Size(box, box),
                            cornerRadius = CornerRadius(box * 0.28f),
                        )
                    }
                }
                val p = window(t, reveal, 900)
                if (p > 0f) {
                    val cx = (busiest.dayOfMonth - 1) * cell + box / 2f
                    val cy = (busiest.monthValue - 1) * rowH + rowH / 2f
                    drawRoundRect(Color.White, Offset(cx - box / 2f, cy - box / 2f), Size(box, box), CornerRadius(box * 0.28f))
                    // One ring spreading out from the day, once.
                    val ring = if (clock.reduceMotion) 1f else easeOutCubic(p)
                    if (ring < 0.99f) {
                        drawCircle(Color.White.copy(alpha = 1f - ring), box * (1f + ring * 6f), Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    }
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.Clock(state: EncoreState, accent: Color) {
    val clock = LocalClock.current
    val context = LocalContext.current
    val reveal = 1_600L
    val personality = remember(state) { personalityOf(state.hourMs) } ?: return
    val peak = remember(state) { state.hourMs.indices.maxBy { state.hourMs[it] } }
    val hours = remember(state) {
        val max = state.hourMs.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        FloatArray(24) { state.hourMs.getOrElse(it) { 0L }.toFloat() / max }
    }
    val hourLabel = remember(peak, context) {
        LocalTime.of(peak, 0).format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h a"))
    }
    SetupLine(stringResource(R.string.encore_story_clock_start))
    Spacer(Modifier.weight(0.4f))
    Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = clock.elapsed()
                val outer = size.minDimension / 2f
                val inner = outer * 0.42f
                for (h in 0 until 24) {
                    val grow = Settle.transform(window(t, BAR_START + h * BAR_STAGGER, BAR_GROW))
                    if (grow <= 0f) continue
                    val angle = (h / 24f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    val length = (outer - inner) * (0.12f + 0.88f * hours[h]) * grow
                    val isPeak = h == peak
                    drawLine(
                        color = if (isPeak && t > reveal) accent else Color.White.copy(alpha = 0.55f),
                        start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                        end = Offset(center.x + cos(angle) * (inner + length), center.y + sin(angle) * (inner + length)),
                        strokeWidth = (if (isPeak) 9.dp else 5.dp).toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
        )
        Text(hourLabel, style = huge(34.sp).copy(letterSpacing = (-1).sp), modifier = Modifier.land(clock, reveal, from = 1.6f))
    }
    Spacer(Modifier.weight(0.5f))
    val (title, detail) = when (personality) {
        Personality.NIGHT_OWL -> R.string.encore_story_night_owl to R.string.encore_story_night_owl_detail
        Personality.EARLY_BIRD -> R.string.encore_story_early_bird to R.string.encore_story_early_bird_detail
        Personality.GOLDEN_HOUR -> R.string.encore_story_golden_hour to R.string.encore_story_golden_hour_detail
        Personality.DAYTIME -> R.string.encore_story_daytime to R.string.encore_story_daytime_detail
        Personality.ALL_DAY -> R.string.encore_story_all_day to R.string.encore_story_all_day_detail
    }
    Text(
        stringResource(title),
        style = huge(50.sp).copy(letterSpacing = (-1.5).sp),
        color = accent,
        modifier = Modifier.enter(clock, reveal + 650, fromScale = 0.7f, fromY = 0.dp),
    )
    Spacer(Modifier.height(8.dp))
    Text(stringResource(detail), style = Detail, modifier = Modifier.enter(clock, reveal + 1_000))
}

private const val BAR_START = 150L
private const val BAR_STAGGER = 45L
private const val BAR_GROW = 600L

@Composable
private fun ColumnScope.ArtistReveal(state: EncoreState, accent: Color) {
    val clock = LocalClock.current
    val reveal = 3_000L
    val top = state.topArtists.firstOrNull() ?: return
    SetupLine(stringResource(R.string.encore_story_artist_start))
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        // The countdown: the rest of the top five pass by before the one.
        val runners = state.topArtists.drop(1).take(4).reversed()
        val step = if (runners.isEmpty()) 0L else ((reveal - 1_400L) / runners.size).coerceAtMost(420L)
        runners.forEachIndexed { index, artist ->
            val rank = runners.size - index + 1
            val from = 1_400L + index * step
            Text(
                "#$rank  ${artist.title}",
                style = huge(34.sp).copy(letterSpacing = (-1).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.graphicsLayer {
                    val p = window(clock.elapsed(), from, step.coerceAtLeast(1))
                    alpha = if (p <= 0f || p >= 1f) 0f else sin(p * PI.toFloat())
                    if (!clock.reduceMotion) {
                        val s = 0.85f + 0.25f * p
                        scaleX = s
                        scaleY = s
                    }
                },
            )
        }
        Cover(top.thumbnailUrl, 210.dp, CircleShape, Modifier.land(clock, reveal, from = 1.5f), elevated = true)
    }
    KineticText(top.title, start = reveal + 420, style = huge(46.sp).copy(letterSpacing = (-1.5).sp))
    Spacer(Modifier.height(8.dp))
    Text(minutesLabel(top.timeListened), style = Detail, color = accent, modifier = Modifier.enter(clock, reveal + 900))
}

@Composable
private fun ColumnScope.SongReveal(state: EncoreState, accent: Color) {
    val clock = LocalClock.current
    val reveal = 2_600L
    val song = state.topSongs.firstOrNull() ?: return
    val numbers = remember { NumberFormat.getIntegerInstance() }
    SetupLine(stringResource(R.string.encore_story_song_start))
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        // The wait: a blank sleeve, until the record turns over.
        Box(
            Modifier
                .size(230.dp)
                .graphicsLayer { alpha = 1f - window(clock.elapsed(), reveal - 120, 200) }
                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .enter(clock, 500, fromY = 0.dp, fromScale = 0.92f),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = huge(110.sp), color = Color.White.copy(alpha = 0.75f))
        }
        Cover(
            song.thumbnailUrl, 236.dp, RoundedCornerShape(12.dp),
            Modifier
                .graphicsLayer {
                    if (clock.reduceMotion) return@graphicsLayer
                    // The sleeve turning over as it lands.
                    rotationY = 90f * (1f - Settle.transform(window(clock.elapsed(), reveal, 700)))
                    cameraDistance = 14f * density
                }
                .land(clock, reveal, duration = 700, from = 1.2f),
            elevated = true,
        )
    }
    // The title typed out a letter at a time, as far as two lines can show.
    val typed by remember(song.title, clock) {
        derivedStateOf {
            if (clock.reduceMotion) song.title.length
            else ((clock.elapsed() - reveal - 250) / 38).toInt().coerceIn(0, song.title.length)
        }
    }
    Text(
        song.title.take(typed),
        style = huge(42.sp).copy(letterSpacing = (-1.2).sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { contentDescription = song.title },
    )
    Text(
        song.artists.joinToString { it.name }.ifBlank { song.artistName.orEmpty() },
        style = Detail.copy(fontSize = 21.sp),
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.enter(clock, reveal + 600),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(
            R.string.encore_recap_song_detail,
            numbers.format(song.songCountListened),
            numbers.format((song.timeListened ?: 0L) / 60_000),
        ),
        style = Detail,
        modifier = Modifier.enter(clock, reveal + 900),
    )
}

private data class RankItem(val rank: Int, val title: String, val subtitle: String?, val artworkUrl: String?)

/** A chart of up to five, sweeping in a row at a time, the first one larger. */
@Composable
private fun ColumnScope.RankList(title: String, rows: List<RankItem>, circular: Boolean, accent: Color) {
    val clock = LocalClock.current
    val reveal = 900L
    SetupLine(title)
    Spacer(Modifier.weight(0.4f))
    rows.forEachIndexed { index, row ->
        val lead = index == 0
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp)
                .semantics(mergeDescendants = true) {}
                .enter(clock, reveal + index * 160L, duration = 750, fromX = 120.dp, fromY = 0.dp),
        ) {
            Text(
                "${row.rank}",
                style = huge(if (lead) 46.sp else 32.sp).copy(letterSpacing = (-1).sp),
                color = if (lead) accent else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.width(44.dp),
            )
            Cover(row.artworkUrl, if (lead) 96.dp else 62.dp, if (circular) CircleShape else RoundedCornerShape(6.dp), elevated = lead)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = TextStyle(fontSize = if (lead) 24.sp else 19.sp, fontWeight = FontWeight.W800),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W500),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))
}

/** Twelve bars rising a month at a time; the biggest month takes the colour. */
@Composable
private fun ColumnScope.Months(state: EncoreState, accent: Color) {
    val clock = LocalClock.current
    val months = remember(state) { monthMs(state) }
    val peak = remember(months) { months.indices.maxBy { months[it] } }
    val max = months.max().coerceAtLeast(1L)
    val reveal = 500L + 11 * 90L + 600L
    SetupLine(stringResource(R.string.encore_story_months))
    Spacer(Modifier.weight(0.5f))
    Row(Modifier.fillMaxWidth().weight(1.4f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        months.forEachIndexed { m, ms ->
            val share = ms.toFloat() / max
            Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(
                    Modifier.weight(1f).fillMaxWidth().drawBehind {
                        val grow = if (clock.reduceMotion) 1f else Settle.transform(window(clock.elapsed(), 500L + m * 90L, 800))
                        val height = size.height * (0.03f + 0.97f * share) * grow
                        val lit = m == peak && clock.elapsed() > reveal
                        drawRoundRect(
                            color = if (lit) accent else Color.White.copy(alpha = if (ms > 0) 0.55f else 0.15f),
                            topLeft = Offset(0f, size.height - height),
                            size = Size(size.width, height),
                            cornerRadius = CornerRadius(size.width * 0.3f),
                        )
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    Month.of(m + 1).getDisplayName(DateTextStyle.NARROW, Locale.getDefault()),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W700),
                    color = Color.White.copy(alpha = if (m == peak) 1f else 0.6f),
                    modifier = Modifier.enter(clock, 500L + m * 90L, fromY = 8.dp),
                )
            }
        }
    }
    Spacer(Modifier.weight(0.5f))
    KineticText(
        stringResource(R.string.encore_story_months_peak, Month.of(peak + 1).getDisplayName(DateTextStyle.FULL_STANDALONE, Locale.getDefault())),
        start = reveal,
        style = Setup.copy(fontSize = 26.sp, lineHeight = 30.sp),
        color = Color.White,
    )
    Spacer(Modifier.height(6.dp))
    Text(minutesLabel(months[peak]), style = Detail, color = accent, modifier = Modifier.enter(clock, reveal + 500))
}

@Composable
private fun ColumnScope.Discovery(state: EncoreState, accent: Color) {
    val clock = LocalClock.current
    val reveal = 1_200L
    SetupLine(stringResource(R.string.encore_story_discovery_start))
    Spacer(Modifier.weight(0.5f))
    listOf(
        state.uniqueSongCount to R.plurals.encore_story_songs_word,
        state.uniqueArtistCount to R.plurals.encore_story_artists_word,
        state.totalAlbums to R.plurals.encore_story_albums_word,
    ).filter { it.first > 0 }.forEachIndexed { index, (count, word) ->
        val start = reveal + index * 550L
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.semantics(mergeDescendants = true) {}.enter(clock, start, fromX = (-120).dp, fromY = 0.dp),
        ) {
            CountUp(count.toLong(), start, 1_300, huge(80.sp), color = if (index == 0) accent else Color.White)
            Spacer(Modifier.width(10.dp))
            Text(
                pluralStringResource(word, count),
                style = huge(28.sp).copy(letterSpacing = (-0.5).sp),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.Summary(state: EncoreState, accent: Color, onPlaylist: () -> Unit) {
    val clock = LocalClock.current
    val artist = state.topArtists.firstOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(
            artist?.thumbnailUrl ?: state.topSongs.firstOrNull()?.thumbnailUrl,
            112.dp,
            if (artist != null) CircleShape else RoundedCornerShape(8.dp),
            Modifier.land(clock, 200, from = 1.4f),
            elevated = true,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.encore_story_summary_title, state.year),
            style = huge(44.sp).copy(letterSpacing = (-1.5).sp),
            modifier = Modifier.enter(clock, 450),
        )
    }
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth()) {
        SummaryColumn(stringResource(R.string.encore_recap_top_artists), state.topArtists.take(5).map { it.title }, 700, accent, Modifier.weight(1f))
        Spacer(Modifier.width(14.dp))
        SummaryColumn(stringResource(R.string.encore_recap_top_songs), state.topSongs.take(5).map { it.title }, 950, accent, Modifier.weight(1f))
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth()) {
        SummaryStat(stringResource(R.string.encore_recap_minutes), NumberFormat.getIntegerInstance().format(state.totalMinutes), 1_900, Modifier.weight(1f))
        state.topAlbum?.let {
            Spacer(Modifier.width(14.dp))
            SummaryStat(stringResource(R.string.encore_top_album_title), it.title, 2_100, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(20.dp))
    val creating = state.playlistCreationState == PlaylistCreationState.Creating
    val playlistLabel = stringResource(
        when {
            state.playlistId != null -> R.string.encore_recap_playlist_open
            state.playlistError -> R.string.encore_recap_playlist_error
            else -> R.string.encore_create_playlist
        },
    )
    Box(
        Modifier
            .enter(clock, 2_400)
            .height(48.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = !creating, role = Role.Button, onClick = onPlaylist)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (creating) {
            CircularProgressIndicator(Modifier.size(20.dp), color = EncoreStageBase, strokeWidth = 2.dp)
        } else {
            Text(playlistLabel, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W700), color = EncoreStageBase, maxLines = 1)
        }
    }
}

@Composable
private fun SummaryColumn(title: String, names: List<String>, start: Long, accent: Color, modifier: Modifier) {
    val clock = LocalClock.current
    Column(modifier) {
        Text(
            title,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W700),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.enter(clock, start),
        )
        Spacer(Modifier.height(6.dp))
        names.forEachIndexed { index, name ->
            Row(Modifier.padding(vertical = 3.dp).enter(clock, start + 120L + index * 110L, fromY = 18.dp)) {
                Text(
                    "${index + 1}",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W900),
                    color = accent,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    name,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W700),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, start: Long, modifier: Modifier) {
    val clock = LocalClock.current
    Column(modifier.semantics(mergeDescendants = true) {}.enter(clock, start, fromScale = 0.85f)) {
        Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W700), color = Color.White.copy(alpha = 0.7f))
        Text(value, style = huge(30.sp).copy(letterSpacing = (-0.8).sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
