/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.music.constants.EnableYouTubeSubtitleLyricsKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableYouTubeSubtitleLyricsKey] ?: true

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.transcript(id)
}
