/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    private val _stations = MutableStateFlow<List<Song>?>(null)
    val stations: StateFlow<List<Song>?> = _stations

    private val _artistStations = MutableStateFlow<List<Artist>?>(null)
    val artistStations: StateFlow<List<Artist>?> = _artistStations

    private val _lastPlayed = MutableStateFlow<Song?>(null)
    val lastPlayed: StateFlow<Song?> = _lastPlayed

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val fromTimeStamp = LocalDateTime.now().minusDays(30)
            val toTimeStamp = LocalDateTime.now()

            // Load most played songs as stations
            val songs = database.mostPlayedSongs(
                fromTimeStamp = fromTimeStamp,
                limit = 12,
                offset = 0,
                toTimeStamp = toTimeStamp
            ).distinctUntilChanged().first()
            _stations.value = songs

            // Load most played artists
            val artists = database.mostPlayedArtists(
                fromTimeStamp = fromTimeStamp,
                limit = 10,
                toTimeStamp = toTimeStamp
            ).distinctUntilChanged().first()
            _artistStations.value = artists

            // Load last played song
            val lastEvent = database.latestEvent().distinctUntilChanged().first()
            _lastPlayed.value = lastEvent?.song
        }
    }
}
