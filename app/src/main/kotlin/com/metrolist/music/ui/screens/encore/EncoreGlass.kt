/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.encore

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val EncoreStageBase = Color(0xFF0B0A10)

/**
 * Three colours of [coverUrl]'s artwork for the mesh. Keeps the previous artwork's colours until
 * the new ones are ready, so turning a story never flashes the theme colours in between.
 */
@Composable
internal fun rememberEncoreMeshColors(coverUrl: String?): List<Color> {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var extracted by remember { mutableStateOf<List<Color>?>(null) }
    LaunchedEffect(coverUrl) {
        if (coverUrl == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context).data(coverUrl).size(100, 100).allowHardware(false).build()
            val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
                ?: return@withContext null
            val palette = Palette.from(bitmap).maximumColorCount(12).generate()
            val fallback = scheme.primary.toArgb()
            listOf(
                palette.getVibrantColor(fallback),
                palette.getMutedColor(palette.getDominantColor(fallback)),
                palette.getLightVibrantColor(palette.getDarkVibrantColor(fallback)),
            ).map(::Color)
        }?.let { extracted = it }
    }
    return extracted ?: listOf(scheme.primary, scheme.tertiary, scheme.secondary)
}

/** Turns [color] round the colour wheel, so every story gets its own tint of its artwork. */
internal fun Color.hueShifted(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    hsv[1] = hsv[1].coerceIn(0.45f, 0.9f)
    hsv[2] = hsv[2].coerceIn(0.5f, 0.85f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** The story's highlight: its most colourful mesh colour, lifted until it reads on the dark stage. */
internal fun encoreAccentOf(colors: List<Color>): Color {
    val hsl = FloatArray(3)
    val best = colors.maxByOrNull { color ->
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] * (1f - kotlin.math.abs(hsl[2] - 0.5f))
    } ?: return Color.White
    ColorUtils.colorToHSL(best.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.55f)
    hsl[2] = 0.72f
    return Color(ColorUtils.HSLToColor(hsl))
}

/** The stage behind every story: a still mesh of [colors], crossfading as the story changes. */
@Composable
internal fun EncoreGlassStage(colors: List<Color>, modifier: Modifier = Modifier) {
    val tints = colors.mapIndexed { i, color ->
        animateColorAsState(color, tween(900), label = "encoreMesh$i").value
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(EncoreStageBase)
        val radius = size.maxDimension * 0.62f
        fun blob(color: Color, center: Offset) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.85f), Color.Transparent), center, radius),
            radius,
            center,
        )
        blob(tints[0], Offset(size.width * 0.15f, size.height * 0.15f))
        blob(tints[1], Offset(size.width * 0.9f, size.height * 0.55f))
        blob(tints[2], Offset(size.width * 0.3f, size.height * 0.95f))
        // Keeps white text readable over any artwork.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.45f),
                0.35f to Color.Black.copy(alpha = 0.1f),
                1f to Color.Black.copy(alpha = 0.34f),
            ),
        )
    }
}
