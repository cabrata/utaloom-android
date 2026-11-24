/*
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 * The glass rendering is Kyant0/backdrop (Apache-2.0), vendored in
 * [com.metrolist.music.ui.glass.backdrop]; this file is the integration glue.
 */
package com.metrolist.music.ui.glass

import android.os.Build
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.glass.backdrop.Backdrop
import com.metrolist.music.ui.glass.backdrop.backdrops.LayerBackdrop
import com.metrolist.music.ui.glass.backdrop.drawBackdrop
import com.metrolist.music.ui.glass.backdrop.effects.blur
import com.metrolist.music.ui.glass.backdrop.effects.colorControls
import com.metrolist.music.ui.glass.backdrop.effects.lens
import com.metrolist.music.ui.glass.backdrop.highlight.Highlight
import com.metrolist.music.ui.glass.backdrop.highlight.HighlightElement
import com.metrolist.music.ui.glass.backdrop.internal.ShapeProvider
import com.metrolist.music.ui.glass.backdrop.shadow.Shadow
import com.metrolist.music.ui.theme.isUtaloomGlass

/**
 * The navigation content that glass chrome samples. Provided by MainActivity in
 * Utaloom Glass; null elsewhere (dialogs, other activities, Material 3), where
 * [liquidGlass] falls back to a solid fill.
 */
val LocalAppBackdrop = compositionLocalOf<Backdrop?> { null }

/** Records the glass a surface draws so a lens above it can refract it (tab bar selection pill). */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

/** Backdrop blur needs RenderEffect on a RenderNode (API 31). */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Spring shared by glass chrome motion. */
val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.4f

// A third of the pixels is enough: the blur hides the upscale, and up to six surfaces render at once.
private const val GLASS_RESOLUTION_SCALE = 0.33f

internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

private const val DARK_INDICATOR_OPACITY = 0.14f

private val LightGlassTint = Color(0xFFFAFAFA)
private val DarkGlassTint = Color(0xFF121212)

@Composable
internal fun isLightTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

/** Icon/label colour on glass: pure black or white, the only tint that holds over arbitrary artwork. */
@Composable
fun glassContentColor(): Color = if (isLightTheme()) Color.Black else Color.White

/** Selected-tab pill on glass. */
@Composable
fun glassIndicatorColor(): Color =
    // A white lift vanishes on light glass, so light theme presses a faint grey in instead.
    if (isLightTheme()) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = DARK_INDICATOR_OPACITY)

/** Whether glass chrome should render (Utaloom Glass on a device with RenderEffect). */
@Composable
fun glassActive(): Boolean = isUtaloomGlass && isGlassSupported()

/**
 * Same tint, highlight and hairline as [liquidGlass] but no backdrop capture, for controls over a
 * stable background. Uses [fallbackColor] when glass is off or unsupported.
 */
@Composable
fun Modifier.lightweightLiquidGlass(shape: CornerBasedShape, fallbackColor: Color): Modifier {
    val useGlass = glassActive()
    val tint = if (isLightTheme()) LightGlassTint else DarkGlassTint
    val shapeProvider = remember(shape) { ShapeProvider { shape } }
    return clip(shape)
        .background(if (useGlass) tint.copy(alpha = SURFACE_OPACITY) else fallbackColor, shape)
        .then(if (useGlass) HighlightElement(shapeProvider = shapeProvider, highlight = { Highlight.Default }) else Modifier)
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/** Only the directional rim of glass, for a control that keeps its own fill. */
@Composable
fun Modifier.liquidGlassRim(shape: CornerBasedShape): Modifier {
    if (!glassActive()) return this
    val shapeProvider = remember(shape) { ShapeProvider { shape } }
    return then(HighlightElement(shapeProvider = shapeProvider, highlight = { Highlight.Default }))
}

/**
 * Liquid glass sampling [LocalAppBackdrop]: vibrancy, blur, lens refraction (API 33+), then a
 * theme-adaptive tint. Without a backdrop or RenderEffect it fills with the theme surface instead.
 * [shape] must be a [CornerBasedShape]; the lens shader reads its corner radii.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    surfaceTint: Color? = null,
): Modifier {
    val backdrop = LocalAppBackdrop.current
    if (!isGlassSupported() || backdrop == null) {
        return background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val tint = surfaceTint ?: (if (isLightTheme()) LightGlassTint else DarkGlassTint).copy(alpha = SURFACE_OPACITY)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(color = tint, size = size) },
        exportedBackdrop = exportedBackdrop,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}
