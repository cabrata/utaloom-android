package com.metrolist.music.ui.player

import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewSlotTest {
    private val lines = listOf(
        LyricsEntry(0L, ""),
        LyricsEntry(5_000L, "first", listOf(WordTimestamp("first", 5.0, 6.0))),
        LyricsEntry(6_500L, "second", listOf(WordTimestamp("second", 6.5, 7.0))),
        LyricsEntry(20_000L, "third"),
        LyricsEntry(25_000L, ""),
        LyricsEntry(30_000L, "fourth"),
    )

    @Test
    fun beforeFirstSungLineIsIntro() {
        assertEquals(PREVIEW_INTRO, previewSlot(lines, 1_000L))
        assertEquals(PREVIEW_INTRO, previewSlot(lines, 4_800L))
    }

    @Test
    fun sungLinesShowTheirIndex() {
        assertEquals(1, previewSlot(lines, 5_500L))
        // A short pause after a line keeps that line up.
        assertEquals(1, previewSlot(lines, 6_200L))
    }

    @Test
    fun longBreakAfterWordTimedLineIsInstrumental() {
        assertEquals(-2 - 2, previewSlot(lines, 10_000L))
    }

    @Test
    fun blankLrcLineIsInstrumental() {
        assertEquals(-2 - 4, previewSlot(lines, 26_000L))
        assertEquals(5, previewSlot(lines, 30_500L))
    }

    @Test
    fun noSungLinesIsIntro() {
        assertEquals(PREVIEW_INTRO, previewSlot(listOf(LyricsEntry(0L, "")), 1_000L))
    }
}
