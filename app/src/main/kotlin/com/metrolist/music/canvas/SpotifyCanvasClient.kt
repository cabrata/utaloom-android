/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.canvas

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

class SpotifyCanvasClient internal constructor(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        val Default = SpotifyCanvasClient()
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        // ponytail: Spotify's persisted query is versioned, update from the web player if it changes.
        private const val SEARCH_HASH = "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751"
    }

    // ponytail: one on-screen lookup at a time, use per-session locks if callers ever run in parallel.
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, Pair<String?, Long>>()
    private var session: String? = null
    private var token: String? = null
    private var tokenExpiresAt = 0L
    private var retryAt = 0L

    suspend fun fetchCanvas(title: String, artist: String, spDc: String?): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cookie = normalizeSpDc(spDc)
            if (session != cookie) {
                session = cookie
                token = null
                tokenExpiresAt = 0L
                retryAt = 0L
                cache.clear()
            }
            if (cookie == null || title.isBlank() || artist.isBlank()) return@withLock null
            val cacheKey = "$title\n$artist"
            cache[cacheKey]?.takeIf { it.second > now() }?.let { return@withLock it.first }
            if (now() < retryAt) return@withLock null

            try {
                val accessToken = getAccessToken(cookie)
                val trackId = searchTrackId(title, artist, accessToken)
                val canvas = trackId?.let { fetchCanvasByTrackId(it, accessToken) }
                coroutineContext.ensureActive()
                cache[cacheKey] = canvas to (now() + if (canvas == null) 5 * 60_000L else 30 * 60_000L)
                if (cache.size > 100) cache.remove(cache.keys.first())
                canvas
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Do not cache a network/auth failure as "this song has no Canvas".
                Timber.w("Spotify Canvas lookup failed (%s)", e.javaClass.simpleName)
                null
            } catch (e: org.json.JSONException) {
                Timber.w("Spotify Canvas returned an unsupported response")
                null
            }
        }
    }

    private suspend fun execute(request: Request): ByteArray {
        coroutineContext.ensureActive()
        val bytes = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    token = null
                    tokenExpiresAt = 0L
                }
                if (response.code == 429) {
                    val seconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 3600L) ?: 60L
                    retryAt = now() + seconds * 1000L
                }
                throw IOException("Spotify HTTP ${response.code}")
            }
            response.body.bytes()
        }
        coroutineContext.ensureActive()
        return bytes
    }

    private fun spotifyRequest(url: String, accessToken: String? = null): Request.Builder = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Origin", "https://open.spotify.com")
        .header("Referer", "https://open.spotify.com/")
        .apply { accessToken?.let { header("Authorization", "Bearer $it") } }

    private suspend fun getAccessToken(cookie: String): String {
        token?.takeIf { now() < tokenExpiresAt - 60_000L }?.let { return it }
        // This public rotation data request must never carry account cookies or bearer tokens.
        val secrets = JSONObject(String(execute(Request.Builder()
            .url("https://raw.githubusercontent.com/xyloflake/spot-secrets-go/refs/heads/main/secrets/secretDict.json")
            .build()), Charsets.UTF_8))
        val version = secrets.keys().asSequence().mapNotNull(String::toIntOrNull).maxOrNull()
            ?: throw IOException("Missing Spotify authentication version")
        val cipher = secrets.getJSONArray(version.toString())
        if (cipher.length() !in 16..128) throw IOException("Invalid Spotify authentication data")
        val key = (0 until cipher.length()).joinToString("") { index ->
            val value = cipher.getInt(index)
            if (value !in 0..255) throw IOException("Invalid Spotify authentication data")
            (value xor ((index % 33) + 9)).toString()
        }.toByteArray(Charsets.US_ASCII)
        val serverTime = JSONObject(String(execute(spotifyRequest("https://open.spotify.com/api/server-time").build()), Charsets.UTF_8))
            .getLong("serverTime")
        if (serverTime <= 0) throw IOException("Invalid Spotify server time")
        val url = "https://open.spotify.com/api/token".toHttpUrl().newBuilder()
            .addQueryParameter("reason", "transport")
            .addQueryParameter("productType", "web-player")
            .addQueryParameter("totp", spotifyTotp(key, serverTime))
            .addQueryParameter("totpVer", version.toString())
            // Spotify's totpServer parameter uses the legacy, already-divided server timestamp.
            .addQueryParameter("totpServer", spotifyTotp(key, serverTime / 30))
            .build()
        val json = JSONObject(String(execute(spotifyRequest(url.toString())
            .header("Cookie", "sp_dc=$cookie").build()), Charsets.UTF_8))
        val accessToken = json.optString("accessToken")
        if (accessToken.isBlank() || json.optBoolean("isAnonymous", true)) {
            throw IOException("Spotify session is not authenticated")
        }
        token = accessToken
        tokenExpiresAt = json.optLong("accessTokenExpirationTimestampMs", now() + 30 * 60_000L)
        return accessToken
    }

    private suspend fun searchTrackId(title: String, artist: String, accessToken: String): String? {
        val payload = JSONObject()
            .put("operationName", "searchTracks")
            .put("variables", JSONObject().put("searchTerm", "${cleanCanvasTitle(title)} $artist").put("offset", 0).put("limit", 10))
            .put("extensions", JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", SEARCH_HASH)))
        val json = JSONObject(String(execute(spotifyRequest("https://api-partner.spotify.com/pathfinder/v2/query", accessToken)
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()), Charsets.UTF_8))
        if (json.optJSONArray("errors")?.length()?.let { it > 0 } == true) throw IOException("Spotify search failed")
        val tracks = json.optJSONObject("data")?.optJSONObject("searchV2")?.optJSONObject("tracksV2")?.optJSONArray("items")
            ?: throw IOException("Missing Spotify track results")
        return matchCanvasTrack(tracks, title, artist)
    }

    private suspend fun fetchCanvasByTrackId(trackId: String, accessToken: String): String? {
        val bytes = execute(spotifyRequest("https://spclient.wg.spotify.com/canvaz-cache/v0/canvases", accessToken)
            .post(canvasRequest(trackId).toRequestBody("application/x-protobuf".toMediaType())).build())
        return decodeCanvasUrl(bytes, "spotify:track:$trackId")
    }
}

