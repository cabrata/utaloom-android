/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor() : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)
    val artwork = MutableStateFlow<Map<Pair<String, String?>, String?>>(emptyMap())
    private val artworkRequests = mutableSetOf<Pair<String, String?>>()
    private val artworkLimiter = Semaphore(4)

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { YouTube.moodAndGenres() }
                .onSuccess {
                    moodAndGenres.value = it
                }.onFailure {
                    if (it is CancellationException) throw it
                    reportException(it)
                }
        }
    }

    fun loadArtwork(item: MoodAndGenres.Item) {
        val key = item.endpoint.browseId to item.endpoint.params
        if (!artworkRequests.add(key)) return
        viewModelScope.launch {
            try {
                artworkLimiter.withPermit {
                    withContext(Dispatchers.IO) {
                        YouTube.browse(item.endpoint.browseId, item.endpoint.params).map { page ->
                            page.items.asSequence()
                                .flatMap { it.items.asSequence() }
                                .mapNotNull { it.thumbnail?.takeIf(String::isNotBlank) }
                                .firstOrNull()
                        }
                    }
                }.onSuccess { thumbnail ->
                    artwork.update { it + (key to thumbnail) }
                }.onFailure {
                    artworkRequests.remove(key)
                    if (it is CancellationException) throw it
                    reportException(it)
                }
            } catch (cancelled: CancellationException) {
                artworkRequests.remove(key)
                throw cancelled
            }
        }
    }
}
