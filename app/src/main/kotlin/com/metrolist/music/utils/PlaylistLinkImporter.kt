/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Imports public Spotify playlists/albums and YouTube (Music) playlists as YouTube Music songs. */
object PlaylistLinkImporter {
    /** [missing] = Spotify tracks with no YouTube Music match, [truncated] = Spotify only gave the first 100. */
    class Result(val name: String, val songs: List<SongItem>, val missing: List<String>, val truncated: Boolean)

    private const val UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private const val MAX_SONGS = 20_000
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    private fun call(req: Request.Builder): Response = http.newCall(req.header("User-Agent", UA).build()).execute()

    private fun get(url: String): String =
        call(Request.Builder().url(url)).use { r ->
            require(r.isSuccessful) { "HTTP ${r.code}" }
            r.body.string()
        }

    fun ytPlaylistId(url: String): String? =
        Regex("[?&]list=([A-Za-z0-9_-]{2,64})").find(url)?.groupValues?.get(1)?.takeIf {
            Regex("^https?://((www|m|music)\\.)?youtube\\.com/").containsMatchIn(url)
        }

    fun spotifyRef(url: String): Pair<String, String>? =
        Regex("^https?://open\\.spotify\\.com/(?:intl-[a-z-]+/)?(playlist|album)/([A-Za-z0-9]{22})").find(url)
            ?.destructured?.let { (type, id) -> type to id }

    suspend fun import(input: String, onProgress: (done: Int, total: Int, title: String) -> Unit): Result =
        withContext(Dispatchers.IO) {
            val url = input.trim()
            require(url.length <= 2048) { "Link is too long" }
            ytPlaylistId(url)?.let { return@withContext importYouTube(it) }
            val resolved =
                if (Regex("^https://spotify\\.link/[A-Za-z0-9]+$").matches(url)) {
                    call(Request.Builder().url(url)).use { it.request.url.toString() }
                } else url
            val (type, id) = spotifyRef(resolved)
                ?: throw IllegalArgumentException("Paste a Spotify playlist/album link or a YouTube Music playlist link")
            importSpotify(type, id, onProgress)
        }

    private suspend fun importYouTube(id: String): Result {
        val page = YouTube.playlist(id).getOrThrow()
        val songs = page.songs.toMutableList()
        var cont = page.songsContinuation ?: page.continuation
        while (cont != null && songs.size < MAX_SONGS) {
            val next = YouTube.playlistContinuation(cont).getOrThrow()
            if (next.songs.isEmpty()) break
            songs += next.songs
            cont = next.continuation
        }
        return Result(page.playlist.title.ifBlank { "YouTube playlist" }, songs.distinctBy { it.id }, emptyList(), false)
    }

    private data class SpTrack(val title: String, val artist: String, val durationSec: Int)

    private class SpEmbed(val name: String, val tracks: List<SpTrack>, val token: String?)

