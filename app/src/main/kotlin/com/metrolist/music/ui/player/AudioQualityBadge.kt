package com.metrolist.music.ui.player

import android.animation.ValueAnimator
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import com.metrolist.music.R
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.playback.MusicAddons
import com.metrolist.music.playback.PlayerConnection

internal data class AudioQualityInfo(
    val tier: Tier,
    val codec: String,
    /** Add-on name, or null when the stream is YouTube Music's. */
    val addon: String?,
    val bitDepth: Int? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
    val reportedQuality: String? = null,
) {
    enum class Tier { DOLBY_ATMOS, HI_RES, LOSSLESS, HIGH_QUALITY, LOSSY }
}

/** Measured on the device from the track being decoded. */
internal data class PlayedAudio(
    val bitDepth: Int? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
    val mimeType: String? = null,
)

/** Average bitrate of a whole file, or null while its size or length is unknown. */
internal fun averageBitrateKbps(bytes: Long?, durationMs: Long): Int? =
    if (bytes != null && bytes > 0 && durationMs > 0) (bytes * 8 / durationMs).toInt() else null

private fun Player.playedAudioFormat(): Format? =
    currentTracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }

private val LOSSLESS_CODECS = setOf("flac", "alac", "wav", "pcm", "ape", "aiff")

private fun audioCodec(value: String?): String? = when (val codec = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }) {
    "opus", "audio/opus" -> "opus"
    "audio/aac", "audio/mp4a-latm" -> "aac"
    "audio/flac", "audio/x-flac" -> "flac"
    "audio/alac" -> "alac"
    "audio/wav", "audio/x-wav" -> "wav"
    "audio/raw" -> "pcm"
    "audio/mpeg" -> "mp3"
    "audio/eac3-joc", "ec+3", "eac3-joc", "eac3_joc", "ec3_joc", "ec3-joc" -> "eac3-joc"
    "audio/eac3", "ec-3", "eac3" -> "eac3"
    else -> codec?.let { if (it.startsWith("mp4a")) "aac" else it.substringAfter('/') }
}

internal val PlayedAudio.isPremiumFormat: Boolean
    get() = audioCodec(mimeType).let { it in LOSSLESS_CODECS || it == "eac3-joc" }

/** What is playing: the add-on stream when the song resolved to one, otherwise the stored YouTube format. */
internal fun audioQualityOf(
    addonStream: MusicAddons.Stream?,
    format: FormatEntity?,
    played: PlayedAudio? = null,
): AudioQualityInfo? {
    if (addonStream == null && format == null && played == null) return null

    val youtubeFormat = format.takeIf { addonStream == null }
    val codec = audioCodec(played?.mimeType ?: addonStream?.codec ?: youtubeFormat?.let {
        it.codecs.ifBlank { it.mimeType }
    })
    val bitDepth = played?.bitDepth ?: addonStream?.bitDepth?.takeIf { it > 0 }
    val sampleRateHz = played?.sampleRateHz ?: addonStream?.sampleRateHz?.takeIf { it > 0 }
        ?: youtubeFormat?.sampleRate?.takeIf { it > 0 }
    val bitrateKbps = played?.bitrateKbps ?: addonStream?.bitrateKbps?.takeIf { it > 0 }
        ?: youtubeFormat?.bitrate?.div(1000)?.takeIf { it > 0 }
    val tier = when {
        codec == "eac3-joc" -> AudioQualityInfo.Tier.DOLBY_ATMOS
        codec in LOSSLESS_CODECS ->
            if ((bitDepth ?: 0) > 16 || (sampleRateHz ?: 0) > 48_000) AudioQualityInfo.Tier.HI_RES
            else AudioQualityInfo.Tier.LOSSLESS
        (bitrateKbps ?: 0) >= 256 -> AudioQualityInfo.Tier.HIGH_QUALITY
        else -> AudioQualityInfo.Tier.LOSSY
    }
    return AudioQualityInfo(
        tier = tier,
        codec = when (codec) {
            "opus" -> "Opus"
            "eac3-joc" -> "E-AC-3 JOC"
            "eac3" -> "E-AC-3"
            else -> codec?.uppercase().orEmpty()
        },
        addon = addonStream?.source,
        bitDepth = bitDepth,
        sampleRateHz = sampleRateHz,
        bitrateKbps = bitrateKbps,
        reportedQuality = addonStream?.quality,
    )
}

private enum class QualityMotion { NONE, PULSE, SHIMMER }

private data class QualityLabel(
    @DrawableRes val icon: Int,
    val text: String,
    val motion: QualityMotion = QualityMotion.NONE,
)

