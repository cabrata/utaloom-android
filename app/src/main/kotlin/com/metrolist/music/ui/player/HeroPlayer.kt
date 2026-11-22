/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.random.Random
import androidx.compose.ui.res.stringArrayResource
import com.metrolist.music.ui.component.drawSungText
import com.metrolist.music.ui.component.sungChars
import com.metrolist.music.lyrics.LyricClock
import com.metrolist.music.lyrics.LyricsTranslationHelper
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.WordTimestamp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.isActive
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.snapshotFlow
import com.metrolist.music.utils.ComposeToImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.foundation.layout.requiredSize
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.withFrameNanos
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaRouter
import android.media.MediaRouter2
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import com.metrolist.music.LocalNavController
import com.metrolist.music.ui.component.CastButton
import kotlin.math.abs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import com.metrolist.music.LocalDatabase
import com.metrolist.music.constants.QueueEditLockKey
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.constants.SliderStyle
import com.metrolist.music.constants.SliderStyleKey
import com.metrolist.music.constants.SquigglySliderKey
import com.metrolist.music.ui.component.SquigglySlider
import com.metrolist.music.ui.component.WavySlider
import com.metrolist.music.ui.theme.PlayerSliderColors
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.menu.QueueMenu
import com.metrolist.music.ui.menu.SelectionMediaMetadataMenu
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.reportException
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.metrolist.music.constants.AutoplayKey
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.ui.component.MediaMetadataListItem
import com.metrolist.music.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.canvas.CanvasPlayerView
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Normal shows the full-bleed sleeve; Lyrics and Queue shrink it into the header, Apple Music style. */
enum class HeroMode { Normal, Lyrics, Queue }

/**
 * Normal: the cover where the artwork sits with its mirror image below, blurred into a reflection behind
 * the controls. Lyrics and Queue fade over to the cover zoomed in, upright, blurred and darker.
 * Static images, so nothing is redrawn per frame outside a fade.
 */
