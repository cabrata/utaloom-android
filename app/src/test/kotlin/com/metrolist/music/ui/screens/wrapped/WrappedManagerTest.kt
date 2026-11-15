/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WrappedManagerTest {
    private lateinit var room: InternalDatabase
    private lateinit var database: MusicDatabase
    private lateinit var manager: WrappedManager
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        room = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build()
        database = MusicDatabase(room)
        manager = WrappedManager(room.dao, context, database, appScope)
    }

    @After
    fun tearDown() {
        manager.dispose()
        appScope.cancel()
        room.close()
    }

    @Test
    fun `prepare includes millisecond year boundaries and saves selected year locally`() = runBlocking {
        val year = WrappedConstants.YEAR - 1
        val before = SongEntity("before", "Before")
        val first = SongEntity("first", "First")
        val top = SongEntity("top", "Top", thumbnailUrl = "top.jpg")
        val after = SongEntity("after", "After")
        withContext(Dispatchers.IO) {
            listOf(before, first, top, after).forEach { room.dao.insert(it) }
            room.dao.insert(Event(songId = before.id, timestamp = LocalDateTime.of(year - 1, 12, 31, 23, 59, 59, 999_000_000), playTime = 900_000))
            room.dao.insert(Event(songId = first.id, timestamp = LocalDateTime.of(year, 1, 1, 0, 0), playTime = 60_000))
            room.dao.insert(Event(songId = top.id, timestamp = LocalDateTime.of(year, 12, 31, 23, 59, 59, 999_000_000), playTime = 120_000))
            room.dao.insert(Event(songId = after.id, timestamp = LocalDateTime.of(year + 1, 1, 1, 0, 0), playTime = 900_000))
        }

        manager.prepare(year)
        val state = manager.state.value
        assertTrue(state.isDataReady)
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals(year, state.year)
        assertEquals(listOf(top.id, first.id), state.topSongs.map { it.id })
        assertNull(state.topSongs.last().thumbnailUrl)
        assertEquals(2, state.uniqueSongCount)
        assertEquals(3L, state.totalMinutes)
        assertTrue(year in state.availableYears)
        assertTrue(WrappedConstants.YEAR in state.availableYears)

        manager.createPlaylist()
        val saved = withTimeout(5_000) {
            manager.state.first { it.playlistCreationState == PlaylistCreationState.Success || it.playlistError }
        }
        assertFalse(saved.playlistError)
        assertNotNull(saved.playlistId)
        val playlist = database.playlist(saved.playlistId!!).first()!!
        assertEquals("Utaloom $year", playlist.playlist.name)
        assertEquals("top.jpg", playlist.playlist.thumbnailUrl)
        assertTrue(playlist.playlist.isLocal)
        assertEquals(listOf(top.id, first.id), database.playlistSongIds(playlist.id))
    }

    @Test
    fun `annual artist totals preserve durations larger than integer milliseconds`() = runBlocking {
        val year = WrappedConstants.YEAR - 1
        val song = SongEntity("annual-favorite", "Annual favorite", thumbnailUrl = "favorite.jpg")
        val artist = ArtistEntity(id = "annual-artist", name = "Annual artist")
        withContext(Dispatchers.IO) {
            room.dao.insert(song)
            room.dao.insert(artist)
            room.dao.insert(SongArtistMap(song.id, artist.id, 0))
            repeat(3) { month ->
                room.dao.insert(Event(songId = song.id, timestamp = LocalDateTime.of(year, month + 1, 1, 0, 0), playTime = 1_500_000_000L))
            }
        }

        manager.prepare(year)

        assertFalse(manager.state.value.hasError)
        assertEquals(4_500_000_000L, manager.state.value.topArtists.single().timeListened)
        assertEquals(75_000L, manager.state.value.totalMinutes)
    }

    @Test
    fun `empty history keeps current year selectable and returns zero data`() = runBlocking {
        manager.prepare(WrappedConstants.YEAR)

        val state = manager.state.value
        assertTrue(state.isDataReady)
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals(listOf(WrappedConstants.YEAR), state.availableYears)
        assertEquals(0, state.uniqueSongCount)
        assertEquals(0, state.uniqueArtistCount)
        assertEquals(0, state.totalAlbums)
        assertEquals(0L, state.totalMinutes)
        assertTrue(state.topSongs.isEmpty())
        assertNull(state.topAlbum)
    }

    @Test
    fun `preparation failure clears loading and can be retried`() = runBlocking {
        manager.prepare(WrappedConstants.YEAR + 1)
        assertFalse(manager.state.value.isLoading)
        assertTrue(manager.state.value.hasError)

        manager.prepare(WrappedConstants.YEAR, force = true)
        assertTrue(manager.state.value.isDataReady)
        assertFalse(manager.state.value.isLoading)
        assertFalse(manager.state.value.hasError)
    }
}
