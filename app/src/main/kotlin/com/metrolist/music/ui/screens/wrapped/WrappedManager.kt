/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import android.content.Context
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

sealed class PlaylistCreationState {
    object Idle : PlaylistCreationState()
    object Creating : PlaylistCreationState()
    object Success : PlaylistCreationState()
}

class WrappedManager(
    private val databaseDao: DatabaseDao,
    context: Context,
    private val database: MusicDatabase = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WrappedEntryPoint::class.java,
    ).database(),
    applicationScope: CoroutineScope = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WrappedEntryPoint::class.java,
    ).applicationScope(),
) {
    private val job = SupervisorJob(applicationScope.coroutineContext[Job])
    private val scope = CoroutineScope(applicationScope.coroutineContext + job)
    private val preparationMutex = Mutex()
    private val _state = MutableStateFlow(WrappedState())
    val state = _state.asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    fun createPlaylist(imageResName: String = "wrapped_playlistv1") {
        val year = _state.value.year
        scope.launch {
            preparationMutex.withLock {
                val snapshot = _state.value
                if (snapshot.year != year || !snapshot.isDataReady || snapshot.topSongs.isEmpty() ||
                    snapshot.playlistCreationState != PlaylistCreationState.Idle
                ) return@withLock

                _state.update {
                    it.copy(playlistCreationState = PlaylistCreationState.Creating, playlistError = false)
                }
                try {
                    val playlistId = withContext(Dispatchers.IO) {
                        val period = wrappedPeriod(year)
                        val songs = databaseDao.mostPlayedSongsStats(
                            fromTimeStamp = period.rankingStart,
                            toTimeStamp = period.endInclusive,
                            limit = 100,
                        ).first()
                        check(songs.isNotEmpty()) { "No songs to save for Wrapped $year" }
                        val playlist = PlaylistEntity(
                            name = "Utaloom $year",
                            thumbnailUrl = snapshot.topSongs.first().thumbnailUrl,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                            isLocal = true,
                        )
                        database.withTransaction {
                            insert(playlist)
                            addSongsToPlaylist(
                                Playlist(playlist, 0, emptyList()),
                                songs.map { it.id to null },
                            )
                            check(playlistSongIds(playlist.id) == songs.map { it.id }) {
                                "Wrapped playlist songs were not inserted"
                            }
                        }
                        playlist.id
                    }
                    _state.update {
                        it.copy(playlistCreationState = PlaylistCreationState.Success, playlistId = playlistId)
                    }
                } catch (e: CancellationException) {
                    _state.update { it.copy(playlistCreationState = PlaylistCreationState.Idle) }
                    throw e
                } catch (e: Exception) {
                    Timber.tag("WrappedManager").e(e, "Error saving Wrapped playlist for %d", year)
                    _state.update {
                        it.copy(playlistCreationState = PlaylistCreationState.Idle, playlistError = true)
                    }
                }
            }
        }
    }

    suspend fun prepare(year: Int = WrappedConstants.YEAR, force: Boolean = false) {
        preparationMutex.withLock {
            val previous = _state.value
            if (previous.year == year && previous.isDataReady && !force) return@withLock
            val loading = WrappedState(
                year = year,
                availableYears = previous.availableYears,
                isLoading = true,
                playlistCreationState = if (previous.year == year) previous.playlistCreationState else PlaylistCreationState.Idle,
                playlistId = previous.playlistId.takeIf { previous.year == year },
            )
            _state.value = loading
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val now = LocalDateTime.now()
                    val period = wrappedPeriod(year, now)
                    val years = async {
                        val cursor = database.openHelper.readableDatabase.query(
                            "SELECT MIN(timestamp) FROM event",
                        )
                        val earliestYear = cursor.use {
                            if (!it.moveToFirst() || it.isNull(0)) now.year else {
                                LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(it.getLong(0)),
                                    java.time.ZoneOffset.UTC,
                                ).year
                            }
                        }
                        (now.year downTo earliestYear.coerceIn(1, now.year)).toList()
                    }
                    val songs = async {
                        databaseDao.mostPlayedSongsStats(
                            period.rankingStart,
                            limit = 30,
                            toTimeStamp = period.endInclusive,
                        ).first()
                    }
                    val artists = async {
                        databaseDao.mostPlayedArtists(
                            period.rankingStart,
                            limit = 5,
                            toTimeStamp = period.endInclusive,
                        ).first().sortedByDescending { it.timeListened ?: 0 }
                    }
                    val albums = async {
                        databaseDao.mostPlayedAlbums(
                            period.rankingStart,
                            limit = 5,
                            toTimeStamp = period.endInclusive,
                        ).first().sortedByDescending { it.timeListened ?: 0L }
                    }
                    val songCount = async {
                        databaseDao.getUniqueSongCountInRange(period.start, period.endInclusive).first()
                    }
                    val artistCount = async {
                        databaseDao.getUniqueArtistCountInRange(period.start, period.endInclusive).first()
                    }
                    val albumCount = async {
                        databaseDao.getUniqueAlbumCountInRange(period.start, period.endInclusive).first()
                    }
                    val playTime = async {
                        databaseDao.getTotalPlayTimeInRange(period.start, period.endInclusive).first() ?: 0L
                    }
                    val topSongs = songs.await()
                    val topAlbums = albums.await()
                    loading.copy(
                        availableYears = years.await(),
                        topSongs = topSongs,
                        topArtists = artists.await(),
                        top5Albums = topAlbums,
                        topAlbum = topAlbums.firstOrNull(),
                        uniqueSongCount = songCount.await(),
                        uniqueArtistCount = artistCount.await(),
                        totalAlbums = albumCount.await(),
                        totalMinutes = playTime.await() / 60_000,
                        isLoading = false,
                        isDataReady = true,
                    )
                }
                _state.value = loaded
            } catch (e: CancellationException) {
                _state.update { it.copy(isLoading = false) }
                throw e
            } catch (e: Exception) {
                Timber.tag("WrappedManager").e(e, "Error preparing Wrapped for %d", year)
                _state.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }

    fun dispose() {
        job.cancel()
    }
}
