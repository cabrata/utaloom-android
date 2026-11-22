/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.ArtistGridItem
import com.metrolist.music.ui.component.CreatePlaylistDialog
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PlaylistGridItem
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.ArtistMenu
import com.metrolist.music.ui.menu.PlaylistMenu
import com.metrolist.music.ui.theme.GlassShapes
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.viewmodels.LibraryAlbumsViewModel
import com.metrolist.music.viewmodels.LibraryArtistsViewModel
import com.metrolist.music.viewmodels.LibraryPlaylistsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassLibraryHome(
    navController: NavController,
    onOpenFilter: (LibraryFilter) -> Unit,
    playlistViewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    albumViewModel: LibraryAlbumsViewModel = hiltViewModel(),
    artistViewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by playerConnection?.isEffectivelyPlaying?.collectAsStateWithLifecycle(false) ?: run { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle() ?: run { mutableStateOf(null) }

    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val playlists by playlistViewModel.allPlaylists.collectAsStateWithLifecycle(emptyList())
    val albums by albumViewModel.allAlbums.collectAsStateWithLifecycle(emptyList())
    val artists by artistViewModel.allArtists.collectAsStateWithLifecycle(emptyList())

    val sortedPlaylists = remember(playlists) {
        playlists.distinctBy { it.id }
    }
    val recentAlbums = remember(albums) {
        albums.distinctBy { it.id }.take(10)
    }
    val recentArtists = remember(artists) {
        artists.distinctBy { it.id }.take(10)
    }

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        // Link List
        item(key = "link_list") {
            GlassLibraryLinkList(
                onOpenFilter = onOpenFilter,
                onDownloadedClick = { navController.navigate("auto_playlist/downloaded") },
            )
        }

        // Playlists Shelf
        item(key = "playlists_shelf") {
            GlassPlaylistShelf(
                playlists = sortedPlaylists,
                onShowAll = { onOpenFilter(LibraryFilter.PLAYLISTS) },
                onCreatePlaylist = { showCreatePlaylistDialog = true },
                onPlaylistClick = { navController.navigate("playlist/${it.id}") },
                onPlaylistLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        PlaylistMenu(
                            playlist = it,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                isPlaying = isPlaying,
                mediaMetadata = mediaMetadata,
            )
        }

        // Recently Added Albums Shelf
        if (recentAlbums.isNotEmpty()) {
            item(key = "albums_shelf") {
                GlassGridShelf(
                    title = stringResource(R.string.albums),
                    onShowAll = { onOpenFilter(LibraryFilter.ALBUMS) },
                ) {
                    items(recentAlbums, key = { "album_${it.id}" }) { album ->
                        AlbumGridItem(
                            album = album,
                            coroutineScope = coroutineScope,
                            fillMaxWidth = false,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    navController.navigate("album/${album.id}")
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        AlbumMenu(
                                            originalAlbum = album,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                        )
                    }
                }
            }
        }

        // Recently Added Artists Shelf
        if (recentArtists.isNotEmpty()) {
            item(key = "artists_shelf") {
                GlassGridShelf(
                    title = stringResource(R.string.artists),
                    onShowAll = { onOpenFilter(LibraryFilter.ARTISTS) },
                ) {
                    items(recentArtists, key = { "artist_${it.id}" }) { artist ->
                        ArtistGridItem(
                            artist = artist,
                            fillMaxWidth = false,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    navController.navigate("artist/${artist.id}")
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        ArtistMenu(
                                            originalArtist = artist,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun GlassLibraryLinkList(
    onOpenFilter: (LibraryFilter) -> Unit,
    onDownloadedClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(GlassShapes.Group)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 8.dp),
    ) {
        val filters = listOf(
            LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists) to R.drawable.playlist_play,
            LibraryFilter.ARTISTS to stringResource(R.string.filter_artists) to R.drawable.mic,
            LibraryFilter.ALBUMS to stringResource(R.string.filter_albums) to R.drawable.album,
            LibraryFilter.SONGS to stringResource(R.string.filter_songs) to R.drawable.music_note,
            LibraryFilter.PODCASTS to stringResource(R.string.filter_podcasts) to R.drawable.radio,
        )

        filters.forEachIndexed { index, (filterPair, iconRes) ->
            val (filter, label) = filterPair
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .combinedClickable(
                        onClick = { onOpenFilter(filter) },
                    )
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }

            if (index < filters.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp)
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        // Downloaded section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .combinedClickable(onClick = onDownloadedClick)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.download),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.filter_downloaded),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun GlassPlaylistShelf(
    playlists: List<Playlist>,
    onShowAll: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPlaylistLongPress: (Playlist) -> Unit,
    isPlaying: Boolean,
    mediaMetadata: com.metrolist.music.models.MediaMetadata?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.filter_playlists),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (playlists.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.show_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.combinedClickable(onClick = onShowAll),
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "new_playlist") {
                GlassNewPlaylistTile(onCreatePlaylist)
            }

            items(playlists.take(10), key = { "playlist_${it.id}" }) { playlist ->
                PlaylistGridItem(
                    playlist = playlist,
                    fillMaxWidth = false,
                    modifier = Modifier.combinedClickable(
                        onClick = { onPlaylistClick(playlist) },
                        onLongClick = { onPlaylistLongPress(playlist) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun GlassNewPlaylistTile(onCreatePlaylist: () -> Unit) {
    // Apple Music's "New Playlist" tile: an artwork-sized square with a plus, titled like the cards beside it.
    Column(
        modifier = Modifier
            .padding(top = 12.dp) // grid cards inset their artwork by the same amount
            .width(GridThumbnailHeight)
            .clip(GlassShapes.Artwork)
            .clickable(onClick = onCreatePlaylist),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(GridThumbnailHeight)
                .clip(GlassShapes.Artwork)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.create_playlist),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GlassGridShelf(
    title: String,
    onShowAll: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.show_all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.combinedClickable(onClick = onShowAll),
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

