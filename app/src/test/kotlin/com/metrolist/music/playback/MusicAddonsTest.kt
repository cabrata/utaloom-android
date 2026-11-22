package com.metrolist.music.playback

import com.metrolist.music.playback.MusicAddons.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
