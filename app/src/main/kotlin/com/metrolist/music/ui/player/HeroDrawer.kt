/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.glass.LocalAppBackdrop
import com.metrolist.music.ui.glass.backdrop.Backdrop
import com.metrolist.music.ui.glass.backdrop.drawBackdrop
import com.metrolist.music.ui.glass.backdrop.effects.blur
import com.metrolist.music.ui.glass.backdrop.effects.colorControls
import kotlin.math.roundToInt

internal val DrawerShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
internal val PopupShape = RoundedCornerShape(14.dp)
private val GlassFill = Color(0xFF1C1C1E)

/**
 * BitChord's bottom drawer for the Liquid Glass player: rises off the bottom edge over a scrim and drags down by its
 * header to dismiss. With a [backdrop] of the player (Android 12+) it frosts the cover and controls behind it as
 * BitChord's menu does; otherwise it uses the cached cover blur. Drawn inside the player rather than in a window so
 * the frost sees what is behind it. [content] scrolls itself; [overlay] draws above the drawer (popups).
 */
@Composable
internal fun HeroDrawer(
    visible: Boolean,
    title: String,
    thumbnailUrl: String?,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    onBack: () -> Unit = onDismiss,
    overlay: @Composable BoxScope.(HeroFrost, () -> IntSize, Offset) -> Unit = { _, _, _ -> },
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onBack)

    val frost = rememberHeroFrost(thumbnailUrl)
    var overlayPos by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var panelOffset by remember { mutableStateOf(IntOffset.Zero) }
    var drag by remember { mutableFloatStateOf(0f) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val dragOffset by animateFloatAsState(drag, spring(stiffness = Spring.StiffnessMediumLow), label = "heroDrawerDrag")
    LaunchedEffect(visible) { if (!visible) drag = 0f }
    val maxPanel = with(LocalDensity.current) {
        if (overlaySize.height > 0) (overlaySize.height * 0.86f).toDp() else Dp.Unspecified
    }

    CompositionLocalProvider(LocalContentColor provides Color.White, LocalAppBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    overlayPos = it.positionInWindow()
                    overlaySize = it.size
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
                val fade = if (panelHeight > 0) (1f - dragOffset / panelHeight).coerceIn(0f, 1f) else 1f
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f * fade))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(180)) { it },
            ) {
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxPanel)
                        .onSizeChanged { panelHeight = it.height }
                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                        .onGloballyPositioned { panelOffset = (it.positionInWindow() - overlayPos).round() }
                        .playerGlass(backdrop != null, DrawerShape, frost, { overlaySize }, { panelOffset })
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp),
                ) {
                    // Only the header drags: the rows below scroll and would swallow the gesture anyway.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = { if (panelHeight > 0 && drag > panelHeight * 0.25f) onDismiss() else drag = 0f },
                                    onDragCancel = { drag = 0f },
                                ) { _, delta -> drag = (drag + delta).coerceAtLeast(0f) }
                            }
                            .padding(top = 10.dp, bottom = 14.dp),
                    ) {
                        Box(
                            Modifier
                                .padding(bottom = 12.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                        )
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        )
                    }
                    content()
                }
            }
            overlay(frost, { overlaySize }, overlayPos)
        }
    }
}

/**
 * BitChord's menu frost (menuBackdropFrost): the player behind, blurred 24dp with its hue drawn up 1.8x,
 * under a charcoal slab at 80% and a hairline rim. No lens or highlight. Without a recorded backdrop
 * (below Android 12) the cached cover frost stands in.
 */
@Composable
internal fun Modifier.playerGlass(
    liveGlass: Boolean,
    shape: RoundedCornerShape,
    frost: HeroFrost,
    overlaySize: () -> IntSize,
    offset: () -> IntOffset,
): Modifier {
    val backdrop = LocalAppBackdrop.current
    val material = if (liveGlass && backdrop != null) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(saturation = 1.8f)
                blur(24.dp.toPx() * FROST_SCALE)
            },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(GlassFill.copy(alpha = 0.80f)) },
            // Sampled small: the blur hides the upscale, and the effect runs over a fraction of the pixels.
            backdropScale = FROST_SCALE,
        ).clip(shape)
    } else {
        clip(shape).heroFrost(frost, overlaySize, offset)
    }
    return material.border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
}

private const val FROST_SCALE = 0.25f
