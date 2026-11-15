/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

sealed class WrappedScreenType {
    object Welcome : WrappedScreenType()
    object MinutesReveal : WrappedScreenType()
    object TopSongReveal : WrappedScreenType()
    object Top5Songs : WrappedScreenType()
    object TopArtistReveal : WrappedScreenType()
    object Conclusion : WrappedScreenType()
}

private const val STORY_DURATION_MS = 7_000

private data class StoryStyle(val background: Color, val content: Color, val accent: Color, val shapes: Int)

// Accents sit behind text, so each pair keeps at least 3:1 against the content color.
private fun WrappedScreenType.style() = when (this) {
    WrappedScreenType.Welcome -> StoryStyle(WrappedInk, WrappedPeach, WrappedEmber, 0)
    WrappedScreenType.MinutesReveal -> StoryStyle(WrappedCoral, WrappedInk, WrappedPeach, 1)
    WrappedScreenType.TopSongReveal -> StoryStyle(WrappedPlum, WrappedPaper, WrappedEmber, 2)
    WrappedScreenType.Top5Songs -> StoryStyle(WrappedPeach, WrappedInk, WrappedCoral, 1)
    WrappedScreenType.TopArtistReveal -> StoryStyle(WrappedEmber, WrappedPaper, WrappedPlum, 0)
    WrappedScreenType.Conclusion -> StoryStyle(WrappedInk, WrappedPaper, WrappedEmber, 2)
}

@Composable
internal fun rememberWrappedYear(): Int {
    var year by remember { mutableStateOf(LocalDate.now().year) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = ZonedDateTime.now()
                year = now.year
                val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, tomorrow).toMillis().coerceAtLeast(1))
            }
        }
    }
    return year
}

