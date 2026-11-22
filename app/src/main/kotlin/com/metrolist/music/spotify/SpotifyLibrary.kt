/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Library queries ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */

package com.metrolist.music.spotify

import com.metrolist.music.canvas.SpotifyCanvasClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val owner: String?,
    val imageUrl: String?,
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Int,
    val imageUrl: String?,
)

/** The signed-in user's Spotify library, read through the web player's GraphQL as the sp_dc session. */
object SpotifyLibrary {
    /** Pseudo-playlist id for Liked Songs, which is a collection rather than a playlist uri. */
    const val LIKED_ID = "liked"
    private const val LIKED_COVER = "https://misc.scdn.co/liked-songs/liked-songs-640.png"

    private const val GQL = "https://api-partner.spotify.com/pathfinder/v2/query"

    // shortcut: persisted-query hashes are pinned to Spotify's web player build; refresh them from it if queries start failing.
    private const val LIBRARY_HASH = "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3"
    private const val PLAYLIST_HASH = "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763"
    private const val LIKED_HASH = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Liked Songs first, then every playlist in the library. */
    suspend fun playlists(spDc: String?): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val token = SpotifyCanvasClient.Default.accessToken(spDc)
        val collected = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        while (true) {
            val variables = JSONObject()
                .put("filters", JSONArray().put("Playlists"))
                .put("order", JSONObject.NULL)
                .put("textFilter", "")
                .put("features", JSONArray().put("LIKED_SONGS").put("YOUR_EPISODES_V2").put("PRERELEASES").put("EVENTS"))
                .put("limit", 50)
                .put("offset", offset)
                .put("flatten", true)
                .put("expandedFolders", JSONArray())
                .put("folderUri", JSONObject.NULL)
                .put("includeFoldersWhenFlattening", false)
            val page = parsePlaylistPage(gql("libraryV3", LIBRARY_HASH, variables, token))
            collected += page.items
            offset += page.raw
            if (page.raw == 0 || offset >= page.total) break
        }
        listOf(SpotifyPlaylist(LIKED_ID, "Liked Songs", owner = null, imageUrl = LIKED_COVER)) + collected
    }

    /** Every track of [playlistId]; [onPage] sees the running list so long playlists fill in as they load. */
    suspend fun tracks(
        spDc: String?,
        playlistId: String,
        onPage: (List<SpotifyTrack>) -> Unit = {},
    ): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val token = SpotifyCanvasClient.Default.accessToken(spDc)
        val collected = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (true) {
            val page = if (playlistId == LIKED_ID) {
                val variables = JSONObject().put("offset", offset).put("limit", 100)
                parseLikedPage(gql("fetchLibraryTracks", LIKED_HASH, variables, token))
            } else {
                val variables = JSONObject()
                    .put("uri", "spotify:playlist:$playlistId")
                    .put("offset", offset)
                    .put("limit", 100)
                    .put("enableWatchFeedEntrypoint", false)
                parseTrackPage(gql("fetchPlaylist", PLAYLIST_HASH, variables, token))
            }
            collected += page.items
            offset += page.raw
            onPage(collected.toList())
            if (page.raw == 0 || offset >= page.total) break
        }
        collected
    }

    private fun gql(operation: String, hash: String, variables: JSONObject, token: String): JSONObject {
        val body = JSONObject()
            .put("variables", variables)
            .put("operationName", operation)
            .put("extensions", JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", hash)))
        val request = Request.Builder()
            .url(GQL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("app-platform", "WebPlayer")
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .header("User-Agent", USER_AGENT)
            .build()
        val text = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Spotify $operation failed (${response.code})")
            response.body.string()
        }
        val root = JSONObject(text)
        root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() }?.let {
            throw IOException(it)
        }
        return root
    }
}

/** One page: the entries kept, the collection's total, and how many entries the page held before filtering. */
internal class SpotifyPage<T>(val items: List<T>, val total: Int, val raw: Int)

