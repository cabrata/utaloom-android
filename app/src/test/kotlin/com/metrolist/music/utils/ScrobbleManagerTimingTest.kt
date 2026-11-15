package com.metrolist.music.utils

import com.metrolist.lastfm.LastFM.ScrobbleResult
import com.metrolist.music.utils.ScrobbleManager.ScrobbleTiming
import com.metrolist.music.utils.ScrobbleManager.PendingScrobble
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleManagerTimingTest {
    private fun entry(id: String) =
        PendingScrobble(artist = "a", track = id, timestamp = 1L, album = null, duration = 200)

    @Test
    fun `delay is half the track unless capped by max delay`() {
        assertEquals(100_000L, ScrobbleTiming.initialDelayMillis(200, 0.5f, 180))
        assertEquals(50_000L, ScrobbleTiming.initialDelayMillis(100, 0.5f, 180))
        assertEquals(180_000L, ScrobbleTiming.initialDelayMillis(600, 0.5f, 180))
        assertEquals(0L, ScrobbleTiming.initialDelayMillis(0, 0.5f, 180))
    }

    @Test
    fun `queue drains fully when every send succeeds`() = runBlocking {
        val persisted = mutableListOf<List<PendingScrobble>>()
        ScrobbleTiming.drainQueue(
            listOf(entry("1"), entry("2"), entry("3")),
            send = { ScrobbleResult.Sent },
            persist = { persisted.add(it) },
        )
        assertEquals(3, persisted.size)
        assertTrue(persisted.last().isEmpty())
    }

    @Test
    fun `transient failure stops the drain and keeps the failed entry`() = runBlocking {
        val persisted = mutableListOf<List<PendingScrobble>>()
        val attempted = mutableListOf<String>()
        ScrobbleTiming.drainQueue(
            listOf(entry("1"), entry("2"), entry("3")),
            send = {
                attempted.add(it.track)
                if (it.track == "2") ScrobbleResult.Retry else ScrobbleResult.Sent
            },
            persist = { persisted.add(it) },
        )
        assertEquals(listOf("1", "2"), attempted)
        assertEquals(listOf("2", "3"), persisted.last().map { it.track })
    }

    @Test
    fun `permanent failure drops the entry and continues`() = runBlocking {
        val persisted = mutableListOf<List<PendingScrobble>>()
        ScrobbleTiming.drainQueue(
            listOf(entry("1"), entry("2")),
            send = { if (it.track == "1") ScrobbleResult.Failed else ScrobbleResult.Sent },
            persist = { persisted.add(it) },
        )
        assertTrue(persisted.last().isEmpty())
    }
}
