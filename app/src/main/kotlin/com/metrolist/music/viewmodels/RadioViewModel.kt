/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

internal suspend fun loadRadioArtist(
    artistId: String,
    fetch: (suspend (String) -> Result<ArtistPage>)? = null,
): ArtistItem? = try {
    val artist = (fetch?.invoke(artistId) ?: YouTube.artist(artistId)).getOrThrow().artist
    currentCoroutineContext().ensureActive()
    artist
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    currentCoroutineContext().ensureActive()
    reportException(error)
    null
}

internal suspend fun loadRadioArtistThumbnails(
    artists: List<Artist>,
    saveThumbnail: (String, String) -> Unit,
    onArtists: (List<Artist>) -> Unit,
    fetch: (suspend (String) -> Result<ArtistPage>)? = null,
) = coroutineScope {
    val ready = artists.filter {
        !it.thumbnailUrl.isNullOrBlank() || !it.artist.isYouTubeArtist || it.artist.isLocal
    }
        .associateByTo(mutableMapOf()) { it.id }
    onArtists(artists.mapNotNull { ready[it.id] })
    val mutex = Mutex()
    val limiter = Semaphore(4)
    artists.filterNot { it.id in ready }.map { artist ->
        async {
            val thumbnail = limiter.withPermit { loadRadioArtist(artist.id, fetch)?.thumbnail }
                ?.takeIf { it.isNotBlank() }
            val resolved = if (thumbnail == null) artist else {
                artist.copy(artist = artist.artist.copy(thumbnailUrl = thumbnail))
            }
            if (thumbnail != null) {
                try {
                    saveThumbnail(artist.id, thumbnail)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    reportException(error)
                }
            }
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                ready[artist.id] = resolved
                onArtists(artists.mapNotNull { ready[it.id] })
            }
        }
    }.awaitAll()
}

internal suspend fun loadHomeRadioStations(
    fetch: (suspend (String?) -> Result<HomePage>)? = null,
): List<PlaylistItem> {
    val seenContinuations = mutableSetOf<String>()
    var continuation: String? = null
    repeat(3) {
        val page = (fetch?.invoke(continuation) ?: YouTube.home(continuation)).getOrThrow()
        currentCoroutineContext().ensureActive()
        val stations = page.sections.flatMap { it.items }.filterIsInstance<PlaylistItem>()
            .filter { !it.isPodcast && it.id.removePrefix("VL").startsWith("RDTMAK") }
            .map { if (it.id.startsWith("VL")) it.copy(id = it.id.removePrefix("VL")) else it }
            .distinctBy { it.id }
        // ponytail: first station shelf within three Home pages, add dedicated pagination for a full Mixes page.
        if (stations.isNotEmpty()) return stations.take(12)
        continuation = page.continuation?.takeIf { it.isNotBlank() && seenContinuations.add(it) }
            ?: return emptyList()
    }
    return emptyList()
}

@HiltViewModel
class RadioViewModel internal constructor(
    val database: MusicDatabase,
    private val fetchArtist: (suspend (String) -> Result<ArtistPage>)?,
    private val fetchHome: (suspend (String?) -> Result<HomePage>)?,
) : ViewModel() {
    @Inject constructor(database: MusicDatabase) : this(database, null, null)

    private val _stations = MutableStateFlow<List<Song>?>(null)
    val stations: StateFlow<List<Song>?> = _stations

    private val _artistStations = MutableStateFlow<List<Artist>?>(null)
    val artistStations: StateFlow<List<Artist>?> = _artistStations

    private val _lastPlayed = MutableStateFlow<Song?>(null)
    val lastPlayed: StateFlow<Song?> = _lastPlayed

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _personalizedStations = MutableStateFlow<List<PlaylistItem>?>(emptyList())
    val personalizedStations: StateFlow<List<PlaylistItem>?> = _personalizedStations

    private val _personalizedError = MutableStateFlow(false)
    val personalizedError: StateFlow<Boolean> = _personalizedError

    private var requestedSession: Triple<String, String?, String>? = null
    private var personalizedJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val toTimeStamp = LocalDateTime.now()
            val fromTimeStamp = toTimeStamp.minusDays(30)
            try {
                coroutineScope {
                    launch {
                        _lastPlayed.value = database.latestEvent().first()?.song
                    }
                    launch {
                        _stations.value = database.mostPlayedSongs(
                            fromTimeStamp = fromTimeStamp,
                            limit = 12,
                            offset = 0,
                            toTimeStamp = toTimeStamp,
                        ).first()
                    }
                    launch {
                        val artists = database.mostPlayedArtists(
                            fromTimeStamp = fromTimeStamp,
                            limit = 10,
                            toTimeStamp = toTimeStamp,
                        ).first()
                        loadRadioArtistThumbnails(
                            artists = artists,
                            saveThumbnail = database::updateArtistThumbnail,
                            onArtists = { _artistStations.value = it },
                            fetch = fetchArtist,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportException(error)
                _stations.value = _stations.value.orEmpty()
                _artistStations.value = _artistStations.value.orEmpty()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadPersonalizedStations(cookie: String?, dataSyncId: String?, authUser: String, force: Boolean = false) {
        val session = cookie?.takeIf { parseCookieString(it)["SAPISID"]?.isNotBlank() == true }
            ?.let { Triple(it, dataSyncId, authUser) }
        if (!force && requestedSession == session) return
        requestedSession = session
        personalizedJob?.cancel()
        _personalizedError.value = false
        _personalizedStations.value = if (session == null) emptyList() else null
        if (session == null) return
        personalizedJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val stations = loadHomeRadioStations(fetchHome)
                withContext(Dispatchers.Main) {
                    if (requestedSession == session) _personalizedStations.value = stations
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                reportException(error)
                withContext(Dispatchers.Main) {
                    if (requestedSession == session) {
                        _personalizedError.value = true
                        _personalizedStations.value = emptyList()
                    }
                }
            }
        }
    }
}
