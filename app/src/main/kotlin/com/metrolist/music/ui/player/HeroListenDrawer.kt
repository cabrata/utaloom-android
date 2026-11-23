/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.glass.backdrop.Backdrop
import com.metrolist.music.ui.menu.ListenTogetherDialog

/**
 * Listen Together in the Liquid Glass player's drawer. The dialog's own list is reused as is; the Material
 * colours are swapped for white-on-glass ones, since the drawer is dark whatever the app theme is.
 */
@Composable
internal fun HeroListenDrawer(
    visible: Boolean,
    mediaMetadata: MediaMetadata?,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
) {
    HeroDrawer(
        visible = visible,
        title = stringResource(R.string.listen_together),
        thumbnailUrl = mediaMetadata?.thumbnailUrl,
        backdrop = backdrop,
        onDismiss = onDismiss,
    ) {
        // Composed only while open: it holds a collector of the room's events.
        if (visible) {
            val colors = MaterialTheme.colorScheme
            val white = Color.White
            MaterialTheme(
                colorScheme = colors.copy(
                    primary = white,
                    onPrimary = Color(0xFF1C1C1E),
                    primaryContainer = white.copy(alpha = 0.14f),
                    onPrimaryContainer = white,
                    secondary = white.copy(alpha = 0.75f),
                    onSecondary = Color(0xFF1C1C1E),
                    surface = Color.Transparent,
                    onSurface = white.copy(alpha = 0.92f),
                    surfaceVariant = white.copy(alpha = 0.08f),
                    onSurfaceVariant = white.copy(alpha = 0.6f),
                    outline = white.copy(alpha = 0.4f),
                    outlineVariant = white.copy(alpha = 0.14f),
                    error = Color(0xFFFF6B63),
                    errorContainer = Color(0xFFFF6B63).copy(alpha = 0.22f),
                    onErrorContainer = Color(0xFFFF6B63),
                ),
            ) {
                ListenTogetherDialog(
                    visible = true,
                    mediaMetadata = mediaMetadata,
                    onDismiss = onDismiss,
                    host = { _, content ->
                        // The first argument is "back" for a sub-view; the drawer's own dismiss stays the scrim.
                        Column(Modifier.fillMaxWidth()) { LazyColumn(content = content) }
                    },
                )
            }
        }
    }
}
