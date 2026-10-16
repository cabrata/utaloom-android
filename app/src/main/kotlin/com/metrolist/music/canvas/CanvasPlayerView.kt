/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.canvas

import android.content.Context
import android.graphics.Matrix
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import coil3.compose.AsyncImage

@OptIn(UnstableApi::class)
@Composable
fun CanvasPlayerView(
    canvasUrl: String?,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    isExpanded: Boolean,
    bottomColor: Color,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)
    var isVideoReady by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        label = "canvasAlpha",
    )

    val exoPlayer = remember(context) {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink? = null
        }
        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, false)
            .setWakeMode(C.WAKE_MODE_NONE)
            .build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }

            override fun onPlayerError(error: PlaybackException) {
                isVideoReady = false
                exoPlayer.pause()
                currentOnError()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.clearVideoSurface()
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, canvasUrl) {
        isVideoReady = false
        videoWidth = 0
        videoHeight = 0
        if (!canvasUrl.isNullOrBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(canvasUrl))
            exoPlayer.prepare()
        } else {
            exoPlayer.clearMediaItems()
        }
    }

    val shouldPlay = isPlaying && isExpanded && !canvasUrl.isNullOrBlank()
    LifecycleResumeEffect(exoPlayer, canvasUrl, shouldPlay) {
        exoPlayer.playWhenReady = shouldPlay
        onPauseOrDispose {
            exoPlayer.pause()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black),
    ) {
        // Fallback: Artwork image
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Canvas video
        if (!canvasUrl.isNullOrBlank()) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        exoPlayer.setVideoTextureView(this)
                        addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                            updateCenterCrop(this, right - left, bottom - top, videoWidth, videoHeight)
                        }
                    }
                },
                update = { view ->
                    exoPlayer.setVideoTextureView(view)
                    updateCenterCrop(view, view.width, view.height, videoWidth, videoHeight)
                },
                onRelease = { view -> exoPlayer.clearVideoTextureView(view) },
                modifier = Modifier.fillMaxSize().alpha(videoAlpha),
            )
        }

        // Top vignette
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Gradient border separating Canvas from Player controls
        if (isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(120.dp)
                    .align(Alignment.CenterEnd)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                bottomColor.copy(alpha = 0.7f),
                                bottomColor,
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                bottomColor.copy(alpha = 0.7f),
                                bottomColor,
                            ),
                        ),
                    ),
            )
        }
    }
}

private fun updateCenterCrop(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val matrix = Matrix()
    val sx = viewWidth.toFloat() / videoWidth
    val sy = viewHeight.toFloat() / videoHeight
    val scale = maxOf(sx, sy)
    val scaledW = videoWidth * scale
    val scaledH = videoHeight * scale
    val dx = (viewWidth - scaledW) / 2f
    val dy = (viewHeight - scaledH) / 2f
    matrix.setScale(scaledW / viewWidth, scaledH / viewHeight, 0f, 0f)
    matrix.postTranslate(dx, dy)
    textureView.setTransform(matrix)
}