    // Public embed page: no API key, but at most 100 tracks. allTracks() pages the rest.
    private fun embed(type: String, id: String): SpEmbed {
        val html = get("https://open.spotify.com/embed/$type/$id")
        val raw = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.+?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: error("Could not read Spotify page")
        val state = Json.parseToJsonElement(raw).jsonObject["props"]?.jsonObject?.get("pageProps")?.jsonObject
            ?.get("state")?.jsonObject
        val entity = state?.get("data")?.jsonObject?.get("entity")?.jsonObject
            ?: throw IllegalArgumentException("Spotify $type not found or private")
        val tracks = entity["trackList"]?.jsonArray.orEmpty().mapNotNull { el ->
            val t = el.jsonObject
            val title = t["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            SpTrack(
                title,
                t["subtitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                ((t["duration"]?.jsonPrimitive?.longOrNull ?: 0) / 1000).toInt(),
            )
        }
        val token = state["settings"]?.jsonObject?.get("session")?.jsonObject?.get("accessToken")?.jsonPrimitive?.contentOrNull
        return SpEmbed(entity["name"]?.jsonPrimitive?.contentOrNull ?: "Spotify $type", tracks, token)
    }

    // Unofficial web-player GraphQL with the embed's anonymous token. The official Web API (OAuth) only returns
    // items of playlists the user owns since Feb 2026 and caps dev apps at 5 users, so it can't serve public playlists.
    // The persisted-query hash rotates; it is re-read from the web player bundle when Spotify answers 412.
    @Volatile
    private var playlistHash = "8964e8eafb21aa992a7d951d256d83285c04be2105d209262901de70cb97584a"

    private fun refreshPlaylistHash() {
        val home = get("https://open.spotify.com/")
        Regex("https://open\\.spotifycdn\\.com/cdn/build/web-player/[^\"]+\\.js").findAll(home).map { it.value }.toSet().forEach { js ->
            Regex("\"fetchPlaylist\",\"query\",\"([a-f0-9]{64})\"").find(get(js))?.let {
                playlistHash = it.groupValues[1]
                return
            }
        }
        error("Spotify playlist query not found")
    }

    private fun page(id: String, token: String, offset: Int, retry: Boolean = true): Pair<Int, List<SpTrack>> {
        val body = """{"operationName":"fetchPlaylist","variables":{"uri":"spotify:playlist:$id","offset":$offset,"limit":100,"enableWatchFeedEntrypoint":false},"extensions":{"persistedQuery":{"version":1,"sha256Hash":"$playlistHash"}}}"""
        val text = call(
            Request.Builder().url("https://api-partner.spotify.com/pathfinder/v2/query")
                .header("Authorization", "Bearer $token")
                .post(body.toRequestBody("application/json".toMediaType())),
        ).use { r ->
            if (r.code == 412 && retry) null else {
                check(r.isSuccessful) { "Spotify HTTP ${r.code}" }
                r.body.string()
            }
        } ?: run {
            refreshPlaylistHash()
            return page(id, token, offset, false)
        }
        val content = Json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("playlistV2")?.jsonObject
            ?.get("content")?.jsonObject ?: error("Spotify playlist unavailable")
        val tracks = content["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            val d = el.jsonObject["itemV2"]?.jsonObject?.get("data")?.jsonObject ?: return@mapNotNull null
            val title = d["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val artists = d["artists"]?.jsonObject?.get("items")?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["profile"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull }
            val ms = d["trackDuration"]?.jsonObject?.get("totalMilliseconds")?.jsonPrimitive?.longOrNull ?: 0
            SpTrack(title, artists.joinToString(", "), (ms / 1000).toInt())
        }
        return (content["totalCount"]?.jsonPrimitive?.intOrNull ?: 0) to tracks
    }

    /** All tracks of a public playlist, or null if the GraphQL route fails (caller keeps the embed's first 100). */
    private fun allTracks(id: String, token: String): List<SpTrack>? =
        runCatching {
            val out = mutableListOf<SpTrack>()
            var total: Int
            do {
                val (count, items) = page(id, token, out.size)
                total = minOf(count, MAX_SONGS)
                out += items
            } while (items.isNotEmpty() && out.size < total)
            out
        }.onFailure { reportException(it) }.getOrNull()

    /** Among the top results: prefer a matching artist, then the closest duration. Rejects hits more than 30s off. */
    fun bestMatch(hits: List<SongItem>, durationSec: Int, artist: String = ""): SongItem? {
        val wanted = artist.lowercase().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        fun diff(h: SongItem) = if (durationSec <= 0 || h.duration == null) 0 else abs(h.duration!! - durationSec)
        fun artistMiss(h: SongItem) =
            if (wanted.isEmpty() || h.artists.any { a -> a.name.lowercase().trim() in wanted }) 0 else 1
        return hits.take(5).filter { diff(it) <= 30 }.minWithOrNull(compareBy({ artistMiss(it) }, { diff(it) }))
    }

    private suspend fun importSpotify(type: String, id: String, onProgress: (Int, Int, String) -> Unit): Result =
        coroutineScope {
            val embed = embed(type, id)
            val full = if (type == "playlist" && embed.tracks.size >= 100 && embed.token != null) allTracks(id, embed.token) else null
            val tracks = full ?: embed.tracks
            val gate = Semaphore(8)
            val done = AtomicInteger()
            val matched = tracks.map { t ->
                async {
                    gate.withPermit {
                        val title = t.title.replace(Regex("\\s+-\\s+.*(remaster|version|edit|mix|live).*$", RegexOption.IGNORE_CASE), "")
                        val hits = YouTube.search("$title ${t.artist}", YouTube.SearchFilter.FILTER_SONG).getOrNull()
                            ?.items?.filterIsInstance<SongItem>().orEmpty()
                        bestMatch(hits, t.durationSec, t.artist).also { onProgress(done.incrementAndGet(), tracks.size, t.title) }
                    }
                }
            }.awaitAll()
            Result(
                name = embed.name.trim().ifBlank { "Spotify $type" },
                songs = matched.filterNotNull().distinctBy { it.id },
                missing = tracks.zip(matched).filter { it.second == null }.map { "${it.first.title} - ${it.first.artist}" },
                truncated = type == "playlist" && embed.tracks.size >= 100 && full == null,
            )
        }
}
