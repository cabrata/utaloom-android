package com.metrolist.music.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.playback.MusicAddons
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.DefaultDialog

internal data class AudioQualityInfo(
    val tier: Tier,
    val codec: String,
    /** Add-on name, or null when the stream is YouTube Music's. */
    val addon: String?,
    val bitDepth: Int? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
) {
    enum class Tier { HI_RES, LOSSLESS, LOSSY }
}

private val LOSSLESS_CODECS = setOf("flac", "alac", "wav", "pcm", "ape", "aiff")

/** What is playing: the add-on stream when the song resolved to one, otherwise the stored YouTube format. */
internal fun audioQualityOf(addonStream: MusicAddons.Stream?, format: FormatEntity?): AudioQualityInfo? {
    if (addonStream != null) {
        val codec = addonStream.codec?.lowercase()
        val tier = when {
            codec !in LOSSLESS_CODECS -> AudioQualityInfo.Tier.LOSSY
            (addonStream.bitDepth ?: 0) > 16 || (addonStream.sampleRateHz ?: 0) > 48_000 -> AudioQualityInfo.Tier.HI_RES
            else -> AudioQualityInfo.Tier.LOSSLESS
        }
        return AudioQualityInfo(
            tier = tier,
            codec = codec?.uppercase() ?: addonStream.source,
            addon = addonStream.source,
            bitDepth = addonStream.bitDepth,
            sampleRateHz = addonStream.sampleRateHz,
            bitrateKbps = addonStream.bitrateKbps,
        )
    }
    format ?: return null
    val codec = when {
        format.codecs.startsWith("opus") -> "Opus"
        format.codecs.startsWith("mp4a") -> "AAC"
        format.codecs.isNotBlank() -> format.codecs.uppercase()
        else -> format.mimeType.substringAfter('/').uppercase()
    }
    return AudioQualityInfo(
        tier = AudioQualityInfo.Tier.LOSSY,
        codec = codec,
        addon = null,
        sampleRateHz = format.sampleRate,
        bitrateKbps = format.bitrate / 1000,
    )
}

@Composable
fun AudioQualityBadge(playerConnection: PlayerConnection, color: Color) {
    val mediaId = playerConnection.mediaMetadata.collectAsStateWithLifecycle().value?.id
    val addonStreams by playerConnection.addonStreams.collectAsStateWithLifecycle()
    val format by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)
    val info = audioQualityOf(addonStreams[mediaId], format) ?: return
    var showDetails by rememberSaveable { mutableStateOf(false) }

    val label = when (info.tier) {
        AudioQualityInfo.Tier.HI_RES -> stringResource(R.string.audio_quality_hires_lossless)
        AudioQualityInfo.Tier.LOSSLESS -> stringResource(R.string.audio_quality_lossless)
        AudioQualityInfo.Tier.LOSSY -> info.codec
    }
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.audio_quality)) { showDetails = true }
            .border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )

    if (showDetails) {
        DefaultDialog(
            onDismiss = { showDetails = false },
            title = { Text(stringResource(R.string.audio_quality)) },
            buttons = {
                TextButton(onClick = { showDetails = false }) { Text(stringResource(android.R.string.ok)) }
            },
        ) {
            listOfNotNull(
                stringResource(R.string.audio_quality_source) to
                    (info.addon?.let { stringResource(R.string.audio_quality_source_addon, it) } ?: "YouTube Music"),
                stringResource(R.string.audio_quality) to label,
                stringResource(R.string.codecs) to info.codec,
                info.bitDepth?.let { stringResource(R.string.audio_quality_bit_depth) to "$it-bit" },
                info.sampleRateHz?.let { stringResource(R.string.sample_rate) to "${(it / 1000.0).toString().removeSuffix(".0")} kHz" },
                info.bitrateKbps?.let { stringResource(R.string.bitrate) to "$it kbps" },
            ).forEach { (name, value) ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value)
                }
            }
        }
    }
}