@Composable
fun HeroBackdrop(
    thumbnailUrl: String?,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val dim = animateFloatAsState(if (dimmed) 1f else 0f, tween(350), label = "heroBackdropDim")
    val context = LocalContext.current
    // BitChord's ArtworkMeshBackdrop: the image on screen and the one fading in over it, both plain
    // bitmaps drawn in one Canvas. The last blur stays up until the next is ready, so a skip never flashes.
    var shown by remember { mutableStateOf(thumbnailUrl?.let(BackdropBlurCache::get)) }
    var incoming by remember { mutableStateOf<ImageBitmap?>(null) }
    // BitChord's ArtworkMeshBackdrop scrim covers bright sleeves with 0.30f flat, so no per-cover
    // read is needed there; here the blurred cover stays up behind the controls, so a white one still
    // washes them out and the shade leans on its brightness (BACKDROP_SHADE_MIN..MAX).
    var brightness by remember { mutableStateOf(0.5f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(thumbnailUrl) {
        val url = thumbnailUrl ?: return@LaunchedEffect
        val next = BackdropBlurCache.get(url) ?: run {
            // The cover changes first; the backdrop follows once its crossfade is done.
            delay(BACKDROP_FOLLOW_DELAY_MS)
            blurredBackdrop(context, url)
        } ?: return@LaunchedEffect
        brightness = next.averageBrightness()
        incoming?.let { shown = it }
        incoming = null
        if (shown == null) { shown = next; return@LaunchedEffect }
        if (next === shown) return@LaunchedEffect
        incoming = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(BACKDROP_FADE_MS, easing = FastOutSlowInEasing))
        shown = next
        incoming = null
    }
    // Every animated value is read in the draw: a skip or a mode switch repaints, never recomposes.
    Box(modifier.fillMaxSize().background(Color.Black)) {
    Canvas(Modifier.fillMaxSize()) {
        // Landscape has no seam below the artwork (min(w, h) is the full height), so it always takes
        // BitChord's full-cover blur and scrim, like its lyrics and queue, or bright covers wash out the text.
        val d = if (size.width > size.height) 1f else dim.value
        shown?.let { drawBackdrop(it, 1f, d) }
        incoming?.let { drawBackdrop(it, fade.value, d) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val d = if (size.width > size.height) 1f else dim.value
        // BitChord's mesh scrim runs 0.10f..0.30f. The blurred cover here stays up behind the
        // controls, so a white one gets a slightly heavier foot; a dark cover keeps its artwork.
        val shade = BACKDROP_SHADE_MIN + (BACKDROP_SHADE_MAX - BACKDROP_SHADE_MIN) * brightness
        val seam = size.width.coerceAtMost(size.height)
        drawRect(
            Brush.verticalGradient(
                colorStops = Array(FADE_CURVE_STOPS + 1) { i ->
                    val t = i / FADE_CURVE_STOPS.toFloat()
                    t to Color.Black.copy(alpha = shade * smootherStep((t / BACKDROP_SHADE_RAMP).coerceAtMost(1f)))
                },
                startY = seam * (1f - HERO_FADE_FRACTION),
                endY = size.height,
            ),
            alpha = 1f - d,
        )
        if (d > 0f) drawRect(BackdropDimmedScrim, alpha = d)
    }
    }
}

/**
 * Normal: the blurred cover behind the artwork, then flipped and stretched to the bottom, so its first row
 * below the seam is the cover's own bottom edge and the two meet without a join (BitChord's seam).
 * Dimmed: the blurred cover zoomed to fill the screen. Bilinear draws of a 128px bitmap, nothing filtered.
 * shortcut: the seam assumes a full-width square artwork at the top (phones, portrait); pass the real
 * artwork bounds in if tablets need it exact.
 */
private fun DrawScope.drawBackdrop(image: ImageBitmap, alpha: Float, dim: Float) {
    if (alpha <= 0.001f) return
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    val seam = w.coerceAtMost(h)
    val src = IntSize(image.width, image.height)
    val normal = alpha * (1f - dim)
    if (normal > 0.001f) {
        drawImage(image, IntOffset.Zero, src, IntOffset.Zero, IntSize(w, seam), normal, filterQuality = FilterQuality.Low)
        if (h > seam) {
            scale(1f, -1f, pivot = Offset(0f, (seam + h) / 2f)) {
                drawImage(image, IntOffset.Zero, src, IntOffset(0, seam), IntSize(w, h - seam), normal, filterQuality = FilterQuality.Low)
            }
        }
    }
    val zoomed = alpha * dim
    if (zoomed > 0.001f) {
        // Centre crop to the screen's aspect, BACKDROP_ZOOM in.
        val srcH = (image.height / BACKDROP_ZOOM).roundToInt()
        val srcW = (srcH * size.width / size.height).roundToInt().coerceAtMost(image.width)
        drawImage(
            image,
            IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
            IntSize(srcW, srcH),
            IntOffset.Zero,
            IntSize(w, h),
            zoomed,
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * BitChord's approach: a 64px cover blurred once off the main thread and cached, instead of a live
 * full-screen RenderEffect that re-blurred on every frame of every fade and morph above it.
 */
private suspend fun blurredBackdrop(context: Context, url: String): ImageBitmap? {
    val request = ImageRequest.Builder(context).data(url).size(BACKDROP_SOURCE_PX).allowHardware(false).build()
    val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
    return withContext(Dispatchers.Default) {
        // Coil's bitmap is immutable and shared with its memory cache: blur a private mutable copy.
        val copy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: return@withContext null
        ComposeToImage.fastBlur(copy, 1f, BACKDROP_BLUR_RADIUS)?.asImageBitmap()
    }?.also { BackdropBlurCache.put(url, it) }
}

internal val BackdropBlurCache = LruCache<String, ImageBitmap>(16)

/** Mean luminance (0..1) of a small bitmap, sampled on a grid, as HeroGlassMenu's frost read. */
private fun ImageBitmap.averageBrightness(): Float {
    val pixels = toPixelMap()
    var sum = 0f
    var n = 0
    for (y in 0 until height step 4) for (x in 0 until width step 4) {
        sum += pixels[x, y].luminance()
        n++
    }
    return if (n == 0) 0f else sum / n
}

private const val HERO_FADE_FRACTION = 0.42f
private const val FADE_CURVE_STOPS = 16

/** 6t⁵ − 15t⁴ + 10t³: zero slope and curvature at both ends, so the fade has no visible edge. */
internal fun smootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

private const val BACKDROP_SOURCE_PX = 128
private const val BACKDROP_BLUR_RADIUS = 14
private const val LIVE_BLUR_DOWNSCALE = 8f
// Matches the cover blur's reach: BACKDROP_BLUR_RADIUS over a BACKDROP_SOURCE_PX source.
private const val LIVE_BLUR_RADIUS_FRACTION = BACKDROP_BLUR_RADIUS.toFloat() / BACKDROP_SOURCE_PX
private const val BACKDROP_FADE_MS = 600
// Scrim weight at the foot of the screen: BitChord's 0.10f..0.30f for its mesh backdrop, nudged
// up to 0.36f for a white sleeve. Well under the flat 0.6f this replaced.
private const val BACKDROP_SHADE_MIN = 0.10f
private const val BACKDROP_SHADE_MAX = 0.36f
// Fraction of the shaded span over which the shade reaches full strength.
private const val BACKDROP_SHADE_RAMP = 0.35f
private const val BACKDROP_ZOOM = 1.4f
private const val BACKDROP_FOLLOW_DELAY_MS = 450L
// BitChord's FullArtworkScrim: deepens toward the controls at the foot.
private val BackdropDimmedScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.34f),
    0.55f to Color.Black.copy(alpha = 0.48f),
    1f to Color.Black.copy(alpha = 0.64f),
)

/**
 * BitChord's full-bleed sleeve: the cover, or the Spotify Canvas in the same spot, edge to edge.
 * Swipe sideways to skip (the art follows the finger, damped, then springs back), double tap a half to seek.
 */
@Composable
fun HeroArtwork(
    thumbnailUrl: String?,
    canvasUrl: String?,
    canvasPlaying: Boolean,
    gesturesEnabled: Boolean,
    onCanvasError: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDoubleTap: (back: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // Read in the draw phase so the morph animates without recomposing or re-measuring the artwork.
    corner: () -> Dp = { 0.dp },
    fadeEdge: () -> Float = { 1f },
    canvasAlpha: () -> Float = { 1f },
    canvasVisible: Boolean = true,
    onTap: (() -> Unit)? = null,
    // Receives the Canvas video each frame so the backdrop can blur it (TextureView draws into layers).
    canvasLayer: GraphicsLayer? = null,
    // 0 while a full-window Canvas plays behind this sleeve, so only its gestures remain.
    coverAlpha: () -> Float = { 1f },
) {
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val nudge = remember { Animatable(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val doubleTap by rememberUpdatedState(onDoubleTap)

    Box(
        modifier
            .pointerInput(gesturesEnabled) {
                if (!gesturesEnabled) return@pointerInput
                val threshold = 72.dp.toPx()
                var total = 0f
                val settle = {
                    scope.launch {
                        nudge.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragCancel = { settle() },
                    onDragEnd = {
                        if (total <= -threshold) next() else if (total >= threshold) previous()
                        settle()
                    },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        total += delta
                        scope.launch { nudge.snapTo(total * 0.35f) }
                    },
                )
            }.pointerInput(gesturesEnabled, rtl) {
                if (!gesturesEnabled) return@pointerInput
                detectTapGestures(
                    onTap = { tap?.invoke() },
                    onDoubleTap = { doubleTap((it.x < size.width / 2) != rtl) },
                )
            }.graphicsLayer {
                translationX = nudge.value
                val radius = corner()
                shape = RoundedCornerShape(radius)
                clip = radius > 0.dp
                compositingStrategy = CompositingStrategy.Offscreen
            }.drawWithContent {
                drawContent()
                // BitChord's seam: the lower HERO_FADE_FRACTION dissolves into the backdrop along
                // smootherstep, so neither end of the fade reads as a line. [fadeEdge] 0 = no fade.
                val fade = fadeEdge()
                if (fade > 0f) {
                    drawRect(
                        Brush.verticalGradient(
                            colorStops = Array(FADE_CURVE_STOPS + 1) { i ->
                                val t = i / FADE_CURVE_STOPS.toFloat()
                                t to Color.Black.copy(alpha = 1f - fade * smootherStep(t))
                            },
                            startY = size.height * (1f - HERO_FADE_FRACTION),
                            endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        Crossfade(
            targetState = thumbnailUrl,
            animationSpec = tween(420),
            modifier = Modifier.graphicsLayer { alpha = coverAlpha() },
            label = "heroArtwork",
        ) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Kept composed (only paused) while hidden: releasing and rebuilding its ExoPlayer blocks the main
        // thread, which landed right at the end of every Lyrics/Queue morph.
        if (!canvasUrl.isNullOrBlank()) {
            CanvasPlayerView(
                canvasUrl = canvasUrl,
                thumbnailUrl = null,
                isPlaying = canvasPlaying && canvasVisible,
                isExpanded = true,
                modifier = Modifier
                    .graphicsLayer { alpha = canvasAlpha() }
                    .then(
                        if (canvasLayer == null) {
                            Modifier
                        } else {
                            Modifier.drawWithContent {
                                canvasLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(canvasLayer)
                            }
                        },
                    ),
                onError = onCanvasError,
            )
        }
    }
}

/** Drag handle over the top of the artwork. */
@Composable
fun HeroHeader(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = null, onClick = onCollapse)
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.6f)),
        )
    }
}

/** Thin flat bar used for both the scrubber and the volume, as in BitChord. [value] is 0..1. */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackHeight: Dp = 6.dp,
) {
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val fraction = value.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        change(target.coerceIn(0f, 1f))
                        finished()
                        true
                    }
                }
            }.pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    change((down.position.x / size.width).coerceIn(0f, 1f))
                    horizontalDrag(down.id) {
                        it.consume()
                        change((it.position.x / size.width).coerceIn(0f, 1f))
                    }
                    finished()
                }
            },
    ) {
        val track = trackHeight.toPx()
        val y = size.height / 2
        val start = track / 2
        val end = size.width - track / 2
        drawLine(Color.White.copy(alpha = 0.3f), Offset(start, y), Offset(end, y), track, StrokeCap.Round)
        drawLine(Color.White, Offset(start, y), Offset(start + (end - start) * fraction, y), track, StrokeCap.Round)
    }
}

/**
 * Glass scrubber/volume bar following the player slider style setting.
 * DEFAULT is the flat BitChord bar, SLIM a thinner one, WAVY the app's wavy/squiggly slider. [value] is 0..1.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPlaying: Boolean = false,
) {
    val style by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val squiggly by rememberPreference(SquigglySliderKey, defaultValue = false)
    val colors = PlayerSliderColors.getSliderColors(Color.White, PlayerBackgroundStyle.BLUR, useDarkTheme = true)
    when (style) {
        SliderStyle.DEFAULT -> ThinSlider(value, onValueChange, onValueChangeFinished, modifier, enabled)
        SliderStyle.SLIM -> ThinSlider(value, onValueChange, onValueChangeFinished, modifier, enabled, trackHeight = 3.dp)
        SliderStyle.WAVY -> if (squiggly) {
            SquigglySlider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                colors = colors,
                isPlaying = isPlaying,
                modifier = modifier,
            )
        } else {
            WavySlider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                colors = colors,
                isPlaying = isPlaying,
                modifier = modifier,
            )
        }
    }
}

/** System media volume, kept in sync with the hardware keys. Hidden on fixed-volume devices. */
@Composable
fun HeroVolumeRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    if (audioManager == null || audioManager.isVolumeFixed) return
    val max = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    fun read() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    var volume by remember { mutableFloatStateOf(read()) }
    var dragging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (!dragging) volume = read()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun set(target: Float) {
        volume = target
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (target * max).roundToInt(), 0)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(R.drawable.volume_down),
            contentDescription = stringResource(R.string.volume_down),
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .size(22.dp)
                .clickable { set((volume - 1f / max).coerceAtLeast(0f)) },
        )
        GlassSlider(
            value = volume,
            onValueChange = {
                dragging = true
                set(it)
            },
            onValueChangeFinished = { dragging = false },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = stringResource(R.string.volume_up),
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .size(22.dp)
                .clickable { set((volume + 1f / max).coerceAtMost(1f)) },
        )
    }
}

