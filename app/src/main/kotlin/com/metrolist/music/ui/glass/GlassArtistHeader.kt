/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */

package com.metrolist.music.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import com.metrolist.music.ui.theme.GlassShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.theme.isUtaloomGlass

private val ARTIST_HEADER_HEIGHT = 360.dp
private val ARTIST_PLAY_BUTTON_SIZE = 56.dp

/**
 * Glass-mode artist header. Full-bleed image with name overlaid and play button.
 * Apple Music style.
 */
@Composable
fun GlassArtistHeader(
    name: String,
    imageUrl: String? = null,
    onShuffle: (() -> Unit)? = null,
    onSubscribe: (() -> Unit)? = null,
    isSubscribed: Boolean = false,
    onRadio: (() -> Unit)? = null,
) {
    if (!isUtaloomGlass) return
    val page = MaterialTheme.colorScheme.background
    val fade = remember(page) { Brush.verticalGradient(0.45f to Color.Transparent, 1f to page) }

    Column(Modifier.fillMaxWidth()) {
        // Apple Music's artist page: the photo runs edge to edge and dissolves into the page,
        // the name sits bottom-left over it and the play control bottom-right.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ARTIST_HEADER_HEIGHT),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Box(Modifier.matchParentSize().background(fade))
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onShuffle != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(ARTIST_PLAY_BUTTON_SIZE)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onShuffle),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = stringResource(R.string.shuffle),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            onSubscribe?.let {
                ArtistCapsule(
                    label = stringResource(if (isSubscribed) R.string.subscribed else R.string.subscribe),
                    icon = null,
                    onClick = it,
                )
            }
            onRadio?.let { ArtistCapsule(label = stringResource(R.string.radio), icon = R.drawable.radio, onClick = it) }
        }
    }
}

@Composable
private fun ArtistCapsule(label: String, icon: Int?, onClick: () -> Unit) {
    val edge = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(36.dp)
            .clip(GlassShapes.Capsule)
            .background(edge.copy(alpha = 0.07f))
            .liquidGlassRim(GlassShapes.Capsule)
            .border(1.dp, edge.copy(alpha = 0.10f), GlassShapes.Capsule)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        icon?.let {
            Icon(painterResource(it), contentDescription = null, tint = edge, modifier = Modifier.size(16.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = edge)
    }
}
