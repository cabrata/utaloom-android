/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import com.metrolist.music.lyrics.WordTimestamp
import org.junit.Test
import org.junit.Assert.assertEquals

class LyricsCommonTest {

    @Test
    fun testSungCharCount_EmptyText() {
        val words = listOf(
            WordTimestamp("hello", 0.0, 0.5, true),
            WordTimestamp("world", 0.5, 1.0, false)
        )
        assertEquals(0, sungChars("", words, 500).toInt())
    }

    @Test
    fun testSungCharCount_EmptyWords() {
        assertEquals(0, sungChars("konnichiwa", emptyList(), 500).toInt())
    }

    @Test
    fun testSungCharCount_BeforeLineStart() {
        val words = listOf(WordTimestamp("こんにちは", 1.0, 2.0, false))
        // Before line starts (1000 ms)
        assertEquals(-1, sungChars("konnichiwa", words, 500).toInt())
    }

    @Test
    fun testSungCharCount_AtLineStart() {
        val words = listOf(WordTimestamp("こんにちは", 1.0, 2.0, false))
        // At exact line start
        assertEquals(0, sungChars("konnichiwa", words, 1000).toInt())
    }

    @Test
    fun testSungCharCount_MidProgress() {
        val words = listOf(WordTimestamp("こんにちは", 0.0, 1.0, false))
        val romanizedText = "konnichiwa" // 10 characters
        // At 500ms (middle of 0-1000ms line)
        val charIndex = sungChars(romanizedText, words, 500).toInt()
        assertEquals(5, charIndex) // Should be at middle character
    }

    @Test
    fun testSungCharCount_AfterLineEnd() {
        val words = listOf(WordTimestamp("こんにちは", 0.0, 1.0, false))
        val romanizedText = "konnichiwa"
        // After line ends (1000 ms)
        assertEquals(romanizedText.length, sungChars(romanizedText, words, 1500).toInt())
    }

    @Test
    fun testSungCharCount_MultipleWords() {
        val words = listOf(
            WordTimestamp("こんにちは", 0.0, 1.0, true),
            WordTimestamp("世界", 1.0, 2.0, false)
        )
        val romanizedText = "konnichiwa sekai"
        // One token per word: halfway through the first word is halfway through "konnichiwa".
        assertEquals(5, sungChars(romanizedText, words, 500).toInt())
        // The first word ends with its token fully lit; the second token starts after the space.
        assertEquals(10, sungChars(romanizedText, words, 1000).toInt())
        assertEquals(13, sungChars(romanizedText, words, 1500).toInt())
    }

    @Test
    fun testSungCharCount_PauseBetweenWordsHoldsWipe() {
        val words = listOf(
            WordTimestamp("ありがとう", 0.0, 1.0, false),
            WordTimestamp("ね", 3.0, 4.0, false),
        )
        // Token count differs from word count, so progress follows sung time only.
        val romanizedText = "ari gatou ne"
        val atEndOfFirst = sungChars(romanizedText, words, 1000).toInt()
        assertEquals(atEndOfFirst, sungChars(romanizedText, words, 2500).toInt())
    }

    @Test
    fun testSungCharCount_ClampedToTextLength() {
        val words = listOf(WordTimestamp("こんにちは", 0.0, 1.0, false))
        val romanizedText = "konnichiwa"
        // Way past the end
        assertEquals(romanizedText.length, sungChars(romanizedText, words, 5000).toInt())
    }

    @Test
    fun testSungChars_GlidesWithinACharacter() {
        val words = listOf(WordTimestamp("こんにちは", 0.0, 1.0, false))
        // 55% through a 10-character token is 5.5 characters, not a step to 5.
        assertEquals(5.5, sungChars("konnichiwa", words, 550), 1e-9)
    }

    @Test
    fun testIsRtlText() {
        assertEquals(true, isRtlText("يا حبيبي، يا شفيعي، يا رسول الله"))
        assertEquals(true, isRtlText("(صلّى الله على خاتَمِ الأنبياء)"))
        assertEquals(true, isRtlText("Ya يا حبيبي"))
        assertEquals(false, isRtlText("Ya Habibi, Ya Muhammad"))
        assertEquals(false, isRtlText(""))
    }
}