/** Current synced lyric line; tap opens the lyrics. Nothing is drawn when the song has no synced lyrics. */
@Composable
fun HeroLyricPreview(
    positionProvider: () -> Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lines = remember(currentLyrics) {
        currentLyrics?.lyrics
            ?.takeIf { it != LyricsEntity.LYRICS_NOT_FOUND }
            ?.let { runCatching { LyricsUtils.parseLyrics(it) }.getOrNull() }
            .orEmpty()
    }
    val position by rememberUpdatedState(positionProvider)
    if (lines.none { it.text.isNotBlank() }) return
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    val lyricsOffset = (currentSong?.song?.lyricsOffset ?: 0).toLong()
    // Blank LRC lines are kept: they mark the instrumental breaks.
    val slot by remember(lines, lyricsOffset) {
        derivedStateOf { previewSlot(lines, position() + lyricsOffset) }
    }
    val introLines = stringArrayResource(R.array.lyrics_intro_lines)
    // One intro caption per track, picked once so recompositions don't reshuffle it (as in BitChord).
    val introPick = remember(currentLyrics) { Random.nextInt(Int.MAX_VALUE) }
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    // BitChord's steered lyric clock, read only while drawing; the provider itself ticks every 100 ms.
    val smooth = remember { mutableLongStateOf(position() + lyricsOffset) }
    val clock = remember { LyricClock(position()) }
    LaunchedEffect(isPlaying, lyricsOffset) {
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val system = System.nanoTime()
            val nowMs = (if (abs(system - frameNanos) < 250_000_000L) frameNanos else system) / 1_000_000.0
            val shown = position()
            val live = playerConnection.currentPosition
            // A scrub shows its own position; paused, each reading is the truth.
            val playhead = if (!isPlaying || abs(shown - live) > 1_000L) {
                clock.hold(shown, nowMs)
                shown
            } else {
                clock.frame(nowMs, live, system / 1_000_000.0, playerConnection.positionJumps.value)
            }
            smooth.longValue = playhead + lyricsOffset
            if (!isPlaying) break
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(indication = null, interactionSource = null, onClick = onClick),
    ) {
        AnimatedContent(
            targetState = slot,
            transitionSpec = {
                // BitChord's strip: the next line slides up while the old one fades and slides away.
                (fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(340, easing = FastOutSlowInEasing)) { (it * 0.35f).toInt() }) togetherWith
                    (fadeOut(tween(340, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { -(it * 0.35f).toInt() })
            },
            label = "heroLyricLine",
            modifier = Modifier.weight(1f, fill = false),
        ) { shown ->
            val entry = lines.getOrNull(shown)
            if (entry != null) {
                // No icon on sung lines, so the words get the full width.
                HeroKaraokeText(entry = entry, positionMs = { smooth.longValue })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (shown == PREVIEW_INTRO) introLines[introPick % introLines.size]
                        else stringResource(R.string.lyrics_instrumental),
                        color = Color.White,
                        style = PreviewLyricStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = stringResource(R.string.lyrics),
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
    }
}

internal const val PREVIEW_INTRO = -1

private const val PREVIEW_PENDING_ALPHA = 0.65f

/** Bright on any artwork blur: medium weight with a soft shadow for contrast. */
private val PreviewLyricStyle = TextStyle(
    fontSize = 15.sp,
    fontWeight = FontWeight.Medium,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.25f), blurRadius = 8f),
)

/** A break between sung lines at least this long shows "Instrumental", matching the lyrics view's dots. */
private const val INSTRUMENTAL_GAP_MS = 4_000L

/**
 * What the lyric preview shows at [positionMs]: a line index, [PREVIEW_INTRO] before the first sung line,
 * or an instrumental break as `-2 - index` (distinct per break so each one animates in).
 */
internal fun previewSlot(lines: List<LyricsEntry>, positionMs: Long): Int {
    val firstSung = lines.indexOfFirst { it.text.isNotBlank() }
    val index = LyricsUtils.findCurrentLineIndex(lines, positionMs)
    if (firstSung < 0 || index < firstSung) return PREVIEW_INTRO
    val line = lines[index]
    if (line.text.isBlank()) return -2 - index
    val end = line.words?.lastOrNull()?.let { (it.endTime * 1000).toLong() }
    val next = lines.getOrNull(index + 1)?.time
    if (end != null && next != null && positionMs >= end && next - end >= INSTRUMENTAL_GAP_MS) return -2 - index
    return index
}

/** One lyric line lit word by word, the same timing the full lyrics view uses; repaints, never recomposes. */
@Composable
private fun HeroKaraokeText(entry: LyricsEntry, positionMs: () -> Long) {
    val words = remember(entry) {
        entry.words?.takeIf { it.isNotEmpty() } ?: run {
            // Line-synced lyrics: the same quick sweep LyricsLine fakes for them.
            val tokens = entry.text.split(Regex("\\s+")).filter { it.isNotBlank() }
            val start = entry.time / 1000.0
            tokens.mapIndexed { i, token -> WordTimestamp(token, start + i * 0.03, start + i * 0.03 + 0.18) }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = entry.text,
        color = Color.White.copy(alpha = PREVIEW_PENDING_ALPHA),
        style = PreviewLyricStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            val result = layout ?: return@drawWithContent drawContent()
            // Drawn only through the sweep: the plain text underneath would double the dim colour.
            drawSungText(
                layout = result,
                sung = sungChars(entry.text, words, positionMs()),
                lit = Color.White,
                pending = Color.White.copy(alpha = PREVIEW_PENDING_ALPHA),
                feather = 20.dp.toPx(),
            )
        },
    )
}

/**
 * Lyrics, audio output (with the current route's name under it) and queue. The open mode's button is
 * highlighted, and in Queue mode the output button gives way to shuffle | repeat | autoplay.
 */
@Composable
fun HeroBottomRow(
    mode: HeroMode,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    controlsEnabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val router = remember(context) { context.getSystemService(MediaRouter::class.java) }
    fun routeName() = router?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)?.getName(context)?.toString()
    var outputName by remember { mutableStateOf(routeName()) }
    DisposableEffect(router) {
        val callback = object : MediaRouter.SimpleCallback() {
            override fun onRouteSelected(r: MediaRouter, type: Int, info: MediaRouter.RouteInfo) {
                outputName = routeName()
            }

            override fun onRouteChanged(r: MediaRouter, info: MediaRouter.RouteInfo) {
                outputName = routeName()
            }
        }
        router?.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, callback)
        onDispose { router?.removeCallback(callback) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 56.dp),
        ) {
            HeroModeButton(R.drawable.lyrics, R.string.lyrics, selected = mode == HeroMode.Lyrics, onClick = onLyrics)
            AnimatedContent(
                targetState = mode == HeroMode.Queue,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "heroBottomCenter",
            ) { queue ->
                if (queue) {
                    HeroPlaybackModes(enabled = controlsEnabled)
                } else {
                    IconButton(onClick = { openOutputSwitcher(context) }) {
                        Icon(
                            painter = painterResource(R.drawable.headphones),
                            contentDescription = stringResource(R.string.audio_output),
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            HeroModeButton(R.drawable.queue_music, R.string.queue, selected = mode == HeroMode.Queue, onClick = onQueue)
        }
        Text(
            text = outputName.orEmpty(),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (compact) 2.dp else 14.dp),
        )
    }
}

@Composable
private fun HeroModeButton(icon: Int, label: Int, selected: Boolean, onClick: () -> Unit) {
    val highlight by animateColorAsState(
        if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        label = "heroModeButton",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(highlight)
            .clickable(onClickLabel = stringResource(label), role = Role.Button, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            tint = Color.White.copy(alpha = if (selected) 1f else 0.75f),
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Shuffle | repeat | autoplay as one segmented pill, the active segments lit. */
@Composable
private fun HeroPlaybackModes(enabled: Boolean) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val shuffle by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val (autoplay, onAutoplayChange) = rememberPreference(AutoplayKey, defaultValue = true)

    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        HeroSegment(R.drawable.shuffle, stringResource(R.string.shuffle), shuffle, enabled) {
            playerConnection.player.shuffleModeEnabled = !shuffle
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.15f)))
        HeroSegment(
            icon = if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
            label = stringResource(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                    Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                    else -> R.string.repeat_mode_off
                },
            ),
            active = repeatMode != Player.REPEAT_MODE_OFF,
            enabled = enabled,
        ) { playerConnection.player.toggleRepeatMode() }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.15f)))
        HeroSegment(R.drawable.all_inclusive, stringResource(R.string.autoplay), autoplay, enabled = true) {
            onAutoplayChange(!autoplay)
        }
    }
}

