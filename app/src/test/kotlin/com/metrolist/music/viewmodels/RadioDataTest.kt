package com.metrolist.music.viewmodels

import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.ArtistEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RadioDataTest {
    @Test
    fun `artist rows appear with resolved art without waiting for slower artists`() = runBlocking {
        val artists = listOf(
            artist("UCcached", "cached.jpg"),
            artist("UCslow"),
            artist("UCfast"),
            artist("LAlocal"),
        )
        val replies = mapOf("UCslow" to CompletableDeferred<Unit>(), "UCfast" to CompletableDeferred<Unit>())
        val started = Channel<String>(Channel.UNLIMITED)
        val snapshots = mutableListOf<List<Artist>>()
        val saved = mutableMapOf<String, String>()
        val loading = async {
            loadRadioArtistThumbnails(artists, { id, thumbnail -> saved[id] = thumbnail }, { snapshots += it }) { id ->
                started.send(id)
                replies.getValue(id).await()
                Result.success(artistPage(id, "$id.jpg"))
            }
        }
        try {
            withTimeout(3_000) { repeat(2) { started.receive() } }
            assertEquals(listOf("UCcached", "LAlocal"), snapshots.single().map { it.id })
            replies.getValue("UCfast").complete(Unit)
            withTimeout(3_000) { while (snapshots.last().size != 3) yield() }
            assertFalse(loading.isCompleted)
            assertEquals(listOf("UCcached", "UCfast", "LAlocal"), snapshots.last().map { it.id })
            assertEquals("UCfast.jpg", snapshots.last().first { it.id == "UCfast" }.thumbnailUrl)
            replies.getValue("UCslow").complete(Unit)
            loading.await()
            assertEquals(artists.map { it.id }, snapshots.last().map { it.id })
            assertEquals(mapOf("UCslow" to "UCslow.jpg", "UCfast" to "UCfast.jpg"), saved)
            assertTrue(snapshots.flatMap { it }.filter { it.artist.isYouTubeArtist }.all { !it.thumbnailUrl.isNullOrBlank() })
        } finally {
            loading.cancel()
        }
    }

    @Test
    fun `artist requests are bounded to four`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = Channel<String>(Channel.UNLIMITED)
        var active = 0
        var maximum = 0
        var calls = 0
        val loading = async {
            loadRadioArtistThumbnails((0..9).map { artist("UC$it") }, { _, _ -> }, {}) { id ->
                calls++
                active++
                maximum = maxOf(maximum, active)
                started.send(id)
                gate.await()
                active--
                Result.success(artistPage(id, "$id.jpg"))
            }
        }
        try {
            withTimeout(3_000) { repeat(4) { started.receive() } }
            yield()
            assertEquals(4, active)
            assertTrue(started.tryReceive().isFailure)
            gate.complete(Unit)
            withTimeout(3_000) { loading.await() }
            assertEquals(4, maximum)
            assertEquals(10, calls)
        } finally {
            loading.cancel()
        }
    }

    @Test
    fun `failed artwork shows fallback but cancellation cannot publish an abandoned row`() = runBlocking {
        val snapshots = mutableListOf<List<Artist>>()
        loadRadioArtistThumbnails(listOf(artist("UCmissing")), { _, _ -> fail("No thumbnail to save") }, { snapshots += it }) {
            Result.failure(IllegalStateException("artist unavailable"))
        }
        assertEquals(listOf("UCmissing"), snapshots.last().map { it.id })
        snapshots.clear()
        val cancelled = CancellationException("request cancelled")
        try {
            loadRadioArtistThumbnails(listOf(artist("UCcancelled")), { _, _ -> fail("Cancelled thumbnail saved") }, { snapshots += it }) {
                Result.failure(cancelled)
            }
            fail("Cancellation swallowed")
        } catch (error: CancellationException) {
            assertEquals(cancelled.message, error.message)
        }
        assertEquals(listOf(emptyList<Artist>()), snapshots)
    }

    @Test
    fun `home continuations locate localized mixes and deduplicate browse ids without inventing endpoints`() = runBlocking {
        val calls = mutableListOf<String?>()
        val mix = playlist("RDTMAK-one", "Campuran Saya")
        val result = loadHomeRadioStations { continuation ->
            calls += continuation
            Result.success(
                if (continuation == null) home(listOf(playlist("PLnormal")), "next")
                else home(listOf(mix, mix.copy(id = "VLRDTMAK-one"), playlist("RDTMAK-podcast").copy(isPodcast = true)), "unused"),
            )
        }
        assertEquals(listOf(null, "next"), calls)
        assertEquals(listOf(mix), result)
        assertEquals("Campuran Saya", result.single().title)
        assertEquals(null, result.single().playEndpoint)
    }

    @Test
    fun `repeated home continuation terminates and cancellation propagates`() = runBlocking {
        val calls = mutableListOf<String?>()
        assertTrue(loadHomeRadioStations {
            calls += it
            Result.success(home(emptyList(), "same"))
        }.isEmpty())
        assertEquals(listOf(null, "same"), calls)
        val cancelled = CancellationException("home cancelled")
        try {
            loadHomeRadioStations { Result.failure(cancelled) }
            fail("Cancellation swallowed")
        } catch (error: CancellationException) {
            assertSame(cancelled, error)
        }
    }

    @Test
    fun `station lookup does not crawl an entire home feed without mixes`() = runBlocking {
        var calls = 0
        assertTrue(loadHomeRadioStations {
            calls++
            Result.success(home(emptyList(), "next-$calls"))
        }.isEmpty())
        assertEquals(3, calls)
    }

    private fun artist(id: String, thumbnail: String? = null) = Artist(ArtistEntity(id, id, thumbnail), songCount = 1)

    private fun artistPage(id: String, thumbnail: String) = ArtistPage(
        artist = ArtistItem(id = id, title = id, thumbnail = thumbnail, shuffleEndpoint = null, radioEndpoint = WatchEndpoint(playlistId = "radio-$id")),
        sections = emptyList(),
        description = null,
        subscriberCountText = null,
    )

    private fun playlist(id: String, title: String = id) = PlaylistItem(
        id = id, title = title, author = null, songCountText = null, thumbnail = "$id.jpg",
        playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null,
    )

    private fun home(items: List<PlaylistItem>, continuation: String?) = HomePage(
        chips = null,
        sections = listOf(HomePage.Section("Mixed for you", null, null, null, items)),
        continuation = continuation,
    )
}
