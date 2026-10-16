/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.canvas

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object SpotifyCanvasClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = LruCache<String, String>(100)
    private val canvasUrlRegex = Regex("""https://canvaz\.scdn\.co/[^\x00-\x1f\x7f-\xff"]+\.mp4""")

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0L

    suspend fun fetchCanvas(
        title: String,
        artist: String,
        spDc: String?,
    ): String? = withContext(Dispatchers.IO) {
        val cacheKey = "$title - $artist"
        synchronized(cache) {
            val cached = cache.get(cacheKey)
            if (cached != null) {
                return@withContext cached.ifEmpty { null }
            }
        }

        try {
            val token = getAccessToken(spDc)
            if (token == null) {
                Timber.w("Failed to get Spotify access token")
                return@withContext null
            }

            val trackId = searchTrackId(title, artist, token)
            if (trackId == null) {
                Timber.d("Spotify track not found for $title by $artist")
                synchronized(cache) { cache.put(cacheKey, "") }
                return@withContext null
            }

            val canvasUrl = fetchCanvasByTrackId(trackId, token)
            synchronized(cache) {
                cache.put(cacheKey, canvasUrl ?: "")
            }
            canvasUrl
        } catch (e: Exception) {
            Timber.e(e, "Error fetching canvas for $title by $artist")
            null
        }
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\(.*?official.*?\)|\[.*?official.*?\]"""), "")
            .replace(Regex("""(?i)\(.*?video.*?\)|\[.*?video.*?\]"""), "")
            .replace(Regex("""(?i)\(.*?audio.*?\)|\[.*?audio.*?\]"""), "")
            .replace(Regex("""(?i)\(.*?lyric.*?\)|\[.*?lyric.*?\]"""), "")
            .replace(Regex("""(?i)\(.*?feat\..*?\)|\[.*?feat\..*?\]"""), "")
            .replace(Regex("""(?i)\(.*?ft\..*?\)|\[.*?ft\..*?\]"""), "")
            .trim()
    }

    private suspend fun getAccessToken(spDc: String?): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTime - 60_000) {
            return@withContext cachedToken
        }

        try {
            val totpVer = "61"
            val secretCipher = intArrayOf(
                44, 55, 44, 43, 103, 60, 49, 126, 59, 49, 80, 69, 89, 75, 81, 87, 62, 54, 123, 37, 117, 49, 52, 93, 76, 62
            )
            val processed = StringBuilder()
            secretCipher.forEachIndexed { i, byte ->
                processed.append(byte xor ((i % 33) + 9))
            }
            val keyBytes = processed.toString().toByteArray(Charsets.US_ASCII)

            val localTime = System.currentTimeMillis() / 1000
            val totpCode = generateTotp(keyBytes, localTime)
            val serverCode = generateTotp(keyBytes, localTime)

            val tokenUrl = "https://open.spotify.com/api/token?reason=transport&productType=web-player&totp=$totpCode&totpVer=$totpVer&totpServer=$serverCode"
            val requestBuilder = Request.Builder()
                .url(tokenUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Origin", "https://open.spotify.com/")
                .header("Referer", "https://open.spotify.com/")

            if (!spDc.isNullOrBlank()) {
                requestBuilder.header("Cookie", "sp_dc=${spDc.trim()}")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                Timber.w("Token fetch unsuccessful: ${response.code}")
                return@withContext null
            }

            val body = response.body.string()
            val json = JSONObject(body)
            val token = json.optString("accessToken")
            if (token.isNotEmpty()) {
                cachedToken = token
                tokenExpiryTime = now + 50 * 60 * 1000L
                return@withContext token
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Error obtaining Spotify access token")
            null
        }
    }

    private fun generateTotp(keyBytes: ByteArray, timestampSec: Long, period: Int = 30, digits: Int = 6): String {
        val counter = timestampSec / period
        val buffer = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hash = mac.doFinal(buffer)
        val offset = hash[19].toInt() and 0xf
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        val otp = (binary % 10.0.pow(digits.toDouble())).toInt()
        return otp.toString().padStart(digits, '0')
    }

    private suspend fun searchTrackId(title: String, artist: String, token: String): String? = withContext(Dispatchers.IO) {
        val cleaned = cleanTitle(title)
        val query = "$cleaned $artist".trim()
        val searchUrl = "https://api.spotify.com/v1/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&type=track&limit=1"

        val request = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "Mozilla/5.0")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("Spotify search failed: ${response.code}")
                return@withContext null
            }
            val body = response.body.string()
            val json = JSONObject(body)
            val items = json.optJSONObject("tracks")?.optJSONArray("items")
            if (items != null && items.length() > 0) {
                return@withContext items.getJSONObject(0).optString("id")
            }
        } catch (e: Exception) {
            Timber.w(e, "Error searching Spotify track")
        }
        null
    }

    private suspend fun fetchCanvasByTrackId(trackId: String, token: String): String? = withContext(Dispatchers.IO) {
        val trackUri = "spotify:track:$trackId"
        val trackUriBytes = trackUri.toByteArray(Charsets.UTF_8)

        val trackBytes = byteArrayOf(0x0a, trackUriBytes.size.toByte()) + trackUriBytes
        val reqBytes = byteArrayOf(0x0a, trackBytes.size.toByte()) + trackBytes

        val request = Request.Builder()
            .url("https://spclient.wg.spotify.com/canvaz-cache/v0/canvases")
            .post(reqBytes.toRequestBody("application/x-protobuf".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("Canvas fetch request failed: ${response.code}")
                return@withContext null
            }
            val responseBytes = response.body.bytes()
            val responseText = String(responseBytes, Charsets.ISO_8859_1)
            val match = canvasUrlRegex.find(responseText)
            return@withContext match?.value
        } catch (e: Exception) {
            Timber.e(e, "Error executing canvas request")
            null
        }
    }
}

fun extractSpDc(cookieString: String?): String? {
    if (cookieString.isNullOrBlank()) return null
    return cookieString.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("sp_dc=") }
        ?.substringAfter("sp_dc=")
        ?.takeIf { it.isNotBlank() }
}