@Composable
private fun HeroSegment(icon: Int, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val highlight by animateColorAsState(
        if (active) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        label = "heroSegment",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(highlight)
            .toggleable(value = active, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
            .semantics { contentDescription = label },
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = if (active) 1f else 0.7f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** "Lyrics by <provider>", shown in place of the lyric preview while the lyrics are open. */
@Composable
fun HeroLyricsCredit(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val provider = currentLyrics?.provider?.takeIf { it.isNotBlank() && currentLyrics?.lyrics != LyricsEntity.LYRICS_NOT_FOUND }
        ?: return
    Text(
        text = stringResource(R.string.lyrics_by, provider),
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 15.sp,
        textDecoration = TextDecoration.Underline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** BitChord's lyrics footer: Latin toggle left, "Lyrics by" centre, translate right. */
@Composable
fun HeroLyricsActions(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val status by LyricsTranslationHelper.status.collectAsStateWithLifecycle()
    val translated by LyricsTranslationHelper.hasActiveTranslations.collectAsStateWithLifecycle()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().height(44.dp),
    ) {
        HeroCircleButton(
            icon = R.drawable.language,
            label = R.string.romanize_current_track,
            selected = currentSong?.romanizeLyrics == true,
        ) {
            currentSong?.song?.let { song -> database.query { upsert(song.copy(romanizeLyrics = !song.romanizeLyrics)) } }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { HeroLyricsCredit() }
        HeroCircleButton(
            icon = R.drawable.translate,
            label = R.string.ai_lyrics_translation,
            selected = translated,
            busy = status is LyricsTranslationHelper.TranslationStatus.Translating,
        ) {
            if (translated) {
                currentLyrics?.let { lyrics ->
                    database.query { upsert(LyricsTranslationHelper.clearTranslations(lyrics)) }
                    LyricsTranslationHelper.triggerClearTranslations()
                }
            } else {
                LyricsTranslationHelper.triggerManualTranslation()
            }
        }
    }
}

@Composable
private fun HeroCircleButton(icon: Int, label: Int, selected: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    val background by animateColorAsState(
        Color.White.copy(alpha = if (selected) 0.32f else 0.14f),
        label = "heroCircleButton",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = stringResource(label), role = Role.Button, onClick = onClick),
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(label),
                tint = Color.White.copy(alpha = if (selected) 1f else 0.8f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * The play queue in place of the artwork, BitChord style: "Now playing", the AutoPlay switch, then what
 * plays next. Sleep timer and save-as-playlist live in the glass menu. Long press selects, as in Queue.kt.
 * Reorder mirrors Queue.kt: a move in play order, or a new shuffle order while shuffling.
 * shortcut: no Cast queue jump or swipe-to-remove-with-undo; the classic queue sheet (other styles) keeps them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroQueue(
    isListenTogetherGuest: Boolean,
    playerBottomSheetState: BottomSheetState,
    onUserScroll: (deltaY: Float, direction: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val automix by playerConnection.service.automixItems.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playingFrom = queueTitle ?: mediaMetadata?.album?.title
    val (autoplay, onAutoplayChange) = rememberPreference(AutoplayKey, defaultValue = true)
    var inSelectMode by remember { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    val exitSelectMode = {
        inSelectMode = false
        selection.clear()
    }
    BackHandler(enabled = inSelectMode, onBack = exitSelectMode)

    val windows = remember { mutableStateListOf<Timeline.Window>() }
    LaunchedEffect(queueWindows) {
        windows.clear()
        windows.addAll(queueWindows)
    }

    val listState = rememberLazyListState()
    // Window indices (not list rows) of the drag in progress: the list also holds labels and the AutoPlay row.
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = windows.indexOfFirst { it.uid.hashCode() == from.key }
        val toIndex = windows.indexOfFirst { it.uid.hashCode() == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        dragInfo = (dragInfo?.first ?: fromIndex) to toIndex
        windows.move(fromIndex, toIndex)
    }
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (reorderState.isAnyItemDragging) return@LaunchedEffect
        val (from, to) = dragInfo ?: return@LaunchedEffect
        dragInfo = null
        val safeFrom = from.coerceIn(0, queueWindows.lastIndex)
        val safeTo = to.coerceIn(0, queueWindows.lastIndex)
        val player = playerConnection.player
        if (!player.shuffleModeEnabled) {
            player.moveMediaItem(safeFrom, safeTo)
        } else {
            player.setShuffleOrder(
                DefaultShuffleOrder(
                    queueWindows.map { it.firstPeriodIndex }.toMutableList().move(safeFrom, safeTo).toIntArray(),
                    System.currentTimeMillis(),
                ),
            )
        }
    }

    val onItemClick: (Timeline.Window, Boolean) -> Unit = { window, active ->
        when {
            inSelectMode -> {
                val id = window.mediaItem.mediaId
                if (id in selection) selection.remove(id) else selection.add(id)
            }

            isListenTogetherGuest -> Unit
            active -> playerConnection.togglePlayPause()
            else -> {
                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                playerConnection.player.playWhenReady = true
            }
        }
    }
    val onItemLongClick: (Timeline.Window) -> Unit = { window ->
        if (!inSelectMode) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            inSelectMode = true
            selection.add(window.mediaItem.mediaId)
        }
    }
    val showQueueMenu: (MediaMetadata) -> Unit = { metadata ->
        menuState.show {
            QueueMenu(
                mediaMetadata = metadata,
                playerBottomSheetState = playerBottomSheetState,
                onShowDetailsDialog = { bottomSheetPageState.show { ShowMediaInfo(metadata.id) } },
                onDismiss = menuState::dismiss,
            )
        }
    }

    val colors = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = colors.copy(
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.6f),
            secondary = Color.White.copy(alpha = 0.6f),
            secondaryContainer = Color.White.copy(alpha = 0.12f),
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(modifier) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = if (inSelectMode) 12.dp else 24.dp),
                ) {
                    if (inSelectMode) {
                        val count = selection.size
                        IconButton(onClick = exitSelectMode) {
                            Icon(painterResource(R.drawable.close), contentDescription = null)
                        }
                        Text(
                            text = pluralStringResource(R.plurals.n_selected, count, count),
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = count == windows.size && count > 0,
                            onCheckedChange = {
                                val all = count == windows.size
                                selection.clear()
                                if (!all) windows.forEach { selection.add(it.mediaItem.mediaId) }
                            },
                        )
                        IconButton(
                            enabled = count > 0,
                            onClick = {
                                val items = windows.filter { it.mediaItem.mediaId in selection }
                                menuState.show {
                                    SelectionMediaMetadataMenu(
                                        songSelection = items.mapNotNull { it.mediaItem.metadata },
                                        onDismiss = menuState::dismiss,
                                        clearAction = exitSelectMode,
                                        currentItems = items,
                                    )
                                }
                            },
                        ) {
                            Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                        }
                    } else {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.queue),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!playingFrom.isNullOrBlank()) {
                                EasedMarqueeText(
                                    text = stringResource(R.string.playing_from, playingFrom),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                val queueScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y != 0f && source == NestedScrollSource.UserInput) {
                                onUserScroll(available.y, if (available.y > 0f) 1 else -1)
                            }
                            return Offset.Zero
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(queueScrollConnection)
                ) {
                    val current = windows.getOrNull(currentWindowIndex)
                    if (current != null) {
                        item(key = "hero_now_playing_label") {
                            HeroQueueLabel(stringResource(R.string.now_playing))
                        }
                        item(key = current.uid.hashCode()) {
                            val metadata = current.mediaItem.metadata ?: return@item
                            HeroQueueItem(
                                metadata = metadata,
                                active = true,
                                isPlaying = isPlaying,
                                selected = if (inSelectMode) current.mediaItem.mediaId in selection else null,
                                onClick = { onItemClick(current, true) },
                                onLongClick = { onItemLongClick(current) },
                            ) {
                                if (!isListenTogetherGuest) {
                                    IconButton(onClick = { showQueueMenu(metadata) }) {
                                        Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                                    }
                                }
                            }
                        }
                    }

                    item(key = "hero_autoplay") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = autoplay, role = Role.Switch, onValueChange = onAutoplayChange)
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.all_inclusive),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = if (autoplay) 1f else 0.5f),
                                modifier = Modifier.size(26.dp),
                            )
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(stringResource(R.string.autoplay), fontSize = 18.sp)
                                Text(
                                    text = stringResource(R.string.autoplay_queue_desc),
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                            Switch(checked = autoplay, onCheckedChange = null)
                        }
                    }

                    val upcoming = windows.withIndex().drop((currentWindowIndex + 1).coerceAtLeast(0))
                    items(upcoming, key = { it.value.uid.hashCode() }) { (_, window) ->
                        val metadata = window.mediaItem.metadata ?: return@items
                        ReorderableItem(reorderState, key = window.uid.hashCode()) {
                            HeroQueueItem(
                                metadata = metadata,
                                active = false,
                                isPlaying = false,
                                selected = if (inSelectMode) window.mediaItem.mediaId in selection else null,
                                onClick = { onItemClick(window, false) },
                                onLongClick = { onItemLongClick(window) },
                            ) {
                                if (!isListenTogetherGuest) {
                                    IconButton(onClick = { showQueueMenu(metadata) }) {
                                        Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                                    }
                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                        Icon(painterResource(R.drawable.drag_handle), contentDescription = null)
                                    }
                                }
                            }
                        }
                    }

                    if (automix.isNotEmpty()) {
                        item(key = "hero_similar_label") {
                            HeroQueueLabel(stringResource(R.string.similar_content))
                        }
                        itemsIndexed(automix, key = { _, item -> "automix_" + item.mediaId }) { index, item ->
                            val metadata = item.metadata ?: return@itemsIndexed
                            MediaMetadataListItem(
                                mediaMetadata = metadata,
                                trailingContent = {
                                    if (!isListenTogetherGuest) {
                                        IconButton(onClick = { playerConnection.service.playNextAutomix(item, index) }) {
                                            Icon(painterResource(R.drawable.playlist_play), contentDescription = null)
                                        }
                                        IconButton(onClick = { playerConnection.service.addToQueueAutomix(item, index) }) {
                                            Icon(painterResource(R.drawable.queue_music), contentDescription = null)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One line that, only when it overflows, glides left with ease-in-out (slow, fast, slow), pauses and loops
 * seamlessly: a second copy trails the first, so each pass ends where it began.
 * shortcut: always scrolls leftwards, mirror the offset if an RTL locale needs it.
 */
@Composable
private fun EasedMarqueeText(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    var textWidth by remember { mutableIntStateOf(0) }
    var overflows by remember { mutableStateOf(false) }
    val shift = remember { Animatable(0f) }
    val density = LocalDensity.current
    val gap = with(density) { 32.dp.toPx() }
    LaunchedEffect(text, overflows, textWidth) {
        shift.snapTo(0f)
        if (!overflows) return@LaunchedEffect
        val distance = textWidth + gap
        val millis = (distance / density.density * 18f).toInt().coerceIn(2_000, 8_000)
        while (isActive) {
            delay(MARQUEE_PAUSE_MS)
            shift.animateTo(distance, tween(millis, easing = FastOutSlowInEasing))
            shift.snapTo(0f)
        }
    }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clipToBounds()
            .drawWithContent {
                val x = -shift.value
                translate(left = x) { this@drawWithContent.drawContent() }
                if (overflows) translate(left = x + textWidth + gap) { this@drawWithContent.drawContent() }
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
                textWidth = placeable.width
                overflows = placeable.width > constraints.maxWidth
                layout(width, placeable.height) { placeable.place(0, 0) }
            },
    )
}

private const val MARQUEE_PAUSE_MS = 2_000L

@Composable
private fun HeroQueueLabel(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 16.sp,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroQueueItem(
    metadata: MediaMetadata,
    active: Boolean,
    isPlaying: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    MediaMetadataListItem(
        mediaMetadata = metadata,
        isSelected = selected == true,
        isActive = active,
        isPlaying = active && isPlaying,
        trailingContent = {
            if (selected != null) Checkbox(checked = selected, onCheckedChange = { onClick() }) else trailing()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

private fun openOutputSwitcher(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        MediaRouter2.getInstance(context).showSystemOutputSwitcher()
    ) {
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.startActivity(Intent(Settings.Panel.ACTION_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Opens a panel on an upward swipe without consuming the drag, so the sheet's swipe-down and the art's skip still work. */
internal fun Modifier.swipeUpToOpen(enabled: Boolean, onOpen: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(onOpen) {
        val threshold = 72.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var total = Offset.Zero
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                total += change.positionChangeIgnoreConsumed()
                if (!change.pressed) break
            }
            if (total.y < -threshold && abs(total.y) > abs(total.x) * 2) onOpen()
        }
    }

/**
 * The Liquid Glass player. Tall windows get the full-bleed BitChord layout, whose artwork morphs into the
 * header for Lyrics and Queue (capped to a centred column on tablets). Wide windows get Apple Music's
 * landscape split: the artwork left, everything else right, compacted on short phone screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroPlayerContent(
    state: BottomSheetState,
    mediaMetadata: MediaMetadata?,
    mode: HeroMode,
    onModeChange: (HeroMode) -> Unit,
    canvasUrl: String?,
    onCanvasError: () -> Unit,
    positionProvider: () -> Long,
    duration: Long,
    isPlaying: Boolean,
    buffering: Boolean,
    playIcon: Int,
    playLabel: Int,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    isListenTogetherGuest: Boolean,
    focusRequester: FocusRequester,
    canvasLayer: GraphicsLayer? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    // BitChord: a tap on the Canvas slides the control deck away so the video plays clear (the title stays);
    // tapping again, or leaving Normal mode, brings it back. The Canvas itself never leaves its sleeve.
    var canvasControlsHidden by remember(canvasUrl) { mutableStateOf(false) }
    LaunchedEffect(state.isExpanded, mode) { if (!state.isExpanded || mode != HeroMode.Normal) canvasControlsHidden = false }
    // Position changes every 100 ms: only HeroProgress and the lyric line read it, never this scope.
    val scrubState = remember { mutableStateOf<Long?>(null) }
    var scrub by scrubState
    val knownDuration = duration.takeIf { it > 0 } ?: 0L
    val toggleMode: (HeroMode) -> Unit = { onModeChange(if (mode == it) HeroMode.Normal else it) }
    BackHandler(enabled = mode != HeroMode.Normal && state.isExpanded) { onModeChange(HeroMode.Normal) }
    if (mode != HeroMode.Lyrics) FetchLyricsEffect(mediaMetadata)

    // Controls auto-hide in Lyrics/Queue mode
    val density = LocalDensity.current
    val controlsSlopPx = remember { with(density) { 20.dp.toPx() } }
    var lyricsControlsVisible by remember { mutableStateOf(true) }
    // Bumped on every reveal so the idle timer below restarts.
    var revealTick by remember { mutableIntStateOf(0) }
    val revealControls = {
        lyricsControlsVisible = true
        revealTick++
    }
    var scrollTravel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mode) { revealControls() }

    // BitChord: finger travelling up (reading ahead) hides the controls, reversing brings them back.
    val onUserScrollLyrics: (Float, Int) -> Unit = { deltaY, direction ->
        if (scrollTravel != 0f && (scrollTravel > 0f) != (direction > 0)) scrollTravel = 0f
        scrollTravel += deltaY
        if (scrollTravel <= -controlsSlopPx) {
            scrollTravel = 0f
            lyricsControlsVisible = false
        } else if (scrollTravel >= controlsSlopPx) {
            scrollTravel = 0f
            revealControls()
        }
    }

    // Lyrics left alone stand the controls down, as in BitChord; never mid-scrub.
    LaunchedEffect(mode, lyricsControlsVisible, revealTick, scrub) {
        if (mode == HeroMode.Lyrics && lyricsControlsVisible && scrub == null) {
            kotlinx.coroutines.delay(LYRICS_CONTROLS_IDLE_MS)
            lyricsControlsVisible = false
        }
    }
    val controlsShown = mode == HeroMode.Normal || lyricsControlsVisible
    var menuAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val openMenu: (androidx.compose.ui.geometry.Rect) -> Unit = { menuAnchor = it }

    val morphState = animateFloatAsState(
        targetValue = if (mode == HeroMode.Normal) 0f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 350f),
        label = "heroMorph",
    )
    // Every morph read below sits in a graphicsLayer, draw or placement block: one recomposition
    // per mode switch instead of one per animation frame.
    val morph = remember(morphState) { { morphState.value } }
    // As in BitChord: lyrics and queue are the heaviest compositions here, so they mount once the
    // morph has landed instead of on its first frame. Closing hands back at once.
    var panelMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        if (mode != HeroMode.Normal && panelMode == HeroMode.Normal) snapshotFlow { morph() }.first { it >= 0.98f }
        panelMode = mode
    }

    // inSleeve: the Canvas plays inside this artwork (landscape). False: it plays behind the whole player
    // (portrait, as in BitChord) and this artwork is the gesture layer over it, then the thumbnail.
    val artwork: @Composable (Modifier, () -> Dp, () -> Float, () -> Float, Boolean, Boolean, Boolean) -> Unit =
        { modifier, corner, fadeEdge, canvasAlpha, canvasVisible, gestures, inSleeve ->
        HeroArtwork(
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
            canvasUrl = canvasUrl.takeIf { inSleeve },
            canvasPlaying = isPlaying && state.isExpanded,
            gesturesEnabled = gestures && !isListenTogetherGuest && state.isExpanded,
            onCanvasError = onCanvasError,
            onNext = { if (canSkipNext) playerConnection.seekToNext() },
            onPrevious = playerConnection::seekToPrevious,
            onDoubleTap = { back ->
                onSeek((positionProvider() + if (back) -5_000L else 5_000L).coerceIn(0L, knownDuration))
            },
            corner = corner,
            fadeEdge = fadeEdge,
            canvasAlpha = canvasAlpha,
            canvasVisible = canvasVisible,
            onTap = if (!canvasUrl.isNullOrBlank() && mode == HeroMode.Normal) {
                { canvasControlsHidden = !canvasControlsHidden }
            } else {
                null
            },
            canvasLayer = canvasLayer.takeIf { inSleeve },
            coverAlpha = if (inSleeve || canvasUrl.isNullOrBlank()) {
                { 1f }
            } else {
                { morph() }
            },
            modifier = modifier,
        )
    }

    // Lyrics or Queue in place of the artwork.
    val panel: @Composable (Modifier) -> Unit = { modifier ->
        AnimatedContent(
            targetState = panelMode,
            transitionSpec = {
                (fadeIn(tween(320, delayMillis = 80)) + slideInVertically(tween(360)) { it / 10 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 10 })
            },
            label = "heroPanel",
            modifier = modifier,
        ) { panelMode ->
            when (panelMode) {
                HeroMode.Lyrics -> Box(
                        Modifier
                            .fillMaxSize()
                            // No extra edge fade here: the lyrics view fades its own edges, and a second
                            // full-panel offscreen layer doubled the cost of every frame.
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                revealControls()
                            },
                    ) {
                    InlineLyricsView(mediaMetadata = mediaMetadata, showLyrics = true, sliderPositionProvider = { scrub }, onUserScroll = onUserScrollLyrics)
                }

                HeroMode.Queue -> HeroQueue(
                    isListenTogetherGuest = isListenTogetherGuest,
                    playerBottomSheetState = state,
                    onUserScroll = onUserScrollLyrics,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            revealControls()
                        },
                )

                HeroMode.Normal -> Box(Modifier.fillMaxSize())
            }
        }
    }

    // Scrubber to bottom row. Never moves between modes; [previewRow] adds the lyric line / credit above it.
    // Hidden while reading lyrics/queue: it slides away and the panel above grows into its space.
    val controls: @Composable (Boolean, Boolean, Boolean, Boolean, Dp) -> Unit = { compact, showVolume, previewRow, bottomRow, hPad ->
        SlidingDeck(visible = controlsShown && !canvasControlsHidden) {
        Column(Modifier.fillMaxWidth()) {
            SlidingDeck(visible = previewRow) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = hPad),
                ) {
                    AnimatedContent(
                        targetState = mode == HeroMode.Lyrics,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                        label = "heroCredit",
                    ) { lyricsOpen ->
                        if (lyricsOpen) {
                            HeroLyricsActions()
                        } else {
                            HeroLyricPreview(
                                positionProvider = { scrub ?: positionProvider() },
                                onClick = { onModeChange(HeroMode.Lyrics) },
                            )
                        }
                    }
                }
            }
            HeroProgress(
                positionProvider = positionProvider,
                duration = knownDuration,
                scrubState = scrubState,
                onSeek = onSeek,
                enabled = !isListenTogetherGuest,
                isPlaying = isPlaying,
                hPad = hPad,
            )

            Spacer(Modifier.height(if (compact) 2.dp else 10.dp))
            val buttonSize = if (compact) 56.dp else 80.dp
            val iconSize = if (compact) 44.dp else 56.dp
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
            ) {
                val skipEnabled = !isListenTogetherGuest
                IconButton(
                    onClick = playerConnection::seekToPrevious,
                    enabled = canSkipPrevious && skipEnabled,
                    modifier = Modifier.size(buttonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.player_rewind),
                        contentDescription = stringResource(R.string.previous),
                        tint = Color.White.copy(alpha = if (canSkipPrevious && skipEnabled) 1f else 0.38f),
                        modifier = Modifier.size(iconSize),
                    )
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(buttonSize).focusRequester(focusRequester)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (buffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(iconSize + 8.dp),
                            )
                        }
                        AnimatedContent(
                            targetState = playIcon,
                            transitionSpec = {
                                (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f)) togetherWith
                                    (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f))
                            },
                            label = "heroPlayPause",
                        ) { icon ->
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = stringResource(playLabel),
                                tint = Color.White,
                                modifier = Modifier.size(if (buffering) iconSize * 0.57f else iconSize),
                            )
                        }
                    }
                }
                IconButton(
                    onClick = playerConnection::seekToNext,
                    enabled = canSkipNext && skipEnabled,
                    modifier = Modifier.size(buttonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.player_forward),
                        contentDescription = stringResource(R.string.next),
                        tint = Color.White.copy(alpha = if (canSkipNext && skipEnabled) 1f else 0.38f),
                        modifier = Modifier.size(iconSize),
                    )
                }
            }

            if (showVolume) {
                Spacer(Modifier.height(if (compact) 6.dp else 20.dp))
                HeroVolumeRow(Modifier.padding(horizontal = hPad))
            }
            if (bottomRow) {
                Spacer(Modifier.height(if (compact) 2.dp else 8.dp))
                HeroBottomRow(
                    mode = mode,
                    onLyrics = { toggleMode(HeroMode.Lyrics) },
                    onQueue = { toggleMode(HeroMode.Queue) },
                    controlsEnabled = !isListenTogetherGuest,
                    compact = compact,
                    modifier = Modifier.padding(horizontal = if (compact) hPad else hPad + 8.dp),
                )
                Spacer(Modifier.height(if (compact) 0.dp else 12.dp))
            }
        }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
        // BitChord: on a phone held upright the Canvas fills the window behind everything.
        val fullCanvas = !landscape && !canvasUrl.isNullOrBlank()
        if (fullCanvas && canvasLayer != null) {
            val deckOpen = mode == HeroMode.Normal && controlsShown && !canvasControlsHidden
            CanvasBackground(
                canvasUrl = canvasUrl.orEmpty(),
                playing = isPlaying && state.isExpanded && mode == HeroMode.Normal,
                alpha = { 1f - morph() },
                layer = canvasLayer,
                deckOpen = deckOpen,
                onError = onCanvasError,
            )
        }
        if (landscape) {
            val compact = maxHeight < 480.dp
            val showVolume = maxHeight >= 400.dp
            val normal = mode == HeroMode.Normal
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 48.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 1100.dp)
                    .fillMaxSize()
                    // BitChord: the status bar's stable inset even while it's hidden, plus a handle strip,
                    // so the title never rides up under where the status bar would be.
                    .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.statusBarsIgnoringVisibility))
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = if (compact) 8.dp else 20.dp),
            ) {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    val bottomHeight = if (compact) 72.dp else 96.dp
                    val artSize = min(maxWidth, maxHeight - bottomHeight - 8.dp).coerceAtLeast(1.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(artSize)) {
                            artwork(
                                Modifier
                                    .fillMaxSize()
                                    .swipeUpToOpen(enabled = normal, onOpen = { onModeChange(HeroMode.Queue) }),
                                LandscapeCorner,
                                NoFade,
                                FullAlpha,
                                true,
                                true,
                                true,
                            )
                            CastButton(tintColor = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        HeroBottomRow(
                            mode = mode,
                            onLyrics = { toggleMode(HeroMode.Lyrics) },
                            onQueue = { toggleMode(HeroMode.Queue) },
                            controlsEnabled = !isListenTogetherGuest,
                            compact = compact,
                            modifier = Modifier.width(artSize),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).widthIn(max = 560.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedVisibility(
                        visible = normal,
                        enter = expandVertically(tween(380)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(380)) + fadeOut(tween(200)),
                    ) {
                        Column {
                            mediaMetadata?.let {
                                HeroTitleRow(
                                    metadata = it,
                                    state = state,
                                    onMenu = openMenu,
                                    titleSize = if (compact) 20.sp else 26.sp,
                                    artistSize = if (compact) 16.sp else 20.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                                HeroLyricPreview(
                                    positionProvider = { scrub ?: positionProvider() },
                                    onClick = { onModeChange(HeroMode.Lyrics) },
                                )
                            }
                        }
                    }
                    if (!normal) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            panel(Modifier.fillMaxSize())
                        }
                    }
                    AnimatedVisibility(
                        visible = mode == HeroMode.Lyrics,
                        enter = expandVertically(tween(380)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(380)) + fadeOut(tween(200)),
                    ) {
                        HeroLyricsActions(Modifier.padding(top = 4.dp))
                    }
                    controls(compact, showVolume && normal, false, false, 0.dp)
                }
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            ) {
                // Artwork, header and the Lyrics/Queue panel. Every bit of spare height lands here,
                // so the control block below stays pinned to the bottom of the screen.
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .swipeUpToOpen(enabled = mode == HeroMode.Normal, onOpen = { onModeChange(HeroMode.Queue) }),
                ) {
                    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val thumbSize = 64.dp
                    val headerTop = topInset + 40.dp
                    val panelTop = headerTop + thumbSize + 16.dp
                    val fullSize = maxWidth
                    val thumbScale = thumbSize / fullSize

                    artwork(
                        Modifier
                            .size(fullSize)
                            .graphicsLayer {
                                val t = morph()
                                val scale = androidx.compose.ui.util.lerp(1f, thumbScale, t)
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = scale
                                scaleY = scale
                                translationX = 24.dp.toPx() * t
                                translationY = headerTop.toPx() * t
                            }.clickable(enabled = mode != HeroMode.Normal, indication = null, interactionSource = null) {
                                onModeChange(HeroMode.Normal)
                            },
                        // The layer is scaled down, so the radius is scaled up to land on 12dp on screen.
                        { 12.dp * morph() / androidx.compose.ui.util.lerp(1f, thumbScale, morph()) },
                        { 1f - morph() },
                        { 1f - morph() },
                        mode == HeroMode.Normal,
                        mode == HeroMode.Normal,
                        canvasUrl.isNullOrBlank(),
                    )
                    HeroHeader(
                        onCollapse = state::collapseSoft,
                        modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars),
                    )
                    CastButton(
                        tintColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(12.dp)
                            .graphicsLayer { alpha = 1f - morph() },
                    )
                    panel(Modifier.fillMaxSize().padding(top = panelTop))

                    // Title, artist and menu: under the art in Normal, beside the thumbnail otherwise.
                    mediaMetadata?.let {
                        HeroMorphTitle(
                            metadata = it,
                            state = state,
                            onMenu = openMenu,
                            morph = morph,
                            headerTop = headerTop,
                            thumbSize = thumbSize,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // The queue needs the room more than the lyric line does.
                controls(false, true, mode != HeroMode.Queue, true, 24.dp)
            }
        }
        // Controls away: a tap anywhere below the header brings them back (the sleeve only covers the top).
        if (fullCanvas && mode == HeroMode.Normal && canvasControlsHidden) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 96.dp)
                    .clickable(indication = null, interactionSource = null) { canvasControlsHidden = false },
            )
        }
        HeroGlassMenu(metadata = mediaMetadata, anchor = menuAnchor, state = state, onDismiss = { menuAnchor = null })
    }
}

/**
 * BitChord's phone Canvas: the video cropped to the whole window, sharp. While the control deck is up
 * it sits under a 50% dim, and the lower 56% is glass that blurs the video behind it live. When the deck
 * slides away, the glass and the dim leave with it and the video plays clear.
 */
@Composable
private fun BoxScope.CanvasBackground(
    canvasUrl: String,
    playing: Boolean,
    alpha: () -> Float,
    layer: GraphicsLayer,
    deckOpen: Boolean,
    onError: () -> Unit,
) {
    CanvasPlayerView(
        canvasUrl = canvasUrl,
        thumbnailUrl = null,
        isPlaying = playing,
        isExpanded = true,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        onError = onError,
    )
    AnimatedVisibility(
        visible = deckOpen,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(CANVAS_DECK_FRACTION),
        enter = fadeIn(tween(CANVAS_DECK_MS)),
        exit = fadeOut(tween(CANVAS_DECK_MS)),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CanvasGlass(layer)
        } else {
            // No RenderEffect: BitChord's flat fallback with the same soft top edge.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color.Black.copy(alpha = 0.40f), 1f to Color.Black.copy(alpha = 0.40f)),
                ),
            )
        }
    }
    val dim = animateFloatAsState(if (deckOpen) 0.5f else 0f, tween(CANVAS_DECK_MS), label = "canvasDim")
    Box(Modifier.matchParentSize().graphicsLayer { this.alpha = dim.value * alpha() }.background(Color.Black))
}

/**
 * The glass under the deck: the same pixels of the video that sit behind it, blurred. [layer] is the
 * whole window's video, so the deck's box samples its own slice, bottom-aligned. Blurred at 1/8 size
 * and scaled up, like BitChord's live frost at a tenth, since it re-blurs on every video frame.
 */
@Composable
private fun CanvasGlass(layer: GraphicsLayer) {
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Progressive top edge, so the glass dissolves into the sharp video above it.
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, CANVAS_DECK_TOP_FADE to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
            }
            .downscaledBlur(),
    ) {
        val source = layer.size
        if (source.width <= 0 || source.height <= 0) return@Canvas
        val scale = size.width / source.width
        withTransform({
            translate(0f, size.height - source.height * scale)
            scale(scale, scale, Offset.Zero)
        }) { drawLayer(layer) }
        drawRect(Color.Black.copy(alpha = 0.18f))
    }
}

/** Lays the content out at 1/[LIVE_BLUR_DOWNSCALE] size, blurs it there, and scales it back up. */
private fun Modifier.downscaledBlur(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val small = measurable.measure(
        Constraints.fixed(
            (width / LIVE_BLUR_DOWNSCALE).roundToInt().coerceAtLeast(1),
            (height / LIVE_BLUR_DOWNSCALE).roundToInt().coerceAtLeast(1),
        ),
    )
    layout(width, height) {
        small.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = LIVE_BLUR_DOWNSCALE
            scaleY = LIVE_BLUR_DOWNSCALE
            val radius = size.minDimension * LIVE_BLUR_RADIUS_FRACTION
            renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
        }
    }
}

private const val CANVAS_DECK_FRACTION = 0.56f
private const val CANVAS_DECK_TOP_FADE = 0.25f
private const val CANVAS_DECK_MS = 420

private val LandscapeCorner = { 12.dp }

/**
 * BitChord's SlidingPlayerDeck: one animated value owns both the pixels and the height the content
 * reports, read only in layout and draw. AnimatedVisibility's expand + slide + fade ran three
 * transitions and re-measured the whole deck every frame; this measures once and only re-places.
 */
@Composable
private fun SlidingDeck(visible: Boolean, content: @Composable () -> Unit) {
    val reveal = remember { Animatable(if (visible) 1f else 0f) }
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) mounted = true
        reveal.animateTo(if (visible) 1f else 0f, tween(DECK_ANIMATION_MS, easing = FastOutSlowInEasing))
        if (!visible) mounted = false
    }
    if (!mounted) return
    Box(
        Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                val height = (placeable.height * reveal.value).roundToInt()
                layout(placeable.width, height) { placeable.placeRelative(0, 0) }
            }.graphicsLayer {
                alpha = reveal.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) { content() }
}

private const val DECK_ANIMATION_MS = 380
private val NoFade = { 0f }
private val FullAlpha = { 1f }

/** Title (opens the album), artist (opens the first artist) and the player's "…" menu. */
@Composable
private fun HeroTitleRow(
    metadata: MediaMetadata,
    state: BottomSheetState,
    onMenu: (androidx.compose.ui.geometry.Rect) -> Unit,
    titleSize: TextUnit,
    artistSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        HeroTitleText(metadata, state, titleSize, artistSize, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        HeroMenuButton(onMenu)
    }
}

@Composable
private fun HeroTitleText(
    metadata: MediaMetadata,
    state: BottomSheetState,
    titleSize: TextUnit,
    artistSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val navController = LocalNavController.current
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    Column(modifier) {
            Text(
                text = metadata.title,
                color = Color.White,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                    .clickable(indication = null, interactionSource = null) {
                        val albumId = metadata.album?.id ?: currentSong?.album?.id ?: currentSong?.song?.albumId
                        if (albumId != null) {
                            navController.navigate("album/$albumId")
                            state.collapseSoft()
                        }
                    },
            )
            Text(
                text = metadata.artists.joinToString { it.name },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = artistSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(indication = null, interactionSource = null) {
                    metadata.artists.firstOrNull { !it.id.isNullOrBlank() }?.id?.let {
                        navController.navigate("artist/$it")
                        state.collapseSoft()
                    }
                },
            )
    }
}

/**
 * Portrait title block for the morph. Text is measured once at its Normal size and only moved and scaled
 * in placement, so the morph never re-measures text the way an animated fontSize would.
 */
@Composable
private fun HeroMorphTitle(
    metadata: MediaMetadata,
    state: BottomSheetState,
    onMenu: (androidx.compose.ui.geometry.Rect) -> Unit,
    morph: () -> Float,
    headerTop: Dp,
    thumbSize: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(
            { HeroTitleText(metadata, state, 26.sp, 22.sp) },
            { HeroMenuButton(onMenu) },
        ),
        modifier = modifier,
    ) { (textMeasurables, menuMeasurables), constraints ->
        val pad = 24.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val headerStart = (24.dp + thumbSize + 14.dp).roundToPx()
        val menu = menuMeasurables.first().measure(Constraints())
        // One width that fits both at full size and, scaled down, beside the thumbnail.
        val textWidth = minOf(
            constraints.maxWidth - 2 * pad - gap - menu.width,
            ((constraints.maxWidth - headerStart - pad - gap - menu.width) / HeaderTitleScale).toInt(),
        ).coerceAtLeast(0)
        val text = textMeasurables.first().measure(Constraints(maxWidth = textWidth))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val t = morph()
            val scale = androidx.compose.ui.util.lerp(1f, HeaderTitleScale, t)
            val rowHeight = maxOf(text.height, menu.height)
            val normalTop = constraints.maxHeight - 12.dp.roundToPx() - rowHeight
            val headerCenter = headerTop.roundToPx() + thumbSize.roundToPx() / 2
            text.placeRelativeWithLayer(
                x = androidx.compose.ui.util.lerp(pad, headerStart, t),
                y = androidx.compose.ui.util.lerp(
                    normalTop + (rowHeight - text.height) / 2,
                    headerCenter - (text.height * scale / 2).roundToInt(),
                    t,
                ),
            ) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            menu.placeRelative(
                x = constraints.maxWidth - pad - menu.width,
                y = androidx.compose.ui.util.lerp(normalTop + (rowHeight - menu.height) / 2, headerCenter - menu.height / 2, t),
            )
        }
    }
}

// 26sp title becomes 20sp beside the thumbnail.
private const val LYRICS_CONTROLS_IDLE_MS = 5_000L
private const val HeaderTitleScale = 20f / 26f

/** Scrubber and times: the only part of the player that recomposes as the position ticks. */
@Composable
private fun HeroProgress(
    positionProvider: () -> Long,
    duration: Long,
    scrubState: MutableState<Long?>,
    onSeek: (Long) -> Unit,
    enabled: Boolean,
    isPlaying: Boolean,
    hPad: Dp,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var scrub by scrubState
    val shownPosition = scrub ?: positionProvider()
    GlassSlider(
        value = if (duration > 0) shownPosition / duration.toFloat() else 0f,
        onValueChange = { if (duration > 0) scrub = (it * duration).toLong() },
        onValueChangeFinished = {
            scrub?.let(onSeek)
            scrub = null
        },
        enabled = enabled,
        isPlaying = isPlaying,
        modifier = Modifier.padding(horizontal = hPad),
    )
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
    ) {
        Text(makeTimeString(shownPosition), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        AudioQualityBadge(playerConnection, Color.White.copy(alpha = 0.6f))
        Text(
            text = if (duration > 0) "-" + makeTimeString((duration - shownPosition).coerceAtLeast(0L)) else "",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
    }
}
