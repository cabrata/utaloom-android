/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import coil3.map.Mapper
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Scale
import com.metrolist.music.ui.utils.resize
import kotlin.math.roundToInt

internal class ArtworkUrlMapper : Mapper<String, String> {
    override fun map(data: String, options: Options): String {
        val width = (options.size.width as? Dimension.Pixels)?.px
        val height = (options.size.height as? Dimension.Pixels)?.px
        val side = maxOf(width ?: 0, height ?: 0)
        if (side == 0) return data

        // Coil has resolved the slot size here, so thumbnails need no extra subcomposition.
        val pixels = artworkSize(side)
        return if (options.scale == Scale.FILL && width != null && height != null) {
            data.resize(
                width = (width.toDouble() * pixels / side).roundToInt().coerceAtLeast(1),
                height = (height.toDouble() * pixels / side).roundToInt().coerceAtLeast(1),
            )
        } else if (width != null) {
            data.resize(width = artworkSize(width))
        } else {
            data.resize(height = artworkSize(height!!))
        }
    }
}

private val ARTWORK_SIZES = intArrayOf(160, 256, 480, 720, 1080, 1440, 2048)

internal fun artworkSize(pixels: Int): Int =
    ARTWORK_SIZES.firstOrNull { it >= pixels } ?: pixels
