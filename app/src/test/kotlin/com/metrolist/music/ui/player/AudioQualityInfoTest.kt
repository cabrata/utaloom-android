package com.metrolist.music.ui.player

import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.playback.MusicAddons
import com.metrolist.music.ui.player.AudioQualityInfo.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `badge tier uses measured values and keeps the add-on label as detail`() {
        val reported = stream("flac", 16, 44_100).copy(quality = "LOSSLESS", bitrateKbps = 900)
        val info = audioQualityOf(reported, opus, PlayedAudio(bitDepth = 24, sampleRateHz = 96_000, bitrateKbps = 2_900))!!
        assertEquals("LOSSLESS", info.reportedQuality)
        assertEquals(Tier.HI_RES, info.tier)
        assertEquals(24, info.bitDepth)
        assertEquals(96_000, info.sampleRateHz)
        assertEquals(2_900, info.bitrateKbps)
        assertEquals(900, audioQualityOf(reported, opus, PlayedAudio())!!.bitrateKbps)
        assertNull(audioQualityOf(null, opus)!!.reportedQuality)
    }

    @Test
    fun `selected track beats the format and quality claimed by the add-on`() {
        val reported = stream("flac", 24, 96_000).copy(quality = "HI-RES")
        val played = PlayedAudio(sampleRateHz = 48_000, bitrateKbps = 128, mimeType = "audio/mp4a-latm")
        val result = audioQualityOf(reported, opus, played)!!
        assertEquals(Tier.LOSSY, result.tier)
        assertEquals("AAC", result.codec)
        assertEquals(128, result.bitrateKbps)
        assertEquals(Tier.LOSSY, audioQualityOf(null, opus, played)!!.tier)
        assertEquals("AAC", audioQualityOf(null, null, played)!!.codec)
    }

    @Test
    fun `high quality needs at least 256 kbps and Atmos needs JOC`() {
        val aac = stream("aac").copy(quality = "HIGH", bitrateKbps = 255)
        assertEquals(Tier.LOSSY, audioQualityOf(aac, null)!!.tier)
        assertEquals(Tier.HIGH_QUALITY, audioQualityOf(aac.copy(bitrateKbps = 256), null)!!.tier)
        assertEquals(Tier.LOSSY, audioQualityOf(stream("ec-3").copy(quality = "DOLBY"), null)!!.tier)
        val atmos = audioQualityOf(aac, null, PlayedAudio(mimeType = "audio/eac3-joc"))!!
        assertEquals(Tier.DOLBY_ATMOS, atmos.tier)
        assertEquals("E-AC-3 JOC", atmos.codec)
        assertEquals(Tier.DOLBY_ATMOS, audioQualityOf(stream("eac3_joc"), null)!!.tier)
        assertEquals(Tier.DOLBY_ATMOS, audioQualityOf(stream("ec3-joc"), null)!!.tier)
    }

    @Test
    fun `only confirmed premium codecs get the quality shimmer`() {
        assertTrue(PlayedAudio(mimeType = "audio/x-flac").isPremiumFormat)
        assertTrue(PlayedAudio(mimeType = "audio/eac3-joc").isPremiumFormat)
        assertFalse(PlayedAudio(mimeType = "audio/eac3").isPremiumFormat)
        assertFalse(PlayedAudio(mimeType = "audio/opus").isPremiumFormat)
        assertFalse(PlayedAudio().isPremiumFormat)
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
