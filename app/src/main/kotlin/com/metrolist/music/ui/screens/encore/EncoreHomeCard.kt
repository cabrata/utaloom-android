package com.metrolist.music.ui.screens.encore

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.metrolist.music.ui.glass.backdrop.shadow.Shadow
import com.metrolist.music.ui.glass.continuous
import com.metrolist.music.ui.glass.glassActive
import com.metrolist.music.ui.glass.glassContentColor
import com.metrolist.music.ui.glass.isLightTheme
import com.metrolist.music.ui.glass.lightweightLiquidGlass
import kotlin.math.roundToInt

@Composable
internal fun EncoreHomeCard(year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val action = stringResource(R.string.encore_recap_view)
    val glass = glassActive()
    // The card's own content, recorded so the bubbles and the arrow can refract it. The page's
    // backdrop can't be used here: the card is part of that recording.
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
            GlassCardContent(year, cardBackdrop)
        } else {
            // Same shape language as the stories, so the banner reads as their cover.
            Canvas(Modifier.matchParentSize()) {
                val unit = size.height
                drawCircle(EncorePeach, unit * 0.62f, Offset(size.width - unit * 0.18f, unit * 0.08f))
                drawCircle(EncorePeach, unit * 0.2f, Offset(size.width * 0.58f, size.height * 1.02f), style = Stroke(unit * 0.07f))
            }
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EncoreCardText(year, EncoreInk, glass = false, modifier = Modifier.weight(1f))
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

@Composable
private fun EncoreCardText(year: Int, ink: Color, glass: Boolean, modifier: Modifier = Modifier) {
    // On glass the hierarchy comes from weight and opacity, not colour, like Apple's vibrant labels.
    val secondary = if (glass) ink.copy(alpha = 0.72f) else ink
    Column(modifier) {
        Text(
            stringResource(R.string.encore_recap_title, year),
            color = secondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            year.toString(),
            color = ink,
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
            color = secondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Glass card: a slow wash of theme colour and the text are recorded into [backdrop], then soap
 * bubbles and a blue glass arrow float over it and bend it. All motion is read in draw or
 * placement lambdas, so frames only redraw, never recompose.
 */
@Composable
private fun BoxScope.GlassCardContent(year: Int, backdrop: LayerBackdrop) {
    val light = isLightTheme()
    val scheme = MaterialTheme.colorScheme
    // Light schemes have deep primaries that muddy a pale card, so the wash uses their pastel containers.
    val warmTint = if (light) scheme.primaryContainer.copy(alpha = 0.9f) else scheme.primary.copy(alpha = 0.42f)
    val coolTint = if (light) scheme.tertiaryContainer.copy(alpha = 0.75f) else scheme.tertiary.copy(alpha = 0.24f)
    val filmAlpha = if (light) 0.7f else 1f
    val transition = rememberInfiniteTransition(label = "encoreGlass")
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
    val swirl = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
        label = "filmSwirl",
    )
    Box(Modifier.fillMaxWidth().layerBackdrop(backdrop)) {
        Canvas(Modifier.matchParentSize()) {
            val unit = size.height
            val warm = Offset(size.width - unit * (0.25f + 0.12f * sway.value), unit * (0.1f + 0.08f * bob.value))
            drawCircle(
                Brush.radialGradient(listOf(warmTint, Color.Transparent), center = warm, radius = unit),
                radius = unit,
                center = warm,
            )
            val cool = Offset(size.width * (0.3f - 0.08f * sway.value), size.height)
            drawCircle(
                Brush.radialGradient(listOf(coolTint, Color.Transparent), center = cool, radius = unit * 0.8f),
                radius = unit * 0.8f,
                center = cool,
            )
        }
        EncoreCardText(
            year,
            glassContentColor(),
            glass = true,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 20.dp, end = 84.dp),
        )
    }
    SoapBubble(backdrop, 92.dp, swirl, phase = 0f, filmAlpha, Modifier.align(Alignment.TopEnd)) {
        IntOffset((-36).dp.roundToPx() + (sway.value * 6.dp.toPx()).roundToInt(), 14.dp.roundToPx() + (bob.value * 7.dp.toPx()).roundToInt())
    }
    SoapBubble(backdrop, 34.dp, swirl, phase = 140f, filmAlpha, Modifier.align(Alignment.BottomCenter)) {
        IntOffset(36.dp.roundToPx() - (sway.value * 5.dp.toPx()).roundToInt(), (-12).dp.roundToPx() - (bob.value * 9.dp.toPx()).roundToInt())
    }
    SoapBubble(backdrop, 20.dp, swirl, phase = 250f, filmAlpha, Modifier.align(Alignment.TopEnd)) {
        IntOffset((-142).dp.roundToPx() + (bob.value * 4.dp.toPx()).roundToInt(), 26.dp.roundToPx() + (sway.value * 6.dp.toPx()).roundToInt())
    }
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp)
            .size(48.dp)
            .liquidLens(backdrop, 48.dp, floating = false) { drawRect(GlassBlue) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.arrow_forward), null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

private val GlassCardShape = RoundedCornerShape(24.dp).continuous()

private val GlassBlue = Color(0xFF0A84FF).copy(alpha = 0.62f)

// Thin-film interference: two opposite arcs of colour with clear gaps, as on a real soap bubble.
private val FilmColors = listOf(
    Color.Transparent,
    Color(0xFFFF5AC8),
    Color(0xFFFFD25A),
    Color(0xFF5AF0D2),
    Color(0xFF5A82FF),
    Color.Transparent,
    Color.Transparent,
    Color(0xFF5AC8FF),
    Color(0xFFB45AFF),
    Color(0xFFFF7A5A),
    Color(0xFFFFE65A),
    Color.Transparent,
    Color.Transparent,
)

@Composable
private fun SoapBubble(
    backdrop: LayerBackdrop,
    size: Dp,
    swirl: State<Float>,
    phase: Float,
    filmAlpha: Float,
    modifier: Modifier,
    offset: Density.() -> IntOffset,
) {
    Box(modifier.offset(offset).size(size).liquidLens(backdrop, size, floating = true) { drawSoapFilm(swirl.value + phase, filmAlpha) })
}

/** Iridescent rim fading into a clear centre, plus the glint of a window caught on the film. */
private fun DrawScope.drawSoapFilm(angle: Float, alpha: Float) {
    val r = size.minDimension / 2f
    drawContext.canvas.saveLayer(size.toRect(), Paint().apply { this.alpha = alpha })
    rotate(angle) { drawCircle(Brush.sweepGradient(FilmColors), r) }
    drawCircle(
        Brush.radialGradient(
            0.55f to Color.Transparent,
            0.88f to Color.White.copy(alpha = 0.45f),
            1f to Color.White,
            center = center,
            radius = r,
        ),
        r,
        blendMode = BlendMode.DstIn,
    )
    drawContext.canvas.restore()
    drawArc(
        Color.White.copy(alpha = 0.55f),
        startAngle = 200f,
        sweepAngle = 42f,
        useCenter = false,
        topLeft = Offset(r * 0.3f, r * 0.3f),
        size = Size(r * 1.4f, r * 1.4f),
        style = Stroke(r * 0.07f, cap = StrokeCap.Round),
    )
}

/** A circle of liquid glass: it lifts the colour behind it, bends it at the rim, and catches light. */
@Composable
private fun Modifier.liquidLens(
    backdrop: LayerBackdrop,
    size: Dp,
    floating: Boolean,
    surface: DrawScope.() -> Unit,
): Modifier {
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
        // Bubbles weigh nothing, so they cast no shadow.
        shadow = if (floating) null else ({ Shadow.Default }),
        onDrawSurface = surface,
    )
}
