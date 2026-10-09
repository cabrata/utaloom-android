package com.metrolist.music.utils

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    @Test
    fun parsesOnlyUtaloomReleaseArtifacts() {
        val assets = JSONArray(
            """
            [
              {"name": "Utaloom-Android.apk", "browser_download_url": "https://example.com/foss.apk", "size": 42},
              {"name": "Utaloom-Android-with-Google-Cast.apk", "browser_download_url": "https://example.com/gms.apk", "size": 43},
              {"name": "Metrolist.apk", "browser_download_url": "https://example.com/old.apk", "size": 44},
              {"name": "source.zip", "browser_download_url": "https://example.com/source.zip", "size": 45}
            ]
            """.trimIndent(),
        )
        val parsed = Updater.parseAssets(assets)

        assertEquals(listOf("foss", "gms"), parsed.map { it.variant })
        assertEquals(listOf("universal", "universal"), parsed.map { it.architecture })
        assertEquals("https://example.com/foss.apk", Updater.getDownloadUrlForCurrentVariant(
            ReleaseInfo("v13.7.0", "13.7.0", "", "2026-10-09T00:00:00Z", parsed),
        ))
        assertFalse(Updater.isUpdateAvailable("13.7.0", "13.7.0"))
        assertTrue(Updater.isUpdateAvailable("13.7.0", "13.7.1"))
    }
}
