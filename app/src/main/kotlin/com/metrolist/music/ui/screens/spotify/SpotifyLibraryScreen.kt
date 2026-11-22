/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.spotify

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.ui.theme.isUtaloomGlass
import com.metrolist.music.ui.glass.GlassDetailHeader
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ListThumbnailSize
import com.metrolist.music.constants.SpotifySpDcCookieKey
import com.metrolist.music.playback.queues.SpotifyQueue
import com.metrolist.music.spotify.SpotifyLibrary
import com.metrolist.music.spotify.SpotifyPlaylist
import com.metrolist.music.spotify.SpotifyTrack
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.ListItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Loaded list, or the reason it couldn't be; null while loading. */
sealed interface SpotifyState<out T> {
    data class Ready<T>(val items: List<T>) : SpotifyState<T>
    data class Failed(val message: String?) : SpotifyState<Nothing>
}

@HiltViewModel
class SpotifyLibraryViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow<SpotifyState<SpotifyPlaylist>?>(null)
    val state: StateFlow<SpotifyState<SpotifyPlaylist>?> = _state
    private var loaded: String? = null
    private var job: Job? = null

    fun load(spDc: String, force: Boolean = false) {
        if (!force && loaded == spDc) return
        loaded = spDc
        job?.cancel()
        _state.value = null
        job = viewModelScope.launch {
            _state.value = try {
                SpotifyState.Ready(SpotifyLibrary.playlists(spDc))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                SpotifyState.Failed(e.message)
            }
        }
    }
}

@HiltViewModel
class SpotifyPlaylistViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    val playlistId: String = checkNotNull(savedStateHandle["id"])
    val name: String = savedStateHandle.get<String>("name").orEmpty()
    val owner: String = savedStateHandle.get<String>("owner").orEmpty()
    val cover: String? = savedStateHandle.get<String>("cover")?.takeIf { it.isNotBlank() }
    private val _state = MutableStateFlow<SpotifyState<SpotifyTrack>?>(null)
    val state: StateFlow<SpotifyState<SpotifyTrack>?> = _state
    private var job: Job? = null

    fun load(spDc: String) {
        if (job != null && _state.value !is SpotifyState.Failed) return
        _state.value = null
        job = viewModelScope.launch {
            try {
                SpotifyLibrary.tracks(spDc, playlistId) { _state.value = SpotifyState.Ready(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                // Keep the pages already shown; only an empty load becomes an error.
                if (_state.value !is SpotifyState.Ready) _state.value = SpotifyState.Failed(e.message)
            }
        }
    }
}

fun spotifyPlaylistRoute(playlist: SpotifyPlaylist) =
    "spotify_playlist/${playlist.id}?name=${Uri.encode(playlist.name)}" +
        "&owner=${Uri.encode(playlist.owner.orEmpty())}&cover=${Uri.encode(playlist.imageUrl.orEmpty())}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLibraryScreen(
    navController: NavController,
    viewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val spDc by rememberPreference(SpotifySpDcCookieKey, "")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(spDc) { if (spDc.isNotBlank()) viewModel.load(spDc) }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            spDc.isBlank() -> connectPrompt { navController.navigate("settings/spotify_login") }
            state == null -> loading()
            state is SpotifyState.Failed -> failure((state as SpotifyState.Failed).message) { viewModel.load(spDc, force = true) }
            else -> items((state as SpotifyState.Ready).items, key = { it.id }) { playlist ->
                ListItem(
                    title = playlist.name,
                    subtitle = playlist.owner,
                    thumbnailContent = { Cover(playlist.imageUrl) },
                    modifier = Modifier.clickable { navController.navigate(spotifyPlaylistRoute(playlist)) },
                )
            }
        }
    }

    SpotifyTopBar(stringResource(R.string.spotify_library), navController)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistScreen(
    navController: NavController,
    viewModel: SpotifyPlaylistViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val spDc by rememberPreference(SpotifySpDcCookieKey, "")
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(spDc) { if (spDc.isNotBlank()) viewModel.load(spDc) }
    val title = viewModel.name.ifBlank { stringResource(R.string.liked_songs) }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val current = state) {
            null -> loading()
            is SpotifyState.Failed -> failure(current.message) { viewModel.load(spDc) }
            is SpotifyState.Ready -> {
                val tracks = current.items
                item(key = "header") {
                    SpotifyPlaylistHeader(
                        title = title,
                        owner = viewModel.owner,
                        cover = viewModel.cover,
                        trackCount = tracks.size,
                        onPlay = { if (tracks.isNotEmpty()) playerConnection.playQueue(SpotifyQueue(title, tracks, 0)) },
                        onShuffle = { if (tracks.isNotEmpty()) playerConnection.playQueue(SpotifyQueue(title, tracks.shuffled(), 0)) },
                    )
                }
                itemsIndexed(tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                    ListItem(
                        title = track.title,
                        subtitle = listOfNotNull(
                            track.artist.takeIf { it.isNotBlank() },
                            track.durationMs.takeIf { it > 0 }?.let { makeTimeString(it.toLong()) },
                        ).joinToString(" • "),
                        thumbnailContent = { Cover(track.imageUrl) },
                        modifier = Modifier.clickable { playerConnection.playQueue(SpotifyQueue(title, tracks, index)) },
                    )
                }
            }
        }
    }

    SpotifyTopBar(title, navController)
}

/**
 * A playlist page's header, as for YouTube Music playlists, minus the add/like action: a Spotify
 * playlist is only read here, never saved into the library.
 */
@Composable
private fun SpotifyPlaylistHeader(
    title: String,
    owner: String,
    cover: String?,
    trackCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val meta = pluralStringResource(R.plurals.n_song, trackCount, trackCount)
    if (isUtaloomGlass) {
        GlassDetailHeader(
            title = title,
            artistText = owner,
            metadataText = meta,
            thumbnailUrl = cover,
            onPlay = onPlay,
            onShuffle = onShuffle,
        )
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(200.dp).clip(MaterialTheme.shapes.medium),
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            listOf(owner, meta).filter { it.isNotBlank() }.joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.play), modifier = Modifier.padding(start = 8.dp))
            }
            FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.shuffle), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.shuffle), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyTopBar(title: String, navController: NavController) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

@Composable
private fun Cover(url: String?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(ListThumbnailSize).clip(MaterialTheme.shapes.small),
    )
}

private fun LazyListScope.loading() = item(key = "loading") {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

private fun LazyListScope.failure(message: String?, onRetry: () -> Unit) = item(key = "failure") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        Text(
            text = message ?: stringResource(R.string.error_unknown),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
    }
}

private fun LazyListScope.connectPrompt(onConnect: () -> Unit) = item(key = "connect") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        Text(
            text = stringResource(R.string.spotify_library_connect_description),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onConnect, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.spotify_login_title))
        }
    }
}
