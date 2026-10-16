/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpotifyCanvasClientTest {
    @Test
    fun `canvas url regex extracts valid mp4 url`() {
        val rawResponse = "some_bytes_https://canvaz.scdn.co/upload/artist/123/video/abc.cnvs.mp4_more_bytes"
        val regex = Regex("""https://canvaz\.scdn\.co/[^\x00-\x1f\x7f-\xff"]+\.mp4""")
        val match = regex.find(rawResponse)
        assertNotNull(match)
        assertEquals("https://canvaz.scdn.co/upload/artist/123/video/abc.cnvs.mp4", match?.value)
    }
}