@Composable
fun AudioQualityBadge(
    playerConnection: PlayerConnection,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val mediaId = playerConnection.mediaMetadata.collectAsStateWithLifecycle().value?.id
    val addonStreams by playerConnection.addonStreams.collectAsStateWithLifecycle()
    val format by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)
    val upgradingMediaId by playerConnection.service.upgradingAudioMediaId.collectAsStateWithLifecycle()
    var showDetails by rememberSaveable(mediaId) { mutableStateOf(false) }

    val player = playerConnection.player
    var trackFormat by remember(player) {
        mutableStateOf(player.currentMediaItem?.mediaId to player.playedAudioFormat())
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TRACKS_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    trackFormat = player.currentMediaItem?.mediaId to player.playedAudioFormat()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val played = trackFormat.takeIf { it.first == mediaId }?.second?.let { track ->
        PlayedAudio(
            bitDepth = when (track.pcmEncoding) {
                C.ENCODING_PCM_16BIT -> 16
                C.ENCODING_PCM_24BIT -> 24
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
                else -> null
            },
            sampleRateHz = track.sampleRate.takeIf { it > 0 },
            // Lossless tracks carry no bitrate; size over length is only needed once the details are open.
            bitrateKbps = track.bitrate.div(1000).takeIf { it > 0 }
                ?: if (showDetails && mediaId != null) {
                    averageBitrateKbps(playerConnection.service.addonStreamBytes(mediaId), player.duration)
                } else {
                    null
                },
            mimeType = track.sampleMimeType,
        )
    }
    val info = audioQualityOf(addonStreams[mediaId], format?.takeIf { it.id == mediaId }, played)
    val bitrate = info?.bitrateKbps?.let { stringResource(R.string.audio_quality_bitrate_value, it) }
    val current = info?.let {
        val premiumMotion = if (played?.isPremiumFormat == true) QualityMotion.SHIMMER else QualityMotion.NONE
        when (it.tier) {
            AudioQualityInfo.Tier.DOLBY_ATMOS -> QualityLabel(R.drawable.quality_atmos, stringResource(R.string.audio_quality_dolby_atmos), premiumMotion)
            AudioQualityInfo.Tier.HI_RES -> QualityLabel(R.drawable.quality_lossless, stringResource(R.string.audio_quality_hires_lossless), premiumMotion)
            AudioQualityInfo.Tier.LOSSLESS -> QualityLabel(R.drawable.quality_lossless, stringResource(R.string.audio_quality_lossless), premiumMotion)
            AudioQualityInfo.Tier.HIGH_QUALITY -> QualityLabel(R.drawable.quality_headphones, stringResource(R.string.audio_quality_high_quality))
            AudioQualityInfo.Tier.LOSSY -> QualityLabel(
                if (it.addon == null) R.drawable.quality_youtube_music else R.drawable.quality_headphones,
                when {
                    it.codec.isNotEmpty() && bitrate != null -> stringResource(R.string.audio_quality_codec_bitrate, it.codec, bitrate)
                    it.codec.isNotEmpty() -> it.codec
                    else -> bitrate ?: stringResource(R.string.audio_quality)
                },
            )
        }
    }
    val label = if (mediaId != null && upgradingMediaId == mediaId && info?.addon == null) {
        QualityLabel(R.drawable.quality_lossless, stringResource(R.string.audio_quality_upgrading), QualityMotion.PULSE)
    } else {
        current
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motionEnabled = animate && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled()
    Box(modifier, contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = label,
            animationSpec = tween(if (motionEnabled) 200 else 0),
            label = "Audio quality",
            modifier = Modifier
                .widthIn(max = 200.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = current != null, role = Role.Button, onClickLabel = stringResource(R.string.audio_quality)) {
                    showDetails = true
                },
        ) { visible ->
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (visible != null) {
                    Row(
                        modifier = qualityMotion(if (motionEnabled) visible.motion else QualityMotion.NONE, color),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(visible.icon), contentDescription = null, tint = color)
                        Text(
                            text = visible.text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showDetails && info != null && current != null) {
        AudioPipelineDialog(playerConnection, info, current.text, onDismiss = { showDetails = false })
    }
}

@Composable
private fun qualityMotion(motion: QualityMotion, color: Color): Modifier {
    if (motion == QualityMotion.NONE) return Modifier
    val pulsing = motion == QualityMotion.PULSE
    val progress = rememberInfiniteTransition(label = "Quality badge").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = if (pulsing) {
            infiniteRepeatable(tween(900), RepeatMode.Reverse)
        } else {
            infiniteRepeatable(tween(1800, delayMillis = 1000, easing = LinearEasing))
        },
        label = "Quality motion",
    )
    if (pulsing) return Modifier.graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = 1f - 0.28f * progress.value
    }

    // Clip the sweep to the logo and text without recomposing the label on every frame.
    val base = color.copy(alpha = color.alpha * 0.76f)
    return Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (size.width <= 0f) return@drawWithContent
            val halfBand = size.width * 0.35f
            val center = -halfBand + progress.value * (size.width + 2f * halfBand)
            drawRect(
                brush = Brush.horizontalGradient(listOf(base, color, base), center - halfBand, center + halfBand),
                blendMode = BlendMode.SrcIn,
            )
        }
}
