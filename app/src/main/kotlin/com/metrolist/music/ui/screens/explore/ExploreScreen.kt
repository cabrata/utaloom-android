/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */

package com.metrolist.music.ui.screens.explore

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.viewmodels.ChartsViewModel
import com.metrolist.music.viewmodels.MoodAndGenresViewModel
import com.metrolist.music.viewmodels.NewReleaseViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    navController: NavController,
    newReleaseViewModel: NewReleaseViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
    moodAndGenresViewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val localConfiguration = LocalConfiguration.current
    val moodColumnsCount = if (localConfiguration.orientation == ORIENTATION_LANDSCAPE) 3 else 2

    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val newReleaseAlbums by newReleaseViewModel.newReleaseAlbums.collectAsStateWithLifecycle()
    val chartsPage by chartsViewModel.chartsPage.collectAsStateWithLifecycle()
    val chartsLoading by chartsViewModel.isLoading.collectAsStateWithLifecycle()
    val moodAndGenresList by moodAndGenresViewModel.moodAndGenres.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            chartsViewModel.loadCharts()
        }
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        // New Releases section
        if (newReleaseAlbums.isNotEmpty()) {
            item(key = "new_releases_header") {
                ExploreSection(
                    title = stringResource(R.string.new_release_albums),
                    onShowAllClick = {
                        navController.navigate("new_release")
                    },
                    modifier = Modifier,
                )
            }

            item(key = "new_releases_content") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    items(
                        items = newReleaseAlbums.take(10).distinctBy { it.id },
                        key = { "explore_newrelease_${it.id}" },
                    ) { album ->
                        YouTubeGridItem(
                            item = album,
                            isActive = mediaMetadata?.album?.id == album.id,
                            isPlaying = isPlaying,
                            coroutineScope = coroutineScope,
                            badges = {},
                            modifier = Modifier
                                .width(GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp)
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("album/${album.id}")
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubeAlbumMenu(
                                                albumItem = album,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ),
                        )
                    }
                }
            }
        } else if (newReleaseAlbums.isEmpty()) {
            item(key = "new_releases_shimmer") {
                ExploreSection(
                    title = stringResource(R.string.new_release_albums),
                    onShowAllClick = { navController.navigate("new_release") },
                    modifier = Modifier,
                )
                ShimmerHost(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    ) {
                        items(5) {
                            Box(
                                modifier = Modifier.width(
                                    GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp
                                ),
                            ) {
                                GridItemPlaceHolder(fillMaxWidth = true)
                            }
                        }
                    }
                }
            }
        }

        // Charts section: Apple Music's ranked rows (artwork + rank) and, when the region only serves
        // chart playlists, a row of their covers.
        val chartSections = chartsPage?.sections?.filterNot { it.title == "Top music videos" }.orEmpty()
        val topSongs = chartSections
            .firstOrNull { section -> section.items.any { it is SongItem } }
            ?.items
            ?.filterIsInstance<SongItem>()
            ?.distinctBy { it.id }
            ?.take(8)
            .orEmpty()
        val chartCards = chartSections
            .flatMap { it.items }
            .filter { it is PlaylistItem || it is AlbumItem || it is ArtistItem }
            .distinctBy { it.id }
            .take(12)

        if (topSongs.isNotEmpty() || chartCards.isNotEmpty() || chartsLoading) {
            item(key = "charts_header") {
                ExploreSection(
                    title = stringResource(R.string.charts),
                    onShowAllClick = {
                        navController.navigate("charts_screen")
                    },
                    modifier = Modifier,
                )
            }

            if (chartsLoading || chartsPage == null) {
                item(key = "charts_shimmer") {
                    ShimmerHost(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        Column {
                            repeat(4) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }
            } else {
                if (chartCards.isNotEmpty()) {
                    item(key = "charts_cards") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(chartCards, key = { "explore_chart_${it.id}" }) { card ->
                                YouTubeGridItem(
                                    item = card,
                                    isActive = false,
                                    isPlaying = isPlaying,
                                    coroutineScope = coroutineScope,
                                    badges = {},
                                    modifier = Modifier
                                        .width(GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp)
                                        .clickable {
                                            when (card) {
                                                is PlaylistItem -> navController.navigate("online_playlist/${card.id}")
                                                is AlbumItem -> navController.navigate("album/${card.id}")
                                                is ArtistItem -> navController.navigate("artist/${card.id}")
                                                else -> Unit
                                            }
                                        },
                                )
                            }
                        }
                    }
                }
                items(topSongs.size, key = { index -> "charts_song_${topSongs[index].id}" }) { index ->
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp),
                  ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    YouTubeListItem(
                        item = topSongs[index],
                        isActive = topSongs[index].id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        isSwipeable = false,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                            .combinedClickable(
                                onClick = {
                                    if (topSongs[index].id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                endpoint = WatchEndpoint(videoId = topSongs[index].id),
                                                preloadItem = topSongs[index].toMediaMetadata(),
                                            ),
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = topSongs[index],
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

        // Mood and Genres section
        if (moodAndGenresList == null) {
            item(key = "mood_genres_shimmer") {
                ExploreSection(
                    title = stringResource(R.string.mood_and_genres),
                    onShowAllClick = { navController.navigate("mood_and_genres") },
                    modifier = Modifier,
                )
                ShimmerHost(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    Column {
                        repeat(4) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                            ) {
                                repeat(moodColumnsCount) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.72f)
                                            .background(
                                                MaterialTheme.colorScheme.onSurface,
                                                RoundedCornerShape(18.dp),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        moodAndGenresList?.forEachIndexed { sectionIndex, section ->
            item(key = "mood_genres_section_title_$sectionIndex") {
                if (sectionIndex == 0) {
                    ExploreSection(
                        title = stringResource(R.string.mood_and_genres),
                        onShowAllClick = { navController.navigate("mood_and_genres") },
                        modifier = Modifier,
                    )
                }
            }

            val rows = section.items.distinctBy { it.endpoint.browseId to it.endpoint.params }
                .chunked(moodColumnsCount)

            items(rows.size, key = { rowIndex ->
                "mood_genres_row_${sectionIndex}_$rowIndex"
            }) { rowIndex ->
                MoodGenreRow(
                    row = rows[rowIndex],
                    columns = moodColumnsCount,
                    viewModel = moodAndGenresViewModel,
                    navController = navController,
                )
            }
        }
    }
}

@Composable
private fun MoodGenreRow(
    row: List<MoodAndGenres.Item>,
    columns: Int,
    viewModel: MoodAndGenresViewModel,
    navController: NavController,
) {
    val artworkFlow = remember(viewModel, row) {
        viewModel.artwork.map { artwork ->
            row.map { artwork[it.endpoint.browseId to it.endpoint.params] }
        }.distinctUntilChanged()
    }
    val artwork by artworkFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
    ) {
        row.forEachIndexed { index, item ->
            LaunchedEffect(item.endpoint) { viewModel.loadArtwork(item) }
            MoodGenreSleeve(
                item = item,
                artwork = artwork.getOrNull(index),
                onClick = {
                    navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
                },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun ExploreSection(
    title: String,
    onShowAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowAllClick) {
            Text(
                text = stringResource(R.string.show_all),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun MoodGenreSleeve(
    item: com.metrolist.innertube.pages.MoodAndGenres.Item,
    artwork: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = androidx.compose.runtime.remember(item.stripeColor, item.title) { moodTone(item) }

    Box(
        modifier = modifier
            .aspectRatio(1.72f)
            .clip(RoundedCornerShape(18.dp))
            .background(tone.stripe)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(1f - 0.28f)
                .background(tone.placeholder),
        ) {
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = tone.duotone,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.24f),
                        0.62f to Color.Transparent,
                    )
                ),
        )

        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(
                    Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, 1f),
                    blurRadius = 6f,
                ),
            ),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

private data class MoodTone(
    val stripe: Color,
    val placeholder: Color,
    val duotone: ColorFilter,
)

private fun moodTone(item: com.metrolist.innertube.pages.MoodAndGenres.Item): MoodTone {
    val base = Color(item.stripeColor.toInt())
    val stripe = lerp(base, Color.Black, 0.08f)
    val shadow = lerp(base, Color.Black, 0.68f)
    val highlight = lerp(base, Color.White, 0.32f)
    return MoodTone(
        stripe = stripe,
        placeholder = lerp(shadow, highlight, 0.45f),
        duotone = duotone(shadow, highlight),
    )
}

private fun duotone(shadow: Color, highlight: Color): ColorFilter {
    fun channel(from: Float, to: Float): FloatArray {
        val span = to - from
        return floatArrayOf(span * 0.299f, span * 0.587f, span * 0.114f, 0f, from * 255f)
    }
    return ColorFilter.colorMatrix(
        ColorMatrix(
            channel(shadow.red, highlight.red) +
                channel(shadow.green, highlight.green) +
                channel(shadow.blue, highlight.blue) +
                floatArrayOf(0f, 0f, 0f, 1f, 0f),
        ),
    )
}
