/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// Tall condensed numerals fit one line at large sizes; extended faces get squashed flat by autosize.
internal val WrappedDisplayFont = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Black))

object WrappedConstants {
    val YEAR: Int
        get() = LocalDate.now().year
    val PLAYLIST_NAME: String
        get() = "Utaloom $YEAR"
}

internal data class WrappedPeriod(
    val start: LocalDateTime,
    val endInclusive: LocalDateTime,
) {
    // Ranking queries use >, while count queries use >=. Room stores whole milliseconds.
    val rankingStart: LocalDateTime
        get() = start.minus(1, ChronoUnit.MILLIS)
}

internal fun wrappedPeriod(year: Int, now: LocalDateTime = LocalDateTime.now()): WrappedPeriod {
    require(year in 1..now.year) { "Wrapped year must not be in the future" }
    val start = LocalDate.of(year, 1, 1).atStartOfDay()
    val end = if (year == now.year) now else start.plusYears(1).minus(1, ChronoUnit.MILLIS)
    return WrappedPeriod(start, end.truncatedTo(ChronoUnit.MILLIS))
}
