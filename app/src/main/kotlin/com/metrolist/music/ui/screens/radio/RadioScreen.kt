/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Radio screen implementation with stations and artist radios.
 */

package com.metrolist.music.ui.screens.radio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.OfflineModeKey
import com.metrolist.music.constants.UseLoginForBrowse
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.theme.GlassShapes
import com.metrolist.music.utils.dataStore
import com.metrolist.music.viewmodels.RadioViewModel
import com.metrolist.music.viewmodels.loadRadioArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal suspend fun playRadioArtist(
    artist: Artist,
    onPlay: (WatchEndpoint) -> Unit,
    onFallback: () -> Unit,
    fetch: (suspend (String) -> Result<ArtistPage>)? = null,
) {
    val endpoint = if (artist.artist.isYouTubeArtist && !artist.artist.isLocal) {
        withContext(Dispatchers.IO) { loadRadioArtist(artist.id, fetch)?.radioEndpoint }
    } else null
    withContext(Dispatchers.Main) {
        if (endpoint == null) onFallback() else onPlay(endpoint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioScreen(
    navController: NavController,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val context = LocalContext.current

    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val artistStations by viewModel.artistStations.collectAsStateWithLifecycle()
    val lastPlayed by viewModel.lastPlayed.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val personalizedStations by viewModel.personalizedStations.collectAsStateWithLifecycle()
    val personalizedError by viewModel.personalizedError.collectAsStateWithLifecycle()
    val radioPreferences by remember(context) {
        context.dataStore.data.map { preferences ->
            Triple(
                preferences[InnerTubeCookieKey].orEmpty(),
                preferences[DataSyncIdKey],
                preferences[InnerTubeAuthUserKey] ?: "0",
            ) to (!(preferences[OfflineModeKey] ?: false) && (preferences[UseLoginForBrowse] ?: true))
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(Triple("", null, "0") to false)
    val (session, onlineRadio) = radioPreferences
    val signedIn = remember(session.first) { parseCookieString(session.first)["SAPISID"]?.isNotBlank() == true }
    val personalizedVisible = signedIn && onlineRadio && !personalizedStations.isNullOrEmpty()
    LaunchedEffect(viewModel, radioPreferences) {
        viewModel.loadPersonalizedStations(
            cookie = session.first.takeIf { signedIn && onlineRadio },
            dataSyncId = session.second,
            authUser = session.third,
        )
    }

    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        if (!signedIn) {
            item(key = "radio_login") {
                TextButton(
                    onClick = { navController.navigate("login") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.yt_login_title))
                }
            }
        }

        if (signedIn && onlineRadio) {
            if (personalizedStations == null) {
                item(key = "radio_personalized_loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (personalizedError) {
                item(key = "radio_personalized_retry") {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.error_unknown))
                        TextButton(onClick = {
                            viewModel.loadPersonalizedStations(session.first, session.second, session.third, force = true)
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            } else if (!personalizedStations.isNullOrEmpty()) {
                item(key = "radio_personalized_title") {
                    NavigationTitle(
                        title = stringResource(R.string.radio_stations_for_you),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                item(key = "radio_personalized_shelf") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        items(personalizedStations.orEmpty(), key = { it.id }) { station ->
                            YouTubeGridItem(
                                item = station,
                                modifier = Modifier.width(150.dp).clickable {
                                    navController.navigate("online_playlist/${station.id}")
                                },
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && stations.isNullOrEmpty() && artistStations.isNullOrEmpty() && lastPlayed == null) {
            item(key = "radio_loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        if (!isLoading && stations.isNullOrEmpty() && artistStations.isNullOrEmpty() && lastPlayed == null &&
            (!signedIn || !onlineRadio || (personalizedStations?.isEmpty() == true && !personalizedError))
        ) {
            item(key = "radio_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.radio),
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.radio_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // Hero card: Continue Listening
        lastPlayed?.let { song ->
            item(key = "radio_hero_${song.id}") {
                HeroCard(
                    song = song,
                    isEnabled = !isListenTogetherGuest,
                    onPlay = {
                        playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                )
            }
        }

        // Stations for you shelf
        stations?.takeIf { it.isNotEmpty() && !personalizedVisible }?.let { stationsList ->
            item(key = "radio_stations_title") {
                NavigationTitle(
                    title = stringResource(R.string.radio_stations_for_you),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item(key = "radio_stations_shelf") {
                StationsShelf(
                    stations = stationsList.take(12),
                    isEnabled = !isListenTogetherGuest,
                    onStationPlay = { song ->
                        playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                    },
                )
            }
        }

        // Artist stations row
        artistStations?.takeIf { it.isNotEmpty() }?.let { artists ->
            item(key = "radio_artist_stations_title") {
                NavigationTitle(
                    title = stringResource(R.string.radio_artist_stations),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item(key = "radio_artist_stations") {
                ArtistStationsRow(
                    artists = artists.take(10),
                    isEnabled = !isListenTogetherGuest,
                    onArtistPlay = { artistId ->
                        coroutineScope.launch {
                            playRadioArtist(
                                artist = artists.first { it.id == artistId },
                                onPlay = { playerConnection.playQueue(YouTubeQueue(it)) },
                                onFallback = { navController.navigate("artist/$artistId") },
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        item(key = "radio_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroCard(
    song: Song,
    isEnabled: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(GlassShapes.ArtworkLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = isEnabled, onClick = onPlay),
        contentAlignment = Alignment.BottomStart,
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.4f),
                        1f to Color.Black.copy(alpha = 0.6f),
                    )
                )
                .align(Alignment.BottomCenter),
        )

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = stringResource(R.string.radio_continue_listening),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.radio_station_name,
                    song.title
                ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StationsShelf(
    stations: List<Song>,
    isEnabled: Boolean,
    onStationPlay: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier,
    ) {
        items(
            items = stations,
            key = { "station_${it.id}" },
        ) { song ->
            StationCard(
                song = song,
                isEnabled = isEnabled,
                onPlay = { onStationPlay(song) },
                modifier = Modifier.width(150.dp),
            )
        }
    }
}

@Composable
private fun StationCard(
    song: Song,
    isEnabled: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(GlassShapes.Artwork)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = isEnabled, onClick = onPlay),
        contentAlignment = Alignment.BottomStart,
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Black.copy(alpha = 0.5f),
                    )
                )
                .align(Alignment.BottomCenter),
        )

        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.artists.isNotEmpty()) {
                Text(
                    text = song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistStationsRow(
    artists: List<com.metrolist.music.db.entities.Artist>,
    isEnabled: Boolean,
    onArtistPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier,
    ) {
        items(
            items = artists,
            key = { "artist_${it.id}" },
        ) { artist ->
            ArtistCircle(
                artist = artist,
                isEnabled = isEnabled,
                onPlay = { onArtistPlay(artist.id) },
            )
        }
    }
}

@Composable
private fun ArtistCircle(
    artist: com.metrolist.music.db.entities.Artist,
    isEnabled: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(100.dp).clickable(enabled = isEnabled, onClick = onPlay),
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (artist.thumbnailUrl.isNullOrBlank()) {
                Icon(
                    painter = painterResource(R.drawable.artist),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = artist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