internal fun spotifyTotp(key: ByteArray, seconds: Long): String {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key, "HmacSHA1"))
    val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(seconds / 30).array())
    val offset = hash.last().toInt() and 15
    val binary = ByteBuffer.wrap(hash, offset, 4).int and Int.MAX_VALUE
    return (binary % 1_000_000).toString().padStart(6, '0')
}

internal fun cleanCanvasTitle(title: String): String = title
    .replace(Regex("""(?i)\([^)]*(?:official|video|audio|lyric|feat\.|ft\.)[^)]*\)|\[[^]]*(?:official|video|audio|lyric|feat\.|ft\.)[^]]*]"""), "")
    .trim()

private fun normalizeCanvasText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

internal fun matchCanvasTrack(tracks: JSONArray, title: String, artist: String): String? {
    val normalizedTitle = normalizeCanvasText(cleanCanvasTitle(title))
    val normalizedArtist = normalizeCanvasText(artist)
    for (index in 0 until tracks.length()) {
        val track = tracks.optJSONObject(index)?.optJSONObject("item")?.optJSONObject("data") ?: continue
        if (normalizeCanvasText(cleanCanvasTitle(track.optString("name"))) != normalizedTitle) continue
        val artists = track.optJSONObject("artists")?.optJSONArray("items") ?: continue
        val artistMatches = (0 until artists.length()).any {
            normalizeCanvasText(artists.optJSONObject(it)?.optJSONObject("profile")?.optString("name").orEmpty()) == normalizedArtist
        }
        val id = track.optString("id")
        if (artistMatches && Regex("[A-Za-z0-9]{22}").matches(id)) return id
    }
    return null
}

internal fun canvasRequest(trackId: String): ByteArray {
    require(Regex("[A-Za-z0-9]{22}").matches(trackId))
    val entityBytes = ByteArrayOutputStream().also { buffer ->
        CodedOutputStream.newInstance(buffer).apply {
            writeString(1, "spotify:track:$trackId")
            flush()
        }
    }.toByteArray()
    return ByteArrayOutputStream().also { buffer ->
        CodedOutputStream.newInstance(buffer).apply {
            writeByteArray(1, entityBytes)
            flush()
        }
    }.toByteArray()
}

internal fun decodeCanvasUrl(bytes: ByteArray, trackUri: String): String? {
    val input = CodedInputStream.newInstance(bytes)
    while (!input.isAtEnd) {
        val tag = input.readTag()
        if (tag != 10) {
            if (!input.skipField(tag)) break
            continue
        }
        val canvas = CodedInputStream.newInstance(input.readByteArray())
        var url: String? = null
        var uri: String? = null
        while (!canvas.isAtEnd) {
            when (val field = canvas.readTag()) {
                18 -> url = canvas.readStringRequireUtf8()
                42 -> uri = canvas.readStringRequireUtf8()
                else -> if (!canvas.skipField(field)) break
            }
        }
        val parsed = url?.toHttpUrlOrNullCanvas()
        if (uri == trackUri && parsed != null) return parsed
    }
    return null
}

private fun String.toHttpUrlOrNullCanvas(): String? = toHttpUrlOrNull()
    ?.takeIf { it.scheme == "https" && it.host == "canvaz.scdn.co" && it.encodedPath.endsWith(".mp4") }
    ?.toString()

internal fun normalizeSpDc(input: String?): String? {
    val trimmed = input?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: return null
    val cookie = if (trimmed.contains("sp_dc=")) extractSpDc(trimmed) else trimmed
    return cookie?.takeIf { it.length in 16..4096 && it.all { char -> char.isLetterOrDigit() && char.code < 128 || char == '-' || char == '_' || char == '=' } }
}

fun extractSpDc(cookieString: String?): String? = cookieString?.split(';')
    ?.asSequence()?.map(String::trim)?.firstOrNull { it.startsWith("sp_dc=") }
    ?.substringAfter("sp_dc=")?.takeIf(String::isNotBlank)
