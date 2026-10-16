/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `extractSpDc correctly extracts cookie from various headers`() {
        val single = "sp_dc=AQB_secret_cookie_value_123"
        assertEquals("AQB_secret_cookie_value_123", extractSpDc(single))

        val multi = "sp_t=abc-123; sp_dc=AQB_secret_cookie_value_123; sp_key=xyz"
        assertEquals("AQB_secret_cookie_value_123", extractSpDc(multi))

        val spaced = "  sp_t=abc ;   sp_dc=AQB_spaced_value_999  ; other=123 "
        assertEquals("AQB_spaced_value_999", extractSpDc(spaced))

        assertNull(extractSpDc(null))
        assertNull(extractSpDc(""))
        assertNull(extractSpDc("   "))
        assertNull(extractSpDc("sp_t=abc; other=xyz"))
        assertNull(extractSpDc("sp_dc="))
    }
}