@Composable
fun WrappedScreen(playMusic: Boolean = true) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val manager = remember { provideWrappedManager(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val currentYear = rememberWrappedYear()
    var chosenYear by rememberSaveable { mutableStateOf<Int?>(null) }
    val year = chosenYear ?: currentYear
    var showYears by remember { mutableStateOf(false) }
    val screens = remember(state.topArtists.isNotEmpty()) {
        buildList {
            add(WrappedScreenType.Welcome)
            add(WrappedScreenType.MinutesReveal)
            add(WrappedScreenType.TopSongReveal)
            add(WrappedScreenType.Top5Songs)
            if (state.topArtists.isNotEmpty()) add(WrappedScreenType.TopArtistReveal)
            add(WrappedScreenType.Conclusion)
        }
    }
    val pager = rememberPagerState(pageCount = { screens.size })
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val layer = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val shareError = stringResource(R.string.wrapped_recap_share_error)
    val ready = state.isDataReady && !state.isLoading && state.year == year
    val stories = ready && !state.hasError && state.topSongs.isNotEmpty()
    val style = (if (stories) screens.getOrElse(pager.currentPage) { screens.last() } else WrappedScreenType.Welcome).style()
    val background by animateColorAsState(style.background, label = "wrapped_background")
    val content by animateColorAsState(style.content, label = "wrapped_content")
    // TalkBack users move with swipe or custom actions; stories must not advance under them.
    val autoAdvance = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled != true
    }
    val audio = if (playMusic && stories) rememberWrappedMusic(state.topSongs.first().id) else null
    val muted = audio?.isMuted?.collectAsStateWithLifecycle()?.value == true
    fun goTo(target: Int): Boolean {
        if (target !in 0 until pager.pageCount) return false
        scope.launch { pager.animateScrollToPage(target) }
        return true
    }

    val onClose: () -> Unit = {
        if (ready && state.topSongs.isNotEmpty()) {
            navController.previousBackStackEntry?.savedStateHandle?.set("wrapped_seen_year", state.year)
        }
        navController.popBackStack()
    }
    BackHandler(onBack = onClose)
    DisposableEffect(manager) {
        onDispose { manager.dispose() }
    }
    LaunchedEffect(year) {
        // The pager is not composed while loading and scrollToPage waits for its first layout.
        launch { pager.scrollToPage(0) }
        manager.prepare(year, force = true)
    }

    val activity = LocalActivity.current
    val bars = remember(activity) { activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) } }
    DisposableEffect(bars) {
        val lightStatus = bars?.isAppearanceLightStatusBars
        val lightNavigation = bars?.isAppearanceLightNavigationBars
        onDispose {
            lightStatus?.let { bars?.isAppearanceLightStatusBars = it }
            lightNavigation?.let { bars?.isAppearanceLightNavigationBars = it }
        }
    }
    val lightBars = style.background.luminance() > 0.5f
    SideEffect {
        bars?.isAppearanceLightStatusBars = lightBars
        bars?.isAppearanceLightNavigationBars = lightBars
    }

    var progress by remember { mutableFloatStateOf(0f) }
    var progressPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(stories, pager.currentPage, held, year) {
        if (!stories) return@LaunchedEffect
        val page = pager.currentPage
        if (progressPage != page) {
            progress = 0f
            progressPage = page
        }
        if (page == pager.pageCount - 1) {
            progress = 1f
            return@LaunchedEffect
        }
        if (held || !autoAdvance) return@LaunchedEffect
        // Frame time, not a tween: "Remove animations" scales tweens to zero and would skip every story.
        val from = progress
        val start = withFrameMillis { it }
        while (progress < 1f) {
            progress = (from + (withFrameMillis { it } - start) / STORY_DURATION_MS.toFloat()).coerceAtMost(1f)
        }
        // Outside this effect: the page change it causes would otherwise cancel the scroll halfway.
        scope.launch { pager.animateScrollToPage(page + 1) }
    }

    Box(Modifier.fillMaxSize().background(background)) {
        CompositionLocalProvider(LocalContentColor provides content) {
            when {
                state.hasError -> StoryColumn {
                    StoryTitle(stringResource(R.string.wrapped_recap_error))
                    StoryButton(stringResource(R.string.retry), style) {
                        scope.launch { manager.prepare(year, force = true) }
                    }
                }
                !ready -> StoryColumn {
                    CircularProgressIndicator(color = content)
                    Text(stringResource(R.string.wrapped_recap_loading))
                }
                state.topSongs.isEmpty() -> StoryColumn {
                    Icon(painterResource(R.drawable.music_note), null, Modifier.size(64.dp))
                    StoryTitle(stringResource(R.string.wrapped_recap_empty_title))
                    Text(
                        stringResource(R.string.wrapped_recap_empty_body, year),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    StoryButton(stringResource(R.string.wrapped_recap_listen), style, onClose)
                }
                else -> {
                    val previousLabel = stringResource(R.string.wrapped_recap_previous)
                    val nextLabel = stringResource(R.string.wrapped_recap_next)
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(previousLabel) { goTo(pager.currentPage - 1) },
                                    CustomAccessibilityAction(nextLabel) { goTo(pager.currentPage + 1) },
                                )
                            }
                            .pointerInput(pager) {
                                detectTapGestures(
                                    onPress = {
                                        held = true
                                        tryAwaitRelease()
                                        held = false
                                    },
                                    // A hold only pauses; it must not also skip a story on release.
                                    onLongPress = {},
                                    onTap = { offset -> goTo(pager.currentPage + if (offset.x < size.width / 3f) -1 else 1) },
                                )
                            },
                        key = { "$year-$it" },
                    ) { page ->
                        val screen = screens[page]
                        val pageStyle = screen.style()
                        val reveal = remember { Animatable(0f) }
                        val active = pager.settledPage == page
                        LaunchedEffect(active) {
                            if (active) reveal.animateTo(1f, tween(1_400, easing = LinearEasing)) else reveal.snapTo(0f)
                        }
                        Box(Modifier.fillMaxSize().background(pageStyle.background)) {
                            StoryShapes(pageStyle.accent, pageStyle.shapes) { reveal.value }
                            CompositionLocalProvider(LocalContentColor provides pageStyle.content) {
                                StoryPage(
                                    screen = screen,
                                    state = state,
                                    isCurrentYear = year == currentYear,
                                    style = pageStyle,
                                    reveal = { reveal.value },
                                    onStart = { goTo(1) },
                                    onReplay = { goTo(0) },
                                    sharing = sharing,
                                    onShare = {
                                        sharing = true
                                        scope.launch {
                                            try {
                                                shareWrapped(context, layer.toImageBitmap().asAndroidBitmap(), state.year)
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                snackbar.showSnackbar(shareError)
                                            } finally {
                                                sharing = false
                                            }
                                        }
                                    },
                                    summaryModifier = Modifier.drawWithContent {
                                        layer.record { this@drawWithContent.drawContent() }
                                        drawLayer(layer)
                                    },
                                    onPlaylist = {
                                        val id = state.playlistId
                                        if (id != null) navController.navigate("local_playlist/$id") else manager.createPlaylist()
                                    },
                                    onPlaylistRetry = manager::createPlaylist,
                                )
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (stories) {
                    val pageLabel = stringResource(R.string.wrapped_recap_page, pager.currentPage + 1, pager.pageCount)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics { contentDescription = pageLabel },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(pager.pageCount) { index ->
                            Spacer(
                                Modifier.weight(1f).height(3.dp).clip(CircleShape)
                                    .background(content.copy(alpha = 0.3f))
                                    .drawBehind {
                                        val fraction = when {
                                            index < pager.currentPage -> 1f
                                            index == pager.currentPage -> progress
                                            else -> 0f
                                        }
                                        drawRect(content, size = Size(size.width * fraction, size.height))
                                    },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.wrapped_recap_brand),
                        modifier = Modifier.padding(start = 4.dp).weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box {
                        val chooseYear = stringResource(R.string.wrapped_recap_select_year)
                        TextButton(
                            onClick = { showYears = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = content),
                            modifier = Modifier.semantics { contentDescription = chooseYear },
                        ) {
                            Text(stringResource(R.string.wrapped_recap_title, year), fontWeight = FontWeight.Bold)
                            Icon(painterResource(R.drawable.expand_more), null, Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showYears, onDismissRequest = { showYears = false }) {
                            (listOf(currentYear) + state.availableYears).distinct().sortedDescending().forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.toString()) },
                                    onClick = {
                                        chosenYear = item.takeUnless { it == currentYear }
                                        showYears = false
                                    },
                                )
                            }
                        }
                    }
                    if (audio != null) {
                        IconButton(onClick = audio::toggleMute) {
                            Icon(
                                painterResource(if (muted) R.drawable.volume_off else R.drawable.volume_up),
                                stringResource(if (muted) R.string.unmute else R.string.mute),
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.close), stringResource(R.string.wrapped_close))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun rememberWrappedMusic(songId: String): WrappedAudioService {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audio = remember { WrappedAudioService(context.applicationContext) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> audio.pause()
                Lifecycle.Event.ON_RESUME -> audio.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audio.release()
        }
    }
    LaunchedEffect(songId) {
        playerConnection?.pause()
        audio.playTrack(songId)
    }
    return audio
}

@Composable
private fun StoryPage(
    screen: WrappedScreenType,
    state: WrappedState,
    isCurrentYear: Boolean,
    style: StoryStyle,
    reveal: () -> Float,
    onStart: () -> Unit,
    onReplay: () -> Unit,
    sharing: Boolean,
    onShare: () -> Unit,
    summaryModifier: Modifier,
    onPlaylist: () -> Unit,
    onPlaylistRetry: () -> Unit,
) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    when (screen) {
        WrappedScreenType.Welcome -> StoryColumn {
            Text(
                stringResource(if (isCurrentYear) R.string.wrapped_recap_year_so_far else R.string.wrapped_recap_full_year),
                Modifier.reveal(reveal, 0),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            DisplayText(state.year.toString(), Modifier.reveal(reveal, 1), maxSize = 140)
            StoryTitle(stringResource(R.string.wrapped_recap_open), Modifier.reveal(reveal, 2))
            Text(stringResource(R.string.wrapped_recap_intro), Modifier.reveal(reveal, 3), textAlign = TextAlign.Center)
            Box(Modifier.reveal(reveal, 4)) {
                StoryButton(stringResource(R.string.wrapped_recap_start), style, onStart)
            }
        }
        WrappedScreenType.MinutesReveal -> StoryColumn {
            StoryTitle(stringResource(R.string.wrapped_recap_time_title), Modifier.reveal(reveal, 0))
            Text(stringResource(R.string.wrapped_recap_time_subtitle), Modifier.reveal(reveal, 1), textAlign = TextAlign.Center)
            val counted = FastOutSlowInEasing.transform(((reveal() - 0.2f) / 0.6f).coerceIn(0f, 1f))
            DisplayText(numbers.format((state.totalMinutes * counted).toLong()), Modifier.reveal(reveal, 1))
            Text(
                stringResource(R.string.wrapped_recap_minutes),
                Modifier.reveal(reveal, 2),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.fillMaxWidth().reveal(reveal, 3), horizontalArrangement = Arrangement.SpaceEvenly) {
                StoryCount(numbers.format(state.uniqueSongCount), stringResource(R.string.wrapped_recap_songs))
                StoryCount(numbers.format(state.uniqueArtistCount), stringResource(R.string.wrapped_recap_artists))
            }
        }
        WrappedScreenType.TopSongReveal -> StoryColumn {
            val song = state.topSongs.first()
            StoryTitle(stringResource(R.string.wrapped_recap_top_song), Modifier.reveal(reveal, 0))
            StoryArtwork(song.thumbnailUrl, style.accent, Modifier.reveal(reveal, 1))
            Text(
                song.title,
                Modifier.reveal(reveal, 2),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(song.artists.joinToString { it.name }.ifBlank { song.artistName.orEmpty() }, Modifier.reveal(reveal, 2), textAlign = TextAlign.Center)
            Text(
                stringResource(
                    R.string.wrapped_recap_song_detail,
                    numbers.format(song.songCountListened),
                    numbers.format((song.timeListened ?: 0L) / 60_000),
                ),
                Modifier.reveal(reveal, 3),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        WrappedScreenType.Top5Songs -> StoryColumn {
            StoryTitle(stringResource(R.string.wrapped_recap_top_songs), Modifier.reveal(reveal, 0))
            state.topSongs.take(5).forEachIndexed { index, song ->
                StoryRank(
                    rank = index + 1,
                    title = song.title,
                    subtitle = song.artists.joinToString { it.name }.ifBlank { song.artistName.orEmpty() },
                    thumbnailUrl = song.thumbnailUrl,
                    accent = style.accent,
                    modifier = Modifier.reveal(reveal, index + 1),
                )
            }
        }
        WrappedScreenType.TopArtistReveal -> StoryColumn {
            StoryTitle(stringResource(R.string.wrapped_recap_top_artist), Modifier.reveal(reveal, 0))
            state.topArtists.firstOrNull()?.let { artist ->
                StoryArtwork(artist.thumbnailUrl, style.accent, Modifier.reveal(reveal, 1), circle = true)
                Text(
                    artist.title,
                    Modifier.reveal(reveal, 2),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.wrapped_recap_artist_detail, numbers.format((artist.timeListened ?: 0) / 60_000)),
                    Modifier.reveal(reveal, 2),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            state.topArtists.drop(1).take(4).forEachIndexed { index, artist ->
                StoryRank(index + 2, artist.title, null, artist.thumbnailUrl, style.accent, Modifier.reveal(reveal, 3))
            }
        }
        WrappedScreenType.Conclusion -> StoryColumn(spacing = 12) {
            StoryTitle(stringResource(R.string.wrapped_recap_summary), Modifier.reveal(reveal, 0))
            WrappedSummaryCard(state, summaryModifier.reveal(reveal, 1))
            Column(Modifier.reveal(reveal, 2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !sharing,
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(containerColor = WrappedCoral, contentColor = WrappedInk),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.share), null, Modifier.padding(end = 8.dp).size(20.dp))
                    Text(stringResource(if (sharing) R.string.wrapped_recap_sharing else R.string.wrapped_recap_share))
                }
                OutlinedButton(
                    onClick = onPlaylist,
                    enabled = state.playlistCreationState != PlaylistCreationState.Creating,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current),
                    border = BorderStroke(1.dp, LocalContentColor.current),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.playlistCreationState == PlaylistCreationState.Creating) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(if (state.playlistId != null) R.string.wrapped_recap_playlist_open else R.string.wrapped_create_playlist))
                    }
                }
                if (state.playlistError) {
                    TextButton(onClick = onPlaylistRetry, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wrapped_recap_playlist_error), color = LocalContentColor.current)
                    }
                }
                TextButton(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.replay), null, Modifier.padding(end = 8.dp).size(20.dp), tint = LocalContentColor.current)
                    Text(stringResource(R.string.wrapped_recap_replay), color = LocalContentColor.current)
                }
            }
        }
    }
}

