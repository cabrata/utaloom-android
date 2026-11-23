package com.metrolist.music.ui.screens.encore

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.ui.glass.backdrop.backdrops.LayerBackdrop
import com.metrolist.music.ui.glass.backdrop.backdrops.layerBackdrop
import com.metrolist.music.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import com.metrolist.music.ui.glass.backdrop.drawBackdrop
import com.metrolist.music.ui.glass.backdrop.effects.blur
import com.metrolist.music.ui.glass.backdrop.effects.colorControls
import com.metrolist.music.ui.glass.backdrop.effects.lens
import com.metrolist.music.ui.glass.continuous
import com.metrolist.music.ui.glass.glassActive
import com.metrolist.music.ui.glass.glassContentColor
import com.metrolist.music.ui.glass.lightweightLiquidGlass
import kotlin.math.roundToInt

@Composable
internal fun EncoreHomeCard(year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val action = stringResource(R.string.encore_recap_view)
    val glass = glassActive()
    val ink = if (glass) glassContentColor() else EncoreInk
    val accent = MaterialTheme.colorScheme.primary
    // The card's own glow, recorded so the bubbles and the arrow can refract it. The page's backdrop
    // can't be used here: the card is part of that recording.
    val cardBackdrop = if (glass) rememberLayerBackdrop() else null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (glass) {
                    Modifier.lightweightLiquidGlass(GlassCardShape, fallbackColor = EncoreCoral)
                } else {
                    Modifier.clip(RoundedCornerShape(20.dp)).background(EncoreCoral)
                },
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$action, $year"
            },
    ) {
        if (cardBackdrop != null) {
            val tertiary = MaterialTheme.colorScheme.tertiary
            Canvas(Modifier.matchParentSize().layerBackdrop(cardBackdrop)) {
                val unit = size.height
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width - unit * 0.1f, unit * 0.05f),
                        radius = unit * 0.95f,
                    ),
                    radius = unit * 0.95f,
                    center = Offset(size.width - unit * 0.1f, unit * 0.05f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(tertiary.copy(alpha = 0.3f), Color.Transparent),
                        center = Offset(size.width * 0.3f, size.height),
                        radius = unit * 0.8f,
                    ),
                    radius = unit * 0.8f,
                    center = Offset(size.width * 0.3f, size.height),
                )
                // The Encore rings in the theme colour, so the lenses have edges to bend.
                drawCircle(accent.copy(alpha = 0.45f), unit * 0.62f, Offset(size.width - unit * 0.18f, unit * 0.08f), style = Stroke(unit * 0.025f))
                drawCircle(accent.copy(alpha = 0.35f), unit * 0.2f, Offset(size.width * 0.58f, size.height * 1.02f), style = Stroke(unit * 0.06f))
            }
            GlassBubbles(cardBackdrop)
        } else {
            // Same shape language as the stories, so the banner reads as their cover.
            Canvas(Modifier.matchParentSize()) {
                val unit = size.height
                drawCircle(EncorePeach, unit * 0.62f, Offset(size.width - unit * 0.18f, unit * 0.08f))
                drawCircle(EncorePeach, unit * 0.2f, Offset(size.width * 0.58f, size.height * 1.02f), style = Stroke(unit * 0.07f))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.encore_recap_title, year),
                    color = ink,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    year.toString(),
                    color = if (glass) accent else ink,
                    fontFamily = EncoreDisplayFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                )
                Text(
                    stringResource(R.string.encore_recap_open),
                    color = ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.encore_recap_home_subtitle),
                    color = ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (cardBackdrop != null) {
                Box(
                    Modifier
                        .size(48.dp)
                        .liquidLens(cardBackdrop, 48.dp, tint = accent.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.arrow_forward), null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            } else {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(EncoreInk),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.arrow_forward), null, tint = EncoreCoral, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

private val GlassCardShape = RoundedCornerShape(24.dp).continuous()

/**
 * Liquid glass drops over the card's glow, each bobbing on its own phase. The motion is read in
 * the placement lambda, so frames only re-place and redraw the drops, never recompose the card.
 */
@Composable
private fun BoxScope.GlassBubbles(backdrop: LayerBackdrop) {
    val transition = rememberInfiniteTransition(label = "glassBubbles")
    val bob = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bubbleBob",
    )
    val sway = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bubbleSway",
    )
    GlassBubble(backdrop, 92.dp, Modifier.align(Alignment.TopEnd)) {
        IntOffset((-40).dp.roundToPx() + (sway.value * 6.dp.toPx()).roundToInt(), 18.dp.roundToPx() + (bob.value * 7.dp.toPx()).roundToInt())
    }
    GlassBubble(backdrop, 40.dp, Modifier.align(Alignment.BottomCenter)) {
        IntOffset(28.dp.roundToPx() - (sway.value * 5.dp.toPx()).roundToInt(), (-14).dp.roundToPx() - (bob.value * 9.dp.toPx()).roundToInt())
    }
    GlassBubble(backdrop, 22.dp, Modifier.align(Alignment.TopEnd)) {
        IntOffset((-150).dp.roundToPx() + (bob.value * 4.dp.toPx()).roundToInt(), 30.dp.roundToPx() + (sway.value * 6.dp.toPx()).roundToInt())
    }
}

@Composable
private fun GlassBubble(backdrop: LayerBackdrop, size: Dp, modifier: Modifier, offset: Density.() -> IntOffset) {
    Box(modifier.offset(offset).size(size).liquidLens(backdrop, size, tint = Color.White.copy(alpha = 0.05f)))
}

/** A circle of liquid glass: it lifts the colour behind it, bends it at the rim, and catches light. */
@Composable
private fun Modifier.liquidLens(backdrop: LayerBackdrop, size: Dp, tint: Color): Modifier {
    val radius = with(LocalDensity.current) { size.toPx() } / 2f
    val soften = with(LocalDensity.current) { 1.5.dp.toPx() }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            colorControls(saturation = 1.5f)
            blur(soften)
            lens(refractionHeight = radius * 0.55f, refractionAmount = radius * 0.9f, depthEffect = true)
        },
        onDrawSurface = { drawRect(tint) },
    )
}
