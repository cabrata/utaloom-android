/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.spotify.SpotifyTrack
import com.metrolist.music.utils.PlaylistLinkImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Plays Spotify tracks by finding each one on YouTube Music. Only the started track and a page
 * after it are searched up front; the rest are matched a page at a time as the queue runs down,
 * so a long playlist doesn't fire hundreds of searches before the first note.
 */
class SpotifyQueue(
    private val title: String,
    private val tracks: List<SpotifyTrack>,
    private val startIndex: Int,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private var next = startIndex

    override suspend fun getInitialStatus(): Queue.Status {
        val first = match(tracks.subList(startIndex, (startIndex + PAGE).coerceAtMost(tracks.size)))
        next = (startIndex + PAGE).coerceAtMost(tracks.size)
        if (first.isEmpty()) throw IllegalStateException("No YouTube Music match for $title")
        return Queue.Status(title = title, items = first, mediaItemIndex = 0)
    }

    override fun hasNextPage(): Boolean = next < tracks.size

    override suspend fun nextPage(): List<MediaItem> {
        val page = tracks.subList(next, (next + PAGE).coerceAtMost(tracks.size))
        next += page.size
        return match(page)
    }

    private suspend fun match(page: List<SpotifyTrack>): List<MediaItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            page.map { track ->
                async { PlaylistLinkImporter.match(track.title, track.artist, track.durationMs / 1000) }
            }.awaitAll()
        }.filterNotNull().map { it.toMediaItem() }
    }

    private companion object {
        const val PAGE = 6
    }
}
