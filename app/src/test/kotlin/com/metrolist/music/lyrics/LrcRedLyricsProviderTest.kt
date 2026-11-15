package com.metrolist.music.lyrics

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcRedLyricsProviderTest {
    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
          <p begin="1.5s"><span begin="1.5s" end="2s">Hello </span><span begin="2s" end="3s">world</span></p>
        </div></body></tt>
    """.trimIndent()

    private fun hit(
        isrc: String,
        title: String = "Deja Vu",
        artist: String = "Beyonce",
        album: String? = "Album",
        duration: Double? = 200.0,
    ): String = """{"isrc":"$isrc","title":"$title","artist":"$artist","album":${album?.let { "\"$it\"" }},"duration":$duration}"""

    @Test
    fun `matching rejects unrelated tracks and versions then ranks album and duration before converting TTML`() = runBlocking {
        val fetched = mutableListOf<String>()
        val hits = listOf(
            hit("USAAA0000001", title = "Unrelated"),
            hit("USAAA0000002", artist = "Someone Else"),
            hit("USAAA0000003", duration = 230.0),
            hit("USAAA0000004", title = "Deja Vu Live"),
            hit("USAAA0000005", album = "Other", duration = 200.0),
            hit("USAAA0000006", duration = 205.0),
            hit("USAAA0000007", title = "Déjà-Vu", artist = "BEYONCÉ", duration = 201.0),
            hit("USAAA0000008", duration = null),
            hit("../invalid"),
        )
        LrcRedLyricsProvider.createClient(MockEngine { request ->
            assertEquals("https", request.url.protocol.name)
            assertEquals("lrc.red", request.url.host)
            assertEquals("Utaloom (https://github.com/cabrata/utaloom-android; admin@caliphdev.com)", request.headers[HttpHeaders.UserAgent])
            if (request.url.encodedPath == "/search.json") {
                assertEquals("Déjà Vu Beyoncé", request.url.parameters["q"])
                respond("""{"hits":[${hits.joinToString()}],"next":"ignored"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                fetched += request.url.encodedPath
                respond(ttml, headers = headersOf(HttpHeaders.ContentType, "application/ttml+xml"))
            }
        }).use { client ->
            val lyrics = LrcRedLyricsProvider.getLyrics("Déjà Vu", "Beyoncé", 200, " ALBUM ", client).getOrThrow()
            assertEquals(listOf("/s/USAAA0000007.ttml"), fetched)
            assertTrue(lyrics.contains("[00:01.50]Hello world"))
            assertTrue(lyrics.contains("<Hello:1.5:2.0|world:2.0:3.0>"))
        }
    }

    @Test
    fun `missing album prefers closest known duration and retries missing lyrics`() = runBlocking {
        val fetched = mutableListOf<String>()
        LrcRedLyricsProvider.createClient(MockEngine { request ->
            if (request.url.encodedPath == "/search.json") {
                val hits = listOf(hit("USAAA0000001", duration = null), hit("USAAA0000002", duration = 202.0), hit("USAAA0000003", duration = 201.0))
                respond("""{"hits":[${hits.joinToString()}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                fetched += request.url.encodedPath
                if (fetched.size == 1) respond("missing", HttpStatusCode.NotFound)
                else respond(ttml)
            }
        }).use { client ->
            assertTrue(LrcRedLyricsProvider.getLyrics("Deja Vu", "Beyonce", 200, null, client).isSuccess)
            assertEquals(listOf("/s/USAAA0000003.ttml", "/s/USAAA0000002.ttml"), fetched)
        }
    }

    @Test
    fun `no match and empty TTML fail with bounded requests`() = runBlocking {
        for (hits in listOf(emptyList(), listOf(hit("USAAA0000001", artist = "Wrong")), (1..5).map { hit("USAAA000000$it") })) {
            var lyricRequests = 0
            LrcRedLyricsProvider.createClient(MockEngine { request ->
                if (request.url.encodedPath == "/search.json") {
                    respond("""{"hits":[${hits.joinToString()}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    lyricRequests++
                    respond("<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div><p>Untimed lyric</p></div></body></tt>")
                }
            }).use { client ->
                assertTrue(LrcRedLyricsProvider.getLyrics("Deja Vu", "Beyonce", 200, null, client).isFailure)
                assertEquals(if (hits.size == 5) 3 else 0, lyricRequests)
            }
        }
    }

    @Test
    fun `HTTP errors fail and cancellation escapes search and lyric fetching`() = runBlocking {
        LrcRedLyricsProvider.createClient(MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) }).use { client ->
            assertTrue(LrcRedLyricsProvider.getLyrics("Deja Vu", "Beyonce", 200, null, client).isFailure)
        }
        LrcRedLyricsProvider.createClient(MockEngine { respond("missing", HttpStatusCode.NotFound) }).use { client ->
            assertTrue(LrcRedLyricsProvider.getLyrics("Deja Vu", "Beyonce", 200, null, client).exceptionOrNull() is ClientRequestException)
        }
        for (cancelSearch in listOf(true, false)) {
            LrcRedLyricsProvider.createClient(MockEngine { request ->
                if (!cancelSearch && request.url.encodedPath == "/search.json") {
                    respond("""{"hits":[${hit("USAAA0000001")}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    throw CancellationException("cancelled")
                }
            }).use { client ->
                var cancelled = false
                try {
                    LrcRedLyricsProvider.getLyrics("Deja Vu", "Beyonce", 200, null, client)
                } catch (e: CancellationException) {
                    cancelled = true
                }
                assertTrue("Cancellation must propagate", cancelled)
            }
        }
    }
}
