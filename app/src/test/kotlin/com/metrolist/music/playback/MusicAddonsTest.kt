package com.metrolist.music.playback

import androidx.media3.common.MimeTypes
import com.metrolist.music.playback.MusicAddons.Track
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAddonsTest {
    private fun pick(vararg tracks: Track, title: String = "Get Lucky (feat. Pharrell Williams)", duration: Int? = 369) =
        MusicAddons.pickMatch(tracks.toList(), title, listOf("Daft Punk", "Pharrell Williams"), duration)?.id

    @Test
    fun `picks the same recording and prefers the closest length`() {
        assertEquals(
            "b",
            pick(
                Track("a", "Get Lucky", "Daft Punk", 371.0),
                Track("b", "Get Lucky (feat. Pharrell Williams & Nile Rodgers)", "Daft Punk", 369.2),
            ),
        )
    }

    @Test
    fun `rejects other versions, artists and lengths`() {
        assertNull(pick(Track("a", "Get Lucky (Radio Edit)", "Daft Punk", 369.0)))
        assertNull(pick(Track("a", "Get Lucky - Live", "Daft Punk", 369.0)))
        assertNull(pick(Track("a", "Get Lucky", "Some Cover Band", 369.0)))
        assertNull(pick(Track("a", "Get Lucky", "Daft Punk", 248.0)))
        assertNull(pick(Track("a", "Lucky", "Daft Punk", 369.0)))
        assertNull(pick(Track("", "Get Lucky", "Daft Punk", 369.0)))
    }

    @Test
    fun `keeps a requested version and ignores accents`() {
        assertEquals("a", pick(Track("a", "Déjà Vu [Live]", "DAFT PUNK", 200.0), title = "Deja Vu (Live)", duration = 201))
    }

    @Test
    fun `needs a known length when the row has no artist`() {
        assertEquals("a", pick(Track("a", "Get Lucky", "", 369.0)))
        assertNull(pick(Track("a", "Get Lucky", "", null)))
        assertEquals("a", pick(Track("a", "Get Lucky", "Daft Punk", null)))
    }

    @Test
    fun `mid-track upgrade requires matching credit and known length within two seconds`() {
        fun pickUpgrade(vararg tracks: Track) = MusicAddons.pickMatch(
            tracks.toList(), "Get Lucky", listOf("Daft Punk"), 369, requireDuration = true,
        )?.id

        assertNull(pickUpgrade(Track("unknown duration", "Get Lucky", "Daft Punk")))
        assertNull(pickUpgrade(Track("unknown artist", "Get Lucky", "", 369.0)))
        assertNull(pickUpgrade(Track("different cut", "Get Lucky", "Daft Punk", 371.1)))
        assertEquals("same", pickUpgrade(Track("same", "Get Lucky", "Daft Punk", 370.9)))
    }

    @Test
    fun `upgrade requires confirmed lossless codec or substantial bitrate increase`() {
        val stream = MusicAddons.Stream("https://example.com/audio", "Provider", "1", "Provider", quality = "LOSSLESS")
        assertFalse(MusicAddons.worthUpgrading(stream, 160_000))
        assertTrue(MusicAddons.worthUpgrading(stream.copy(codec = "flac"), null))
        assertFalse(MusicAddons.worthUpgrading(stream.copy(codec = "aac", bitrateKbps = 320), null))
        assertFalse(MusicAddons.worthUpgrading(stream.copy(codec = "aac", bitrateKbps = 192), 128_000))
        assertTrue(MusicAddons.worthUpgrading(stream.copy(codec = "aac", bitrateKbps = 320), 160_000))
        assertTrue(MusicAddons.worthUpgrading(stream.copy(codec = "eac3", isDolbyAtmos = true), 256_000))
    }

    @Test
    fun `reads an atmos row from its own fields`() {
        assertTrue(Track("1", "x", "y", 200.0, audioQuality = "DOLBY_ATMOS").isDolbyAtmos)
        assertTrue(Track("1", "x", "y", 200.0, atmos = true).isDolbyAtmos)
        assertFalse(Track("1", "x", "y", 200.0, audioQuality = "HI_RES_LOSSLESS", format = "flac").isDolbyAtmos)
    }

    @Test
    fun `atmos hint is offered only when wanted and never overrides the add-on`() {
        val plain = MusicAddon("https://example.com", "A", params = mapOf("quality" to "LOSSLESS"))
        assertEquals("auto", MusicAddons.requestParams(plain, atmos = true)["atmos"])
        assertNull(MusicAddons.requestParams(plain, atmos = false)["atmos"])
        val declared = plain.copy(params = plain.params + ("atmos" to "1"))
        assertEquals("1", MusicAddons.requestParams(declared, atmos = true)["atmos"])
        assertEquals("0", MusicAddons.requestParams(declared, atmos = false)["atmos"])
    }

    @Test
    fun `builds the search query and add-on root`() {
        assertEquals("Get Lucky Daft Punk", MusicAddons.searchQuery("Get Lucky (feat. Pharrell Williams)", listOf("Daft Punk", "Pharrell Williams")))
        assertEquals("https://example.com/a", MusicAddons.normalizeUrl(" https://example.com/a/manifest.json "))
        assertEquals("https://example.com/a", MusicAddons.normalizeUrl("https://example.com/a/"))
        assertNull(MusicAddons.normalizeUrl("http://example.com/a"))
        assertNull(MusicAddons.normalizeUrl("file:///sdcard/a"))
    }

    @Test
    fun `stored list survives a round trip and bad data`() {
        val addons = listOf(
            MusicAddon(
                "https://example.com/a", "k-DL", enabled = false, params = mapOf("quality" to "lossless"),
                version = "1.0.0", qualities = listOf(MusicAddon.Quality("lossless", "Lossless")),
            ),
        )
        assertEquals("lossless", addons.single().quality)
        // Entries stored before version and quality options existed still load.
        assertEquals("k-DL", MusicAddons.decode("""[{"url":"https://example.com/a","name":"k-DL"}]""").single().name)
        assertEquals(addons, MusicAddons.decode(MusicAddons.encode(addons)))
        assertEquals(emptyList<MusicAddon>(), MusicAddons.decode("not json"))
        assertEquals(emptyList<MusicAddon>(), MusicAddons.decode(null))
    }


    private fun withServer(
        handler: (path: String, query: String?) -> Pair<Int, String>,
        block: suspend (baseUrl: String) -> Unit,
    ) = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val query = exchange.requestURI.rawQuery
            val (code, body) = handler(path, query)
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `resolves Tidal DASH Dolby Atmos row on extensionless URL and sends title and artist`() {
        val seenQueries = CopyOnWriteArrayList<String>()
        withServer({ path, query ->
            if (query != null) seenQueries += query
            when (path) {
                "/search" -> 200 to """
                    {"tracks":[
                      {"id":"stereo-1","title":"Song","artist":"Artist","duration":200.0,"audioQuality":"LOSSLESS","audioModes":["STEREO"]},
                      {"id":"atmos-1","title":"Song","artist":"Artist","duration":200.0,"audioQuality":"LOW","audioModes":["DOLBY_ATMOS"]}
                    ]}
                """.trimIndent()
                "/stream/atmos-1" -> 200 to """
                    {"url":"https://cdn.example.com/manifests/12345","manifest":"dash","format":"dash","codec":"eac3_joc","container":"mp4","streamQuality":"[Tidal] DOLBY_ATMOS","atmos":true,"encrypted":false}
                """.trimIndent()
                else -> 404 to "{}"
            }
        }) { baseUrl ->
            val stream = MusicAddons.resolveUpgrade(
                listOf(MusicAddon(baseUrl, "Tidal")),
                title = "Song",
                artists = listOf("Artist", "Other"),
                durationSec = 200,
                playingBitrate = 141_000,
                atmos = true,
            )
            assertNotNull(stream)
            assertTrue(stream!!.isDolbyAtmos)
            assertEquals("eac3-joc", stream.codec)
            assertEquals(MimeTypes.APPLICATION_MPD, stream.manifestMimeType)
            assertTrue(seenQueries.any { "title=Song" in it && "artist=Artist" in it && "atmos=auto" in it })
        }
    }

    @Test
    fun `uses exact uppercase HLS MIME type required by Media3`() {
        withServer({ path, _ ->
            when (path) {
                "/search" -> 200 to """{"tracks":[{"id":"1","title":"Song","artist":"Artist","duration":200.0,"audioQuality":"DOLBY_ATMOS"}]}"""
                "/stream/1" -> 200 to """{"url":"https://cdn.example.com/stream","mediaType":"hls","atmos":true,"encrypted":"none"}"""
                else -> 404 to "{}"
            }
        }) { baseUrl ->
            val stream = MusicAddons.resolveUpgrade(
                listOf(MusicAddon(baseUrl, "HLSAddon")),
                title = "Song",
                artists = listOf("Artist"),
                durationSec = 200,
                playingBitrate = 141_000,
                atmos = true,
            )!!
            assertEquals("application/x-mpegURL", MimeTypes.APPLICATION_M3U8)
            assertEquals("application/x-mpegURL", stream.manifestMimeType)
            assertTrue(stream.isDolbyAtmos)
            assertEquals("eac3-joc", stream.codec)
        }
    }

    @Test
    fun `resolveUpgrade waits for all add-ons so slower Atmos beats faster Hi-Res`() {
        withServer({ path, _ ->
            when (path) {
                "/fast/search" -> 200 to """{"tracks":[{"id":"1","title":"Song","artist":"Artist","duration":200.0,"audioQuality":"HI_RES_LOSSLESS"}]}"""
                "/fast/stream/1" -> 200 to """{"url":"https://cdn.example.com/fast.flac","codec":"flac","bitDepth":24,"sampleRate":96000}"""
                "/slow/search" -> {
                    Thread.sleep(150)
                    200 to """{"tracks":[{"id":"2","title":"Song","artist":"Artist","duration":200.0,"audioModes":["DOLBY_ATMOS"]}]}"""
                }
                "/slow/stream/2" -> 200 to """{"url":"https://cdn.example.com/atmos","manifest":"dash","format":"dash","codec":"eac3_joc","atmos":true}"""
                else -> 404 to "{}"
            }
        }) { baseUrl ->
            val stream = MusicAddons.resolveUpgrade(
                listOf(
                    MusicAddon("$baseUrl/fast", "FastHiRes"),
                    MusicAddon("$baseUrl/slow", "SlowAtmos"),
                ),
                title = "Song",
                artists = listOf("Artist"),
                durationSec = 200,
                playingBitrate = 141_000,
                atmos = true,
            )!!
            assertEquals("SlowAtmos", stream.source)
            assertTrue(stream.isDolbyAtmos)
            assertEquals(MimeTypes.APPLICATION_MPD, stream.manifestMimeType)
        }
    }

    @Test
    fun `falls back from failing Atmos row to stereo Hi-Res and rejects DRM`() {
        val requests = ConcurrentHashMap.newKeySet<String>()
        withServer({ path, _ ->
            requests += path
            when (path) {
                "/search" -> 200 to """
                    {"tracks":[
                      {"id":"atmos-drm","title":"Song","artist":"Artist","duration":200.0,"audioModes":["DOLBY_ATMOS"]},
                      {"id":"atmos-dead","title":"Song","artist":"Artist","duration":200.0,"atmos":true},
                      {"id":"hires-ok","title":"Song","artist":"Artist","duration":200.0,"audioQuality":"HI_RES_LOSSLESS"}
                    ]}
                """.trimIndent()
                "/stream/atmos-drm" -> 200 to """{"url":"https://cdn.example.com/drm","encrypted":"widevine","atmos":true}"""
                "/stream/atmos-dead" -> 500 to """{"error":"upstream timeout"}"""
                "/stream/hires-ok" -> 200 to """{"url":"https://cdn.example.com/hires","format":"dash","quality":"Tidal · Hi-Res FLAC (DASH)","streamQuality":"[Tidal] HI_RES_LOSSLESS"}"""
                else -> 404 to "{}"
            }
        }) { baseUrl ->
            val stream = MusicAddons.resolveUpgrade(
                listOf(MusicAddon(baseUrl, "Addon")),
                title = "Song",
                artists = listOf("Artist"),
                durationSec = 200,
                playingBitrate = 141_000,
                atmos = true,
            )!!
            assertFalse(stream.isDolbyAtmos)
            assertEquals("flac", stream.codec)
            assertEquals(MimeTypes.APPLICATION_MPD, stream.manifestMimeType)
            assertTrue("/stream/hires-ok" in requests)
        }
    }

    @Test
    fun `isBetter ranks Dolby Atmos above Hi-Res above CD lossless`() {
        val cd = MusicAddons.Stream("https://e/1", "A", "1", "A", codec = "flac", bitDepth = 16, sampleRateHz = 44_100)
        val hires = MusicAddons.Stream("https://e/2", "B", "2", "B", codec = "flac", bitDepth = 24, sampleRateHz = 96_000)
        val atmos = MusicAddons.Stream("https://e/3", "C", "3", "C", codec = "eac3-joc", isDolbyAtmos = true)
        assertTrue(MusicAddons.isBetter(hires, cd))
        assertFalse(MusicAddons.isBetter(cd, hires))
        assertTrue(MusicAddons.isBetter(atmos, hires))
        assertFalse(MusicAddons.isBetter(hires, atmos))
    }
}
