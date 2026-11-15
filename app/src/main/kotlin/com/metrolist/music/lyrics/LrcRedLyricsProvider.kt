package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.betterlyrics.TTMLParser
import com.metrolist.music.constants.EnableLrcRedKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object LrcRedLyricsProvider : LyricsProvider {
    override val name = "lrc.red"

    private val client by lazy { createClient(OkHttp.create()) }
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val isrcPattern = Regex("[A-Za-z0-9]{12}")

    @Serializable
    private data class SearchResponse(val hits: List<Hit>)

    @Serializable
    private data class Hit(
        val isrc: String,
        val title: String,
        val artist: String,
        val album: String? = null,
        val duration: Double? = null,
    )

    internal fun createClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        defaultRequest {
            url("https://lrc.red")
            header(HttpHeaders.UserAgent, "Utaloom (https://github.com/cabrata/utaloom-android; admin@caliphdev.com)")
        }
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcRedKey] ?: true

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = getLyrics(title, artist, duration, album, client)

    internal suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        http: HttpClient,
    ): Result<String> {
        return try {
            val normalizedTitle = normalize(title)
            val normalizedArtist = normalize(artist)
            require(normalizedTitle.isNotEmpty() && normalizedArtist.isNotEmpty()) { "Missing title or artist" }
            val normalizedAlbum = album?.let(::normalize)?.takeIf { it.isNotEmpty() }
            fun durationDifference(hit: Hit): Double =
                if (duration > 0 && hit.duration != null) abs(hit.duration - duration) else Double.POSITIVE_INFINITY

            val response = http.get("/search.json") { parameter("q", "$title $artist") }.body<SearchResponse>()
            val candidates = response.hits.filter {
                isrcPattern.matches(it.isrc) && normalize(it.title) == normalizedTitle &&
                    normalize(it.artist) == normalizedArtist &&
                    (it.duration == null || (it.duration.isFinite() && it.duration > 0 &&
                        (duration <= 0 || durationDifference(it) <= 10)))
            }.sortedWith(compareBy<Hit>(
                { normalizedAlbum == null || it.album?.let(::normalize) != normalizedAlbum },
                { durationDifference(it) },
            )).distinctBy { it.isrc }
            check(candidates.isNotEmpty()) { "No matching lrc.red track" }

            var lastFailure: Exception? = null
            // ponytail: first search page, three candidates max. Add pagination only if real lookups need it.
            for (hit in candidates.take(3)) {
                try {
                    val ttml = http.get("/s/${hit.isrc}.ttml").bodyAsText()
                    val lines = TTMLParser.parseTTML(ttml)
                    check(lines.isNotEmpty()) { "No lrc.red lyrics" }
                    val lyrics = TTMLParser.toLRC(lines)
                    check(lyrics.isNotBlank()) { "No lrc.red lyrics" }
                    return Result.success(lyrics)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastFailure = e
                }
            }
            Result.failure(IllegalStateException("No lrc.red lyrics", lastFailure))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(marks, "").lowercase(Locale.ROOT).replace(separators, " ").trim()
}
