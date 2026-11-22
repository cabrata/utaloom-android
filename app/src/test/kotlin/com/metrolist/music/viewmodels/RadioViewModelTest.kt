package com.metrolist.music.viewmodels

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.ui.screens.radio.playRadioArtist
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class RadioViewModelTest {
    private lateinit var database: InternalDatabase
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()
        database.close()
    }

    @Test
    fun `signed-out songs and last play load while missing artist artwork is still pending`() = runBlocking {
        database.dao.insert(SongEntity("song", "Song"))
        database.dao.insert(ArtistEntity("UCartist", "Artist", cachedPageJson = "cached-page"))
        database.dao.insert(SongArtistMap("song", "UCartist", 0))
        database.dao.insert(Event(songId = "song", timestamp = LocalDateTime.now().minusMinutes(1), playTime = 60_000))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val viewModel = RadioViewModel(
            MusicDatabase(database),
            fetchArtist = {
                started.complete(Unit)
                release.await()
                Result.success(artistPage("artist.jpg"))
            },
            fetchHome = { error("Signed-out local radio must not fetch home") },
        )
        store.put("radio", viewModel)
        withTimeout(5_000) { started.await() }
        await { viewModel.lastPlayed.value != null && viewModel.stations.value != null }
        assertEquals("song", viewModel.lastPlayed.value!!.id)
        assertEquals(listOf("song"), viewModel.stations.value!!.map { it.id })
        assertTrue(viewModel.artistStations.value!!.isEmpty())
        assertTrue(viewModel.isLoading.value)
        viewModel.loadPersonalizedStations(null, null, "0")
        assertTrue(viewModel.personalizedStations.value!!.isEmpty())
        release.complete(Unit)
        await { !viewModel.isLoading.value }
        assertEquals("artist.jpg", viewModel.artistStations.value!!.single().thumbnailUrl)
        assertEquals("artist.jpg", database.dao.getArtistById("UCartist")!!.thumbnailUrl)
        assertEquals("cached-page", database.dao.getArtistById("UCartist")!!.cachedPageJson)
    }

    @Test
    fun `switching accounts and signing out cannot publish stale personalized stations`() = runBlocking {
        val started = Channel<Int>(Channel.UNLIMITED)
        val releaseOld = CompletableDeferred<Unit>()
        val oldFinished = CompletableDeferred<Unit>()
        val count = AtomicInteger()
        val viewModel = RadioViewModel(
            MusicDatabase(database),
            fetchArtist = { error("No artists") },
            fetchHome = {
                val index = count.incrementAndGet()
                started.send(index)
                if (index == 1) {
                    withContext(NonCancellable) { releaseOld.await() }
                    oldFinished.complete(Unit)
                }
                Result.success(home("RDTMAK-$index"))
            },
        )
        store.put("radio", viewModel)
        viewModel.loadPersonalizedStations("SAPISID=session", "profile-a", "0")
        withTimeout(5_000) { assertEquals(1, started.receive()) }
        assertNull(viewModel.personalizedStations.value)
        viewModel.loadPersonalizedStations("SAPISID=session", "profile-b", "1")
        withTimeout(5_000) { assertEquals(2, started.receive()) }
        await { viewModel.personalizedStations.value?.isNotEmpty() == true }
        assertEquals(listOf("RDTMAK-2"), viewModel.personalizedStations.value!!.map { it.id })
        viewModel.loadPersonalizedStations("SAPISID=session", "profile-b", "1")
        assertEquals(2, count.get())
        viewModel.loadPersonalizedStations(null, null, "0")
        releaseOld.complete(Unit)
        withTimeout(5_000) { oldFinished.await() }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(viewModel.personalizedStations.value!!.isEmpty())
        assertFalse(viewModel.personalizedError.value)
        assertEquals(2, count.get())
    }

    @Test
    fun `artist playback and null or failed endpoint fallback stay on main`() = runBlocking {
        val artist = Artist(ArtistEntity("UCartist", "Artist"), 1)
        val endpoint = WatchEndpoint(playlistId = "artist-radio")
        suspend fun check(result: Result<ArtistPage>, shouldPlay: Boolean) {
            var fetchOnMain = true
            var playCalls = 0
            var fallbackCalls = 0
            val task = async(Dispatchers.IO) {
                playRadioArtist(
                    artist = artist,
                    onPlay = {
                        assertSame(Looper.getMainLooper(), Looper.myLooper())
                        assertEquals(endpoint, it)
                        playCalls++
                    },
                    onFallback = {
                        assertSame(Looper.getMainLooper(), Looper.myLooper())
                        fallbackCalls++
                    },
                    fetch = {
                        fetchOnMain = Looper.myLooper() == Looper.getMainLooper()
                        result
                    },
                )
            }
            await { task.isCompleted }
            task.await()
            assertFalse(fetchOnMain)
            assertEquals(if (shouldPlay) 1 else 0, playCalls)
            assertEquals(if (shouldPlay) 0 else 1, fallbackCalls)
        }
        check(Result.success(artistPage("artist.jpg", endpoint)), true)
        check(Result.success(artistPage("artist.jpg")), false)
        check(Result.failure(IllegalStateException("artist unavailable")), false)
        val cancelled = CancellationException("artist cancelled")
        try {
            playRadioArtist(artist, { fail("Cancelled playback") }, { fail("Cancelled fallback") }) {
                Result.failure(cancelled)
            }
            fail("Cancellation swallowed")
        } catch (error: CancellationException) {
            assertSame(cancelled, error)
        }
    }

    private suspend fun await(matches: () -> Boolean) = withTimeout(10_000) {
        while (!matches()) {
            shadowOf(Looper.getMainLooper()).idle()
            delay(10)
        }
    }

    private fun artistPage(thumbnail: String, endpoint: WatchEndpoint? = null) = ArtistPage(
        artist = ArtistItem("UCartist", "Artist", thumbnail, shuffleEndpoint = null, radioEndpoint = endpoint),
        sections = emptyList(),
        description = null,
        subscriberCountText = null,
    )

    private fun home(id: String) = HomePage(
        chips = null,
        sections = listOf(HomePage.Section("Mixes", null, null, null, listOf(
            PlaylistItem(id, id, null, null, "$id.jpg", null, null, null),
        ))),
    )
}
