/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import timber.log.Timber

/**
 * Whether this device can decode Dolby Atmos (E-AC-3 JOC). The decoder is licensed and ships with
 * the vendor image, so many phones lack it; an Atmos stream there skips or fails instead of playing.
 */
object DeviceCodecs {
    // A plain E-AC-3 decoder still plays a JOC stream (as its 5.1 core), so either one is enough.
    private val DOLBY_MIMES = listOf("audio/eac3-joc", "audio/eac3")

    /** Asked once per process: the codec list cannot change while the app runs. */
    val playsDolbyAtmos: Boolean by lazy { probe() }

    // An unreadable codec list says yes: a false no would hide Atmos on a capable phone.
    @OptIn(UnstableApi::class)
    private fun probe(): Boolean {
        val decoders = runCatching {
            DOLBY_MIMES.flatMap { MediaCodecUtil.getDecoderInfos(it, false, false) }.map { it.name }
        }.recoverCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info -> !info.isEncoder && info.supportedTypes.any { it.lowercase() in DOLBY_MIMES } }
                .map { it.name }
        }.getOrNull()
        Timber.tag("DeviceCodecs").d("Dolby Atmos decoders: %s", decoders ?: "unknown")
        return decoders?.isNotEmpty() ?: true
    }
}
