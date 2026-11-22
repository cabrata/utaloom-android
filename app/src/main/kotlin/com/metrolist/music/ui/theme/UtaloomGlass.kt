/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.metrolist.music.constants.AppDesignStyle
import com.metrolist.music.ui.glass.SquirclePillShape
import com.metrolist.music.ui.glass.continuous

/** Which visual language the app is drawn in. Provided by [MetrolistTheme]. */
val LocalAppDesignStyle = staticCompositionLocalOf { AppDesignStyle.MATERIAL3 }

val isUtaloomGlass: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAppDesignStyle.current == AppDesignStyle.UTALOOM_GLASS

/** Continuous (squircle) corners used by Utaloom Glass surfaces. */
object GlassShapes {
    // Plain rounded rects, as BitChord uses for list art: they clip on the RenderNode, while a
    // continuous corner is a path clip on every thumbnail, every scroll frame. Invisible at 8–12dp.
    val Artwork = RoundedCornerShape(8.dp)
    val ArtworkLarge = RoundedCornerShape(12.dp)
    val Group = RoundedCornerShape(26.dp).continuous()
    val Sheet = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp).continuous()
    val Alert = RoundedCornerShape(26.dp).continuous()
    val Button = RoundedCornerShape(12.dp).continuous()
    val Capsule = SquirclePillShape()
}

/**
 * iOS system colors: black/white pages, grouped grays for cells and fills, and one accent.
 */
internal fun ColorScheme.utaloomGlass(dark: Boolean, accent: Color, onAccent: Color): ColorScheme =
    if (dark) {
        copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.22f),
            onPrimaryContainer = accent,
            // Material's "secondary" carries subtitles here; iOS sets those in system gray.
            secondary = Color(0xFF8E8E93),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF2C2C2E),
            onSecondaryContainer = Color.White,
            tertiary = accent,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1C1C1E),
            onSurfaceVariant = Color(0x99EBEBF5),
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF2C2C2E),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF1C1C1E),
            surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF3A3A3C),
            outline = Color(0xFF545458),
            outlineVariant = Color(0xFF38383A),
            inverseSurface = Color.White,
            inverseOnSurface = Color.Black,
        )
    } else {
        copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.14f),
            onPrimaryContainer = accent,
            // Material's "secondary" carries subtitles here; iOS sets those in system gray.
            secondary = Color(0xFF8E8E93),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE5E5EA),
            onSecondaryContainer = Color.Black,
            tertiary = accent,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF2F2F7),
            onSurfaceVariant = Color(0x993C3C43),
            surfaceDim = Color(0xFFF2F2F7),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF2F2F7),
            surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFFE5E5EA),
            surfaceContainerHighest = Color(0xFFD1D1D6),
            outline = Color(0xFFC6C6C8),
            outlineVariant = Color(0xFFD1D1D6),
            inverseSurface = Color.Black,
            inverseOnSurface = Color.White,
        )
    }

/** Bolder headings with tighter tracking, the way iOS sets its titles. Sizes stay Material's. */
internal fun Typography.utaloomGlass(): Typography {
    fun TextStyle.title() = copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    fun TextStyle.semibold() = copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    return copy(
        displayLarge = displayLarge.title(),
        displayMedium = displayMedium.title(),
        displaySmall = displaySmall.title(),
        headlineLarge = headlineLarge.title(),
        headlineMedium = headlineMedium.title(),
        headlineSmall = headlineSmall.title(),
        titleLarge = titleLarge.title(),
        titleMedium = titleMedium.semibold(),
        titleSmall = titleSmall.semibold(),
        labelLarge = labelLarge.semibold(),
    )
}