/** Fades and lifts content in, staggered by [order]; reads [progress] only while drawing. */
private fun Modifier.reveal(progress: () -> Float, order: Int) = graphicsLayer {
    val local = FastOutSlowInEasing.transform(((progress() - order * 0.12f) / 0.45f).coerceIn(0f, 1f))
    alpha = local
    translationY = (1f - local) * 32.dp.toPx()
}

@Composable
private fun StoryShapes(color: Color, variant: Int, progress: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val grow = FastOutSlowInEasing.transform((progress() / 0.6f).coerceIn(0f, 1f))
        val unit = size.minDimension
        when (variant) {
            0 -> {
                drawCircle(color, unit * 0.42f * grow, Offset(size.width * 1.02f, size.height * 0.78f))
                drawCircle(color, unit * 0.28f * grow, Offset(0f, size.height), style = Stroke(unit * 0.07f))
            }
            1 -> {
                val side = unit * 0.5f * grow
                rotate(-30f + 45f * grow, Offset(0f, size.height * 0.9f)) {
                    drawRect(color, Offset(-side / 2, size.height * 0.9f - side / 2), Size(side, side))
                }
                drawCircle(color, unit * 0.14f * grow, Offset(size.width * 0.92f, size.height * 0.88f))
            }
            else -> {
                drawArc(
                    color,
                    startAngle = 180f,
                    sweepAngle = 180f * grow,
                    useCenter = true,
                    topLeft = Offset(size.width * 0.1f, size.height - unit * 0.4f),
                    size = Size(unit * 0.8f, unit * 0.8f),
                )
                drawCircle(color, unit * 0.1f * grow, Offset(size.width * 0.1f, size.height * 0.72f), style = Stroke(unit * 0.04f))
            }
        }
    }
}

@Composable
private fun StoryColumn(spacing: Int = 20, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            content = content,
        )
    }
}

@Composable
private fun DisplayText(text: String, modifier: Modifier = Modifier, maxSize: Int = 96) {
    BasicText(
        text,
        modifier.fillMaxWidth(),
        style = TextStyle(
            color = LocalContentColor.current,
            fontFamily = WrappedDisplayFont,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = maxSize.sp),
    )
}

@Composable
private fun StoryTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun StoryButton(text: String, style: StoryStyle, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = style.content, contentColor = style.background),
    ) {
        Text(text, modifier = Modifier.padding(vertical = 6.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StoryCount(value: String, label: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StoryArtwork(url: String?, accent: Color, modifier: Modifier = Modifier, circle: Boolean = false) {
    Box(
        modifier.widthIn(max = 260.dp).fillMaxWidth(0.72f).aspectRatio(1f)
            .clip(if (circle) CircleShape else RoundedCornerShape(8.dp)).background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.music_note), null, Modifier.size(64.dp))
        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun StoryRank(
    rank: Int,
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            rank.toString(),
            Modifier.widthIn(min = 32.dp),
            fontFamily = WrappedDisplayFont,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
        )
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(accent), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.music_note), null)
            AsyncImage(thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
