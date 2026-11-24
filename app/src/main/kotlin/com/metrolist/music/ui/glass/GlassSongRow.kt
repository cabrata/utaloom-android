/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.glass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.R
import com.metrolist.music.constants.ListThumbnailSize
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.Icon
import com.metrolist.music.ui.component.ItemThumbnail
import com.metrolist.music.ui.component.ListItem
import com.metrolist.music.ui.theme.GlassShapes
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.utils.joinToArtistString
import com.metrolist.music.utils.makeTimeString

/**
 * Apple Music style track row for long lists: artwork, title, artist, hairline divider.
 * Unlike [com.metrolist.music.ui.component.SongListItem] it opens no preference or download
 * collectors per row, which is what made large playlists stutter while scrolling.
 */
@Composable
fun GlassSongRow(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val artistNameAliases = LocalArtistNameAliases.current
    val and = stringResource(R.string.and)
    val artists = song.orderedArtists.joinToArtistString(" $and ") {
        ArtistNameAliases.resolve(artistNameAliases, it.id, it.name)
    }

    Column(modifier) {
        ListItem(
            title = song.song.title,
            subtitle = {
                if (song.song.explicit) Icon.Explicit()
                Text(
                    text = joinByBullet(artists, makeTimeString(song.song.duration * 1000L)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.song.thumbnailUrl?.let { if (song.isDownloaded) it else it.resize(200, 200) },
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = GlassShapes.Artwork,
                    modifier = Modifier.size(ListThumbnailSize),
                )
            },
            trailingContent = trailingContent,
            isSelected = isSelected,
            isActive = isActive,
        )
        if (showDivider) {
            // Inset to the text column: row padding 8 + thumbnail inset 6 + art 48 + text inset 6.
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
