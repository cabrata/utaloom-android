package com.metrolist.music.ui.player

import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.playback.MusicAddons
import com.metrolist.music.ui.player.AudioQualityInfo.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualityInfoTest {
    private fun stream(codec: String?, bitDepth: Int? = null, sampleRateHz: Int? = null) =
        MusicAddons.Stream("https://example.com/a.flac", "k-DL", "key", "k-DL", codec, bitDepth, sampleRateHz)

    private val opus = FormatEntity("id", 251, "audio/webm", "opus", 141_000, 48_000, 1L, null, null, null)

    @Test
    fun `add-on stream wins over the stored YouTube format`() {
        val info = audioQualityOf(stream("flac", 16, 44_100), opus)!!
        assertEquals(Tier.LOSSLESS, info.tier)
        assertEquals("k-DL", info.addon)
        assertEquals("FLAC", info.codec)
    }

    @Test
    fun `hi-res needs more than CD quality`() {
        assertEquals(Tier.HI_RES, audioQualityOf(stream("FLAC", 24, 44_100), null)!!.tier)
        assertEquals(Tier.HI_RES, audioQualityOf(stream("flac", 16, 96_000), null)!!.tier)
        assertEquals(Tier.LOSSLESS, audioQualityOf(stream("flac"), null)!!.tier)
        assertEquals(Tier.LOSSY, audioQualityOf(stream("mp3", 24, 96_000), null)!!.tier)
        assertEquals(Tier.LOSSY, audioQualityOf(stream(null), null)!!.tier)
    }

    @Test
    fun `badge uses the add-on's quality and measured values beat reported ones`() {
        val reported = stream("flac", 16, 44_100).copy(quality = "LOSSLESS", bitrateKbps = 900)
        val info = audioQualityOf(reported, opus, PlayedAudio(bitDepth = 24, sampleRateHz = 96_000, bitrateKbps = 2_900))!!
        assertEquals("LOSSLESS", info.label)
        assertEquals(Tier.HI_RES, info.tier)
        assertEquals(24, info.bitDepth)
        assertEquals(96_000, info.sampleRateHz)
        assertEquals(2_900, info.bitrateKbps)
        assertEquals(900, audioQualityOf(reported, opus, PlayedAudio())!!.bitrateKbps)
        assertNull(audioQualityOf(null, opus)!!.label)
    }

    @Test
    fun `average bitrate comes from file size over length`() {
        // 41 699 102 bytes over 5:54
        assertEquals(942, averageBitrateKbps(41_699_102L, 354_000L))
        assertNull(averageBitrateKbps(null, 354_000L))
        assertNull(averageBitrateKbps(41_699_102L, -1L))
    }

    @Test
    fun `YouTube format is lossy and names its codec`() {
        val info = audioQualityOf(null, opus)!!
        assertEquals(Tier.LOSSY, info.tier)
        assertEquals("Opus", info.codec)
        assertNull(info.addon)
        assertEquals(141, info.bitrateKbps)
        assertEquals("AAC", audioQualityOf(null, opus.copy(codecs = "mp4a.40.2"))!!.codec)
        assertNull(audioQualityOf(null, null))
    }
}
