package com.metrolist.music.ui.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPipelineTextTest {
    @Test
    fun `rates drop a trailing zero`() {
        assertEquals("44.1 kHz", rateText(44_100))
        assertEquals("48 kHz", rateText(48_000))
    }

    @Test
    fun `pcm buffers read as time, compressed ones as size`() {
        // 16-bit stereo: 4 bytes a frame, 9600 frames at 48 kHz is 200 ms.
        assertEquals("200 ms (9600 frames)", bufferText(38_400, C.ENCODING_PCM_16BIT, 2, 48_000))
        assertEquals("Float32", encodingText(C.ENCODING_PCM_FLOAT))
        assertEquals("64 KiB", bufferText(65_536, C.ENCODING_OPUS, 2, 48_000))
    }
}
