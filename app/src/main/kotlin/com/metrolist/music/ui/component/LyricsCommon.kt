/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import kotlin.math.floor
import kotlin.math.sin
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.WordTimestamp

sealed class LyricsListItem {
    data class Line(val index: Int, val entry: LyricsEntry) : LyricsListItem()
    data class Indicator(
        val afterLineIndex: Int,
        val gapMs: Long,
        val gapStartMs: Long,
        val gapEndMs: Long,
        val nextAgent: String?
    ) : LyricsListItem()
}


/** Apple Music style interlude: three breathing dots that fill left to right across the gap. */
@Composable
internal fun IntervalIndicator(
    gapStartMs: Long,
    gapEndMs: Long,
    currentPositionMs: () -> Long,
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(visible) { appear.animateTo(if (visible) 1f else 0f, tween(400)) }

    Canvas(
        modifier
            .height(56.dp * appear.value)
            .graphicsLayer { alpha = appear.value }
    ) {
        if (appear.value <= 0f) return@Canvas
        // Read the position while drawing so each frame only redraws, never recomposes.
        val position = currentPositionMs()
        val progress = if (gapEndMs > gapStartMs) {
            ((position - gapStartMs).toFloat() / (gapEndMs - gapStartMs)).coerceIn(0f, 1f)
        } else 0f
        val breathe = 1f + 0.12f * sin(position / 250f)
        val r = 7.dp.toPx() * breathe * appear.value
        val step = 24.dp.toPx()
        // Matches the lyric column's leading edge, like the reference renderer.
        repeat(3) { k ->
            val fill = (progress * 3 - k).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = 0.3f + 0.7f * fill),
                radius = r,
                center = Offset(9.dp.toPx() + k * step, size.height / 2),
            )
        }
    }
}

private val RomanizedToken = Regex("\\S+")

/**
 * How many characters of [text] (a lyric line or its romanization) have been sung at [currentPositionMs],
 * as a fraction so the wipe glides through a character instead of stepping; -1 before the line starts.
 * When the text has one token per timed word, each token follows its own word. Otherwise the characters
 * advance only while a word is being sung, so pauses between words hold the wipe still.
 */
internal fun sungChars(
    text: String,
    words: List<WordTimestamp>,
    currentPositionMs: Long,
): Double {
    if (text.isEmpty() || words.isEmpty()) return 0.0
    val position = currentPositionMs.toDouble()
    if (position < words.first().startTime * 1000) return -1.0
    if (position >= words.last().endTime * 1000) return text.length.toDouble()

    val tokens = RomanizedToken.findAll(text).map { it.range }.toList()
    if (tokens.size == words.size) {
        var lit = 0.0
        for ((i, word) in words.withIndex()) {
            val start = word.startTime * 1000
            val end = word.endTime * 1000
            val token = tokens[i]
            when {
                position >= end -> lit = token.last + 1.0
                position > start -> return token.first + (token.last + 1 - token.first) * (position - start) / (end - start)
                else -> return lit
            }
        }
        return lit
    }

    var total = 0.0
    var sung = 0.0
    for (word in words) {
        val start = word.startTime * 1000
        val end = maxOf(word.endTime * 1000, start + 1)
        total += end - start
        sung += position.coerceIn(start, end) - start
    }
    return (text.length * sung / total).coerceIn(0.0, text.length.toDouble())
}

/**
 * Draws [layout] lit up to [sung] characters with a [feather]-wide gradient edge, row by row, so the colour
 * change travels smoothly across the text (BitChord's feathered sweep). One measured layout, no per-frame text work.
 */
internal fun DrawScope.drawSungText(
    layout: TextLayoutResult,
    sung: Double,
    lit: Color,
    pending: Color,
    feather: Float,
) {
    if (sung <= 0.0) {
        drawText(layout, color = pending)
        return
    }
    for (row in 0 until layout.lineCount) {
        val start = layout.getLineStart(row)
        val end = layout.getLineEnd(row, visibleEnd = true)
        val brush = when {
            sung >= end -> SolidColor(lit)
            sung <= start || end <= start -> SolidColor(pending)
            else -> {
                val i = floor(sung).toInt().coerceIn(start, end - 1)
                val x0 = layout.getHorizontalPosition(i, usePrimaryDirection = true)
                val x1 = layout.getHorizontalPosition(i + 1, usePrimaryDirection = true)
                val x = x0 + (x1 - x0) * (sung - i).toFloat()
                // RTL rows sweep the other way, so the lit side of the gradient follows the text direction.
                val rtl = layout.getParagraphDirection(i) == ResolvedTextDirection.Rtl
                Brush.horizontalGradient(
                    0f to (if (rtl) pending else lit),
                    1f to (if (rtl) lit else pending),
                    startX = x - feather,
                    endX = x + feather,
                )
            }
        }
        clipRect(0f, layout.getLineTop(row), size.width, layout.getLineBottom(row)) {
            drawText(layout, brush = brush)
        }
    }
}
