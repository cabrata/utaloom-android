/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */

package com.metrolist.music.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import com.metrolist.music.ui.theme.GlassShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val ARTWORK_SIZE = 240.dp
private val BUTTON_SIZE = 50.dp
private val ACTION_GAP = 14.dp
private val HEADER_PADDING = 24.dp

/**
 * Glass-mode detail header for albums/playlists. Apple Music style with centered artwork,
 * title, artist/owner text, metadata, and play/shuffle buttons.
 */
@Composable
fun GlassDetailHeader(
    title: String,
    artistText: String = "",
    metadataText: String = "",
    thumbnailUrl: String? = null,
    artwork: (@Composable () -> Unit)? = null,
    onPlay: () -> Unit,
    onShuffle: (() -> Unit)? = null,
    onLikeClick: (() -> Unit)? = null,
    isLiked: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    onArtistClick: (() -> Unit)? = null,
) {
    if (!isUtaloomGlass) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (artwork != null) {
            artwork()
        } else {
            // Artwork container with continuous corners
            Surface(
                modifier = Modifier
                    .size(ARTWORK_SIZE)
                    .shadow(
                        elevation = 12.dp,
                        shape = GlassShapes.ArtworkLarge,
                    ),
                shape = GlassShapes.ArtworkLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = HEADER_PADDING),
        )

        if (artistText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artistText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = HEADER_PADDING)
                    .let { m ->
                        if (onArtistClick != null) {
                            m.clip(RoundedCornerShape(6.dp))
                                .clickable { onArtistClick() }
                        } else {
                            m
                        }
                    },
            )
        }

        if (metadataText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = metadataText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HEADER_PADDING),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // BitChord's release row: Shuffle and the side actions as circles around a filled Play pill.
        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = HEADER_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onShuffle?.let {
                CircleAction(R.drawable.shuffle, stringResource(R.string.shuffle), onClick = it)
            }
            PlayPill(onClick = onPlay, modifier = Modifier.weight(1f))
            onLikeClick?.let {
                CircleAction(
                    icon = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                    contentDescription = null,
                    tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    onClick = it,
                )
            }
            onMenuClick?.let {
                CircleAction(R.drawable.more_horiz, contentDescription = null, onClick = it)
            }
        }
    }
}

@Composable
private fun PlayPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.onSurface
    val content = MaterialTheme.colorScheme.surface
    Row(
        modifier = modifier
            .height(BUTTON_SIZE)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.play), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.play), style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
    }
}

@Composable
private fun CircleAction(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val edge = MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(BUTTON_SIZE)
            .clip(CircleShape)
            .background(edge.copy(alpha = 0.07f), CircleShape)
            .liquidGlassRim(CircleShape)
            .border(1.dp, edge.copy(alpha = 0.10f), CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}
