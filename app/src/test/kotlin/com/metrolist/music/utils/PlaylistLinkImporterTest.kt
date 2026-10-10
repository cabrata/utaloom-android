/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class PlaylistLinkImporterTest {
    @Test
    fun `parses supported links`() {
        assertEquals("PLabc_-123", PlaylistLinkImporter.ytPlaylistId("https://music.youtube.com/playlist?list=PLabc_-123"))
        assertEquals("RDabc", PlaylistLinkImporter.ytPlaylistId("https://www.youtube.com/watch?v=x&list=RDabc"))
        assertNull(PlaylistLinkImporter.ytPlaylistId("https://evil.com/?list=PLabc"))
        assertEquals("playlist" to "37i9dQZF1DXcBWIGoYBM5M", PlaylistLinkImporter.spotifyRef("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=x"))
        assertEquals("album" to "37i9dQZF1DXcBWIGoYBM5M", PlaylistLinkImporter.spotifyRef("https://open.spotify.com/intl-id/album/37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(PlaylistLinkImporter.spotifyRef("https://open.spotify.com/track/37i9dQZF1DXcBWIGoYBM5M"))
    }

    @Test
    fun `prefers matching artist then closest duration`() {
        val a = SongItem(id = "a", title = "x", artists = emptyList(), duration = 300, thumbnail = "")
        val b = SongItem(id = "b", title = "x", artists = emptyList(), duration = 200, thumbnail = "")
        val c = b.copy(id = "c", artists = listOf(Artist("Olivia Rodrigo", null)), duration = 210)
        assertEquals("b", PlaylistLinkImporter.bestMatch(listOf(a, b), 199)?.id)
        assertNull(PlaylistLinkImporter.bestMatch(listOf(a), 100))
        assertEquals("c", PlaylistLinkImporter.bestMatch(listOf(b, c), 200, "Olivia Rodrigo")?.id)
    }

    @Test
    fun `live spotify import beyond 100 tracks`() = runBlocking {
        assumeTrue(System.getenv("IMPORT_PROBE") != null)
        val r = PlaylistLinkImporter.import("https://open.spotify.com/playlist/2YRe7HRKNRvXdJBp9nXFza") { _, _, _ -> }
        println("LIVE ${r.songs.size} missing=${r.missing.size} truncated=${r.truncated}")
        assertEquals(false, r.truncated)
    }
}
