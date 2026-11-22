package com.metrolist.music.spotify

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, manifest = Config.NONE)
class SpotifyLibraryParserTest {
    @Test
    fun `library keeps playlists only and picks the widest cover`() {
        val page = parsePlaylistPage(JSONObject("""
            {"data":{"me":{"libraryV3":{"totalCount":3,"items":[
              {"item":{"__typename":"PlaylistResponseWrapper","_uri":"spotify:playlist:abc","data":{"__typename":"Playlist","name":"Mix",
                "ownerV2":{"data":{"name":"me"}},
                "images":{"items":[{"sources":[{"url":"big","width":640},{"url":"small","width":60}]}]}}}},
              {"item":{"__typename":"AlbumResponseWrapper","_uri":"spotify:album:x","data":{"__typename":"Album","name":"Album"}}},
              {"item":{"__typename":"PlaylistResponseWrapper","_uri":"spotify:playlist:gone","data":{"__typename":"NotFound"}}}
            ]}}}}
        """.trimIndent()))
        assertEquals(listOf(SpotifyPlaylist("abc", "Mix", "me", "big")), page.items)
        assertEquals(3, page.total)
        assertEquals(3, page.raw)
    }

    @Test
    fun `playlist tracks join artists and read duration`() {
        val page = parseTrackPage(JSONObject("""
            {"data":{"playlistV2":{"content":{"totalCount":1,"items":[{"itemV2":{"data":{
              "uri":"spotify:track:t1","name":"Song","duration":{"totalMilliseconds":200000},
              "artists":{"items":[{"profile":{"name":"A"}},{"profile":{"name":"B"}}]},
              "albumOfTrack":{"name":"Al","coverArt":{"sources":[{"url":"c1"},{"url":"c2"}]}}}}}]}}}}
        """.trimIndent()))
        assertEquals(listOf(SpotifyTrack("t1", "Song", "A, B", "Al", 200000, "c2")), page.items)
    }
}
