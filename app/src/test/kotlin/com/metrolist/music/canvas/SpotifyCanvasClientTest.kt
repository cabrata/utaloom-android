package com.metrolist.music.canvas

import android.app.Application
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class SpotifyCanvasClientTest {
    private val trackId = "2qSkIjg1o9h3YT9RAgYN75"
    private val uri = "spotify:track:$trackId"
    private val video = "https://canvaz.scdn.co/upload/video/example.cnvs.mp4"

    private fun proto(write: CodedOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { buffer ->
        CodedOutputStream.newInstance(buffer).apply {
            write()
            flush()
        }
    }.toByteArray()

    private fun response(url: String = video, trackUri: String = uri): ByteArray = proto {
        writeByteArray(1, proto {
            writeString(2, url)
            writeString(5, trackUri)
            writeInt32(4, 2)
        })
        writeInt32(2, 3600)
    }

    private fun track(name: String = "Espresso", artist: String = "Sabrina Carpenter", id: String = trackId): JSONObject = JSONObject()
        .put("item", JSONObject().put("data", JSONObject()
            .put("name", name).put("id", id)
            .put("artists", JSONObject().put("items", JSONArray().put(JSONObject().put("profile", JSONObject().put("name", artist)))))))

    @Test
    fun `production protobuf encoder and decoder respect track identity and URL boundary`() {
        val request = CodedInputStream.newInstance(canvasRequest(trackId))
        assertEquals(10, request.readTag())
        val entity = CodedInputStream.newInstance(request.readByteArray())
        assertEquals(10, entity.readTag())
        assertEquals(uri, entity.readString())
        assertEquals(video, decodeCanvasUrl(response(), uri))
        assertEquals("$video?token=valid", decodeCanvasUrl(response("$video?token=valid"), uri))
        assertNull(decodeCanvasUrl(response(trackUri = "spotify:track:wrong"), uri))
        assertNull(decodeCanvasUrl(proto { writeInt32(2, 3600) }, uri))
        for (url in listOf("http://canvaz.scdn.co/video.mp4", "https://canvaz.scdn.co.attacker.example/video.mp4", "https://canvaz.scdn.co/image.jpg")) {
            assertNull(decodeCanvasUrl(response(url), uri))
        }
    }

    @Test
    fun `matching rejects covers and wrong artists but normalizes accents and featuring suffixes`() {
        val candidates = JSONArray()
            .put(track("Sesi Potret", "karaoke SESH"))
            .put(track("Sesi Potret - Cover", "eńau"))
            .put(track("Sesi Potret", "eńau"))
        assertEquals(trackId, matchCanvasTrack(candidates, "Sesi Potret (feat. Ari Lesmana)", "enau"))
        assertNull(matchCanvasTrack(candidates, "Sesi Potret", "Sabrina Carpenter"))
        assertNull(matchCanvasTrack(JSONArray(), "Sesi Potret", "eńau"))
    }

    @Test
    fun `cookie parsing validates raw quoted and header values and rejects injection`() {
        val value = "fixture-session-value-1234567890"
        assertEquals(value, normalizeSpDc(value))
        assertEquals(value, normalizeSpDc(" \"$value\" "))
        assertEquals(value, normalizeSpDc("sp_t=public; sp_dc=$value; other=x"))
        assertEquals(value, extractSpDc("sp_t=x; sp_dc=$value; other=x"))
        for (input in listOf(null, "", "sp_dc=", "short", "$value\r\nInjected: invalid", "https://invalid.example/")) {
            assertNull(normalizeSpDc(input))
        }
        assertNull(extractSpDc("other=value"))
        assertEquals("287082", spotifyTotp("12345678901234567890".toByteArray(), 59))
    }

    @Test
    fun `real client request chain retries temporary failure without caching missing Canvas and isolates sessions`() = runBlocking {
        var clock = 1_700_000_000_000L
        var authCount = 0
        var searchCount = 0
        var canvasCount = 0
        val requests = mutableListOf<String>()
        val client = SpotifyCanvasClient(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            requests += path
            var status = 200
            var body: ByteArray
            when {
                path.endsWith("secretDict.json") -> {
                    assertNull(request.header("Cookie"))
                    assertNull(request.header("Authorization"))
                    body = "{\"1\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]}".toByteArray()
                }
                path == "/api/server-time" -> body = "{\"serverTime\":1700000000}".toByteArray()
                path == "/api/token" -> {
                    authCount++
                    assertTrue(request.header("Cookie").orEmpty().startsWith("sp_dc=fixture-session"))
                    assertEquals("1", request.url.queryParameter("totpVer"))
                    body = "{\"accessToken\":\"fixture-token-$authCount\",\"isAnonymous\":false,\"accessTokenExpirationTimestampMs\":${clock + 3600000}}".toByteArray()
                }
                path == "/pathfinder/v2/query" -> {
                    searchCount++
                    assertNull(request.header("Cookie"))
                    assertNotNull(request.header("Authorization"))
                    assertEquals("POST", request.method)
                    if (searchCount == 1) {
                        status = 429
                        body = ByteArray(0)
                    } else {
                        body = JSONObject().put("data", JSONObject().put("searchV2", JSONObject().put("tracksV2", JSONObject().put("items", JSONArray().put(track()))))).toString().toByteArray()
                    }
                }
                path == "/canvaz-cache/v0/canvases" -> {
                    canvasCount++
                    assertNull(request.header("Cookie"))
                    body = response()
                }
                else -> error("Unexpected host/path, refusing network")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .header("Retry-After", "2").body(body.toResponseBody()).build()
        }.build()) { clock }
        val session = "fixture-session-value-1234567890"
        assertNull(client.fetchCanvas("Espresso", "Sabrina Carpenter", null))
        assertTrue(requests.isEmpty())
        assertNull(client.fetchCanvas("Espresso", "Sabrina Carpenter", session))
        assertNull(client.fetchCanvas("Espresso", "Sabrina Carpenter", session))
        assertEquals(1, searchCount)
        clock += 2001
        assertEquals(video, client.fetchCanvas("Espresso", "Sabrina Carpenter", session))
        assertEquals(video, client.fetchCanvas("Espresso", "Sabrina Carpenter", session))
        assertEquals(1, authCount)
        assertEquals(2, searchCount)
        assertEquals(1, canvasCount)
        assertEquals(video, client.fetchCanvas("Espresso", "Sabrina Carpenter", "fixture-session-different-123456"))
        assertEquals(2, authCount)
        assertEquals(3, searchCount)
        assertEquals(2, canvasCount)
        assertNull(client.fetchCanvas("Unknown song", "Sabrina Carpenter", "fixture-session-different-123456"))
        assertNull(client.fetchCanvas("Unknown song", "Sabrina Carpenter", "fixture-session-different-123456"))
        assertEquals(4, searchCount)
        assertEquals(2, canvasCount)
        clock += 5 * 60_000L + 1
        assertNull(client.fetchCanvas("Unknown song", "Sabrina Carpenter", "fixture-session-different-123456"))
        assertEquals(5, searchCount)
        assertNull(client.fetchCanvas("Espresso", "Sabrina Carpenter", ""))
    }

    @Test
    fun `opt in real Spotify lookup returns a readable Canvas MP4`() = runBlocking {
        val session = System.getenv("UTALOOM_SPOTIFY_CANVAS_SESSION").orEmpty()
        assumeTrue("Explicit session required for live Spotify validation", session.isNotBlank())
        val statuses = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request()).also {
                statuses += "${chain.request().url.host}${chain.request().url.encodedPath}: ${it.code}"
            }
        }.build()
        val client = SpotifyCanvasClient(http)
        val sesiPotret = client.fetchCanvas("Sesi Potret (feat. Ari Lesmana)", "eńau", session)
        println("Live Sesi Potret Canvas available: ${sesiPotret != null}")
        assertNotNull("Reported Sesi Potret track has a Canvas in the live Spotify response", sesiPotret)
        val espresso = client.fetchCanvas("Espresso", "Sabrina Carpenter", session)
        println("Live request statuses: ${statuses.joinToString()}")
        assertNotNull("Real known-Canvas lookup must succeed, not just token generation", espresso)
        for (url in listOf(sesiPotret!!, espresso!!)) {
            http.newCall(okhttp3.Request.Builder().url(url).header("Range", "bytes=0-1023").build()).execute().use {
                assertTrue(it.code == 200 || it.code == 206)
                val bytes = it.body.source().readByteArray(8)
                assertEquals("ftyp", String(bytes.copyOfRange(4, 8), Charsets.US_ASCII))
                println("Live Canvas MP4 read: HTTP ${it.code}, ftyp verified")
            }
        }
    }
}