internal fun parsePlaylistPage(root: JSONObject): SpotifyPage<SpotifyPlaylist> {
    val library = root.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("libraryV3")
        ?: throw IOException("Spotify library response was empty")
    val raw = library.optJSONArray("items") ?: JSONArray()
    val items = raw.objects().mapNotNull { element ->
        val wrapper = element.optJSONObject("item") ?: return@mapNotNull null
        if (!wrapper.optString("__typename").contains("Playlist", ignoreCase = true)) return@mapNotNull null
        val data = wrapper.optJSONObject("data") ?: return@mapNotNull null
        if (data.optString("__typename") != "Playlist") return@mapNotNull null
        val uri = wrapper.optString("_uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val name = data.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SpotifyPlaylist(
            id = uri.substringAfterLast(":"),
            name = name,
            owner = data.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name")?.takeIf { it.isNotBlank() },
            imageUrl = largestSource(data.optJSONObject("images")),
        )
    }
    return SpotifyPage(items, library.optInt("totalCount", raw.length()), raw.length())
}

internal fun parseTrackPage(root: JSONObject): SpotifyPage<SpotifyTrack> {
    val content = root.optJSONObject("data")?.optJSONObject("playlistV2")?.optJSONObject("content")
        ?: throw IOException("Spotify playlist response was empty")
    val raw = content.optJSONArray("items") ?: JSONArray()
    val items = raw.objects().mapNotNull { element ->
        val data = element.optJSONObject("itemV2")?.optJSONObject("data") ?: return@mapNotNull null
        val uri = data.optString("uri").ifBlank { data.optString("_uri") }
        trackOf(uri, data, data.optJSONObject("duration"))
    }
    return SpotifyPage(items, content.optInt("totalCount", raw.length()), raw.length())
}

internal fun parseLikedPage(root: JSONObject): SpotifyPage<SpotifyTrack> {
    val tracks = root.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("library")?.optJSONObject("tracks")
        ?: throw IOException("Spotify liked songs response was empty")
    val raw = tracks.optJSONArray("items") ?: JSONArray()
    val items = raw.objects().mapNotNull { element ->
        val wrapper = element.optJSONObject("track") ?: return@mapNotNull null
        val data = wrapper.optJSONObject("data") ?: return@mapNotNull null
        val uri = wrapper.optString("_uri").ifBlank { data.optString("uri") }
        trackOf(uri, data, data.optJSONObject("trackDuration") ?: data.optJSONObject("duration"))
    }
    return SpotifyPage(items, tracks.optInt("totalCount", raw.length()), raw.length())
}

private fun trackOf(uri: String, data: JSONObject, duration: JSONObject?): SpotifyTrack? {
    val title = data.optString("name")
    if (title.isBlank() || uri.isBlank()) return null
    val album = data.optJSONObject("albumOfTrack")
    return SpotifyTrack(
        id = uri.substringAfterLast(":"),
        title = title,
        artist = (data.optJSONObject("artists")?.optJSONArray("items") ?: JSONArray()).objects()
            .mapNotNull { it.optJSONObject("profile")?.optString("name")?.takeIf(String::isNotBlank) }
            .joinToString(", "),
        album = album?.optString("name")?.takeIf { it.isNotBlank() },
        durationMs = duration?.optInt("totalMilliseconds") ?: 0,
        imageUrl = album?.optJSONObject("coverArt")?.optJSONArray("sources")?.objects()
            ?.mapNotNull { it.optString("url").takeIf(String::isNotBlank) }?.lastOrNull(),
    )
}

/** Spotify lists image sizes in no fixed order, so pick the widest source of the first image. */
private fun largestSource(images: JSONObject?): String? =
    images?.optJSONArray("items")?.optJSONObject(0)?.optJSONArray("sources")?.objects()
        ?.filter { it.optString("url").isNotBlank() }
        ?.maxByOrNull { it.optInt("width") }
        ?.optString("url")

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
