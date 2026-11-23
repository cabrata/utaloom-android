/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.datastore.preferences.core.Preferences
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.constants.AudioQualityCellularKey
import com.metrolist.music.constants.AudioQualityKey
import com.metrolist.music.constants.AudioQualityWifiKey

/**
 * What a connection may stream, best first, as BitChord's ceilings do: [LOSSLESS] lets an add-on replace the
 * YouTube stream, [HIGH] is YouTube's best on its own, [LOW] is its smallest.
 */
enum class StreamQuality { LOSSLESS, HIGH, LOW }

/**
 * The streaming ceiling for each kind of connection. A connection nobody has chosen for follows [legacy], the
 * single setting from before, so nobody's quality changes until they pick one: Auto still means "best on Wi-Fi,
 * low on a metered connection", and add-ons keep upgrading.
 */
data class QualityPrefs(
    val legacy: AudioQuality = AudioQuality.AUTO,
    val wifi: StreamQuality? = null,
    val cellular: StreamQuality? = null,
) {
    private fun chosen(metered: Boolean) = if (metered) cellular else wifi

    /** What to ask YouTube for on the connection that is up. */
    fun resolve(metered: Boolean): AudioQuality = chosen(metered)?.let {
        if (it == StreamQuality.LOW) AudioQuality.LOW else AudioQuality.HIGH
    } ?: legacy

    /** Whether an add-on may replace the stream: only a connection set to Lossless, or one nobody has chosen for. */
    fun allowsAddons(metered: Boolean): Boolean = chosen(metered)?.let { it == StreamQuality.LOSSLESS } ?: true

    /** The ceiling a connection is at, for showing: its own choice, else what the legacy setting amounted to. */
    fun effective(wifi: Boolean): StreamQuality = (if (wifi) this.wifi else cellular) ?: when (legacy) {
        AudioQuality.AUTO -> if (wifi) StreamQuality.LOSSLESS else StreamQuality.LOW
        AudioQuality.HIGH -> StreamQuality.LOSSLESS
        AudioQuality.LOW -> StreamQuality.LOW
    }

    companion object {
        fun from(prefs: Preferences) = QualityPrefs(
            legacy = prefs[AudioQualityKey].let { value ->
                if (value == "VERY_HIGH") AudioQuality.HIGH else AudioQuality.entries.find { it.name == value }
            } ?: AudioQuality.AUTO,
            wifi = StreamQuality.entries.find { it.name == prefs[AudioQualityWifiKey] },
            cellular = StreamQuality.entries.find { it.name == prefs[AudioQualityCellularKey] },
        )
    }
}
