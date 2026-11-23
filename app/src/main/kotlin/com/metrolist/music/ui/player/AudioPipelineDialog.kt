package com.metrolist.music.ui.player

import android.animation.ValueAnimator
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.audio.AudioSink
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import java.util.Locale
import kotlin.math.abs

private val CardColor = Color(0xFF1C1C1E)
private val NodeColor = Color(0xFF131315)

/** The current item's quality and its header label, from what the renderer actually decoded. */
@Composable
internal fun rememberPipelineQuality(playerConnection: PlayerConnection): Pair<AudioQualityInfo, String>? {
    val mediaId = playerConnection.mediaMetadata.collectAsStateWithLifecycle().value?.id
    val addonStreams by playerConnection.addonStreams.collectAsStateWithLifecycle()
    val format by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)
    val player = playerConnection.player
    var trackFormat by remember(player) {
        mutableStateOf(player.currentMediaItem?.mediaId to player.pipelinePlayedAudioFormat())
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TRACKS_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    trackFormat = player.currentMediaItem?.mediaId to player.pipelinePlayedAudioFormat()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val played = trackFormat.takeIf { it.first == mediaId }?.second?.let { track ->
        PlayedAudio(
            bitDepth = pcmBits(track.pcmEncoding),
            sampleRateHz = track.sampleRate.takeIf { it > 0 },
            bitrateKbps = track.bitrate.div(1000).takeIf { it > 0 },
            mimeType = track.sampleMimeType,
        )
    }
    val info = audioQualityOf(addonStreams[mediaId], format?.takeIf { it.id == mediaId }, played) ?: return null
    val bitrate = info.bitrateKbps?.let { kbps -> stringResource(R.string.audio_quality_bitrate_value, kbps) }
    val label = when (info.tier) {
        AudioQualityInfo.Tier.DOLBY_ATMOS -> stringResource(R.string.audio_quality_dolby_atmos)
        AudioQualityInfo.Tier.HI_RES -> stringResource(R.string.audio_quality_hires_lossless)
        AudioQualityInfo.Tier.LOSSLESS -> stringResource(R.string.audio_quality_lossless)
        AudioQualityInfo.Tier.HIGH_QUALITY -> stringResource(R.string.audio_quality_high_quality)
        AudioQualityInfo.Tier.LOSSY -> when {
            info.codec.isNotEmpty() && bitrate != null -> stringResource(R.string.audio_quality_codec_bitrate, info.codec, bitrate)
            info.codec.isNotEmpty() -> info.codec
            else -> bitrate ?: stringResource(R.string.audio_quality)
        }
    }
    return info to label
}

private fun Player.pipelinePlayedAudioFormat(): Format? = currentTracks.groups
    .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
    ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat) }

/** The pipeline in its own window, for callers outside the Liquid Glass player. */
@Composable
internal fun AudioPipelineDialog(
    playerConnection: PlayerConnection,
    info: AudioQualityInfo,
    qualityLabel: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AudioPipelineCard(
            playerConnection = playerConnection,
            info = info,
            qualityLabel = qualityLabel,
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(24.dp)
                .width(320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardColor),
        )
    }
}

/**
 * BitChord's Audio Pipeline: the live signal path, source to output, with a pulse running down the
 * stages while music plays. Everything shown is what the renderer reported for the current player,
 * not what was asked for. [modifier] supplies the card's material.
 */
@Composable
internal fun AudioPipelineCard(
    playerConnection: PlayerConnection,
    info: AudioQualityInfo,
    qualityLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val service = playerConnection.service
    val player = playerConnection.player
    val pipelines by service.audioPipeline.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaId = playerConnection.mediaMetadata.collectAsStateWithLifecycle().value?.id
    val format by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)
    val eqProfile by service.eqProfileRepository.activeProfile.collectAsStateWithLifecycle()
    val devices by playerConnection.audioOutputDevices.collectAsStateWithLifecycle()
    val selectedDeviceId by playerConnection.selectedAudioDeviceId.collectAsStateWithLifecycle()
    val device = remember(devices, selectedDeviceId) { activeOutputDevice(context, devices, selectedDeviceId) }
    val pipeline = pipelines[player]
    val input = pipeline?.inputFormat
    val track = pipeline?.track
    val mixerRateHz = remember(context) {
        context.getSystemService(AudioManager::class.java)
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    }

    val dash = "—"
    val inRate = input?.sampleRate?.takeIf { it > 0 } ?: info.sampleRateHz
    val outRate = track?.sampleRate?.takeIf { it > 0 }
    val channelCount = input?.channelCount?.takeIf { it > 0 }
    val offload = track?.offload == true
    val gainDb = service.normalizationGainDb(player).takeUnless { offload }
    val eqActive = service.equalizerActive(player) && !offload
    val params = player.playbackParameters
    val sonic = params.speed != 1f || params.pitch != 1f
    val resampled = inRate != null && outRate != null && inRate != outRate
    val lossless = info.tier == AudioQualityInfo.Tier.LOSSLESS || info.tier == AudioQualityInfo.Tier.HI_RES
    val trackBits = track?.takeUnless { offload }?.let { pcmBits(it.encoding) }
    val pcmKbps = if (inRate != null && trackBits != null) inRate.toLong() * trackBits * (channelCount ?: 2) / 1000 else null
    val loudnessDb = format?.takeIf { it.id == mediaId }?.let { it.perceptualLoudnessDb ?: it.loudnessDb }
    val mixerResamples = !offload && mixerRateHz != null && outRate != null && mixerRateHz != outRate
    val directSupport = remember(track) { track?.let(::directPlaybackSupport) }
    val channels = when (channelCount) {
        null -> dash
        1 -> stringResource(R.string.pipeline_mono)
        2 -> stringResource(R.string.pipeline_stereo)
        else -> stringResource(R.string.pipeline_surround, channelCount)
    }
    val off = stringResource(R.string.pipeline_off)
    val bitExact = when {
        !lossless -> stringResource(R.string.pipeline_exact_no, stringResource(R.string.pipeline_reason_lossy))
        gainDb != null -> stringResource(R.string.pipeline_exact_no, stringResource(R.string.audio_normalization))
        eqActive -> stringResource(R.string.pipeline_exact_no, stringResource(R.string.equalizer))
        sonic -> stringResource(R.string.pipeline_exact_no, stringResource(R.string.tempo_and_pitch))
        resampled -> stringResource(R.string.pipeline_exact_no, stringResource(R.string.pipeline_resampler))
        mixerResamples -> stringResource(R.string.pipeline_exact_no, stringResource(R.string.pipeline_system_mixer))
        else -> stringResource(R.string.pipeline_exact_yes)
    }

    val stages = listOf(
        Stage(R.drawable.music_note, stringResource(R.string.pipeline_track_info)) {
            PipelineRow(
                stringResource(R.string.audio_quality_source),
                info.addon?.let { stringResource(R.string.audio_quality_source_addon, it) }
                    ?: stringResource(R.string.audio_quality_source_youtube),
            )
            PipelineRow(stringResource(R.string.audio_quality), qualityLabel)
            info.reportedQuality?.let { PipelineRow(stringResource(R.string.audio_quality_reported), it) }
            PipelineRow(stringResource(R.string.pipeline_format), info.codec.ifEmpty { dash })
            PipelineRow(
                stringResource(R.string.audio_quality_bit_depth),
                info.bitDepth?.takeIf { lossless }?.let { stringResource(R.string.audio_quality_bit_depth_value, it) } ?: dash,
            )
            PipelineRow(stringResource(R.string.sample_rate), inRate?.let(::rateText) ?: dash)
            PipelineRow(
                stringResource(R.string.bitrate),
                info.bitrateKbps?.let { stringResource(R.string.audio_quality_bitrate_value, it) } ?: dash,
            )
            PipelineRow(
                stringResource(R.string.pipeline_pcm_data_rate),
                pcmKbps?.let { stringResource(R.string.audio_quality_bitrate_value, it.toInt()) } ?: dash,
            )
            PipelineRow(stringResource(R.string.pipeline_channels), channels)
        },
        Stage(R.drawable.tune, stringResource(R.string.pipeline_decoder)) {
            PipelineRow(
                stringResource(R.string.pipeline_decoder_name),
                if (offload) stringResource(R.string.pipeline_offload) else pipeline?.decoderName ?: dash,
            )
            PipelineRow(stringResource(R.string.pipeline_format), input?.sampleMimeType ?: dash)
            input?.codecs?.takeIf { it.isNotBlank() }?.let { PipelineRow(stringResource(R.string.pipeline_codec_profile), it) }
            PipelineRow(stringResource(R.string.pipeline_pcm_format), track?.encoding?.let(::encodingText) ?: dash)
        },
        Stage(R.drawable.sync, stringResource(R.string.pipeline_resampler)) {
            PipelineRow(
                stringResource(R.string.pipeline_io_rate),
                "${inRate?.let(::rateText) ?: dash} → ${outRate?.let(::rateText) ?: dash}",
            )
            PipelineRow(
                stringResource(R.string.pipeline_type),
                when {
                    offload -> stringResource(R.string.pipeline_offload)
                    inRate == null || outRate == null -> dash
                    sonic -> stringResource(R.string.pipeline_sonic)
                    resampled -> stringResource(R.string.pipeline_resampler_framework)
                    else -> stringResource(R.string.pipeline_none)
                },
            )
            PipelineRow(
                stringResource(R.string.pipeline_quality),
                when {
                    inRate == null || outRate == null -> dash
                    resampled || sonic -> stringResource(R.string.pipeline_resampled)
                    else -> stringResource(R.string.pipeline_passthrough)
                },
            )
        },
        Stage(R.drawable.graphic_eq, stringResource(R.string.pipeline_dsp)) {
            PipelineRow(stringResource(R.string.pipeline_pcm_format), track?.takeUnless { offload }?.encoding?.let(::encodingText) ?: dash)
            PipelineRow(stringResource(R.string.sample_rate), outRate?.let(::rateText) ?: dash)
            PipelineRow(
                stringResource(R.string.audio_normalization),
                gainDb?.let { stringResource(R.string.pipeline_gain_db, "%+.1f".format(Locale.ROOT, it)) } ?: off,
            )
            PipelineRow(
                stringResource(R.string.pipeline_loudness_measured),
                loudnessDb?.let { stringResource(R.string.pipeline_gain_db, "%+.1f".format(Locale.ROOT, it)) } ?: dash,
            )
            PipelineRow(
                stringResource(R.string.equalizer),
                if (eqActive) eqProfile?.name ?: stringResource(R.string.pipeline_on) else off,
            )
            PipelineRow(
                stringResource(R.string.tempo_and_pitch),
                "%.2f× / %.2f×".format(Locale.ROOT, params.speed, params.pitch),
            )
            PipelineRow(
                stringResource(R.string.pipeline_output_api),
                when {
                    track == null -> dash
                    offload -> "AudioTrack (offload)"
                    track.tunneling -> "AudioTrack (tunneled)"
                    else -> "AudioTrack"
                },
            )
            PipelineRow(stringResource(R.string.pipeline_bit_exact), bitExact)
        },
        Stage(R.drawable.volume_up, stringResource(R.string.pipeline_output_device)) {
            PipelineRow(
                stringResource(R.string.pipeline_device_name),
                device?.let { outputDeviceName(it, context) } ?: stringResource(R.string.pipeline_system_default),
            )
            device?.let { PipelineRow(stringResource(R.string.pipeline_route), routeText(it, context)) }
            PipelineRow(
                stringResource(R.string.pipeline_direct),
                when {
                    offload -> stringResource(R.string.pipeline_direct_offload)
                    directSupport == null -> dash
                    directSupport -> stringResource(R.string.pipeline_direct_supported)
                    else -> stringResource(R.string.pipeline_direct_unsupported)
                },
            )
            PipelineRow(
                stringResource(R.string.pipeline_audiotrack),
                track?.let { "${encodingText(it.encoding)} / ${rateText(it.sampleRate)}" } ?: dash,
            )
            PipelineRow(
                stringResource(R.string.pipeline_buffers),
                track?.let { bufferText(it.bufferSize, it.encoding, channelCount ?: 2, it.sampleRate) } ?: dash,
            )
            PipelineRow(
                stringResource(R.string.pipeline_system_mixer),
                if (offload) stringResource(R.string.pipeline_offload) else mixerRateHz?.let(::rateText) ?: dash,
            )
            device?.let(::capabilityText)?.let { PipelineRow(stringResource(R.string.pipeline_device_capability), it) }
        },
    )

    val animate = isPlaying && ValueAnimator.areAnimatorsEnabled()
    val pulse = if (animate) {
        rememberInfiniteTransition(label = "pipelinePulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "pipelinePulseProgress",
        )
    } else {
        null
    }
    val centers = remember { mutableStateMapOf<Int, Offset>() }
    var column by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 19.dp),
        ) {
            Text(
                stringResource(R.string.audio_pipeline),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.audio_pipeline_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .onGloballyPositioned { column = it }
                .drawBehind { drawSignalFlow(centers, pulse?.value) },
        ) {
            stages.forEachIndexed { index, stage ->
                PipelineRule()
                PipelineSection(
                    icon = stage.icon,
                    title = stage.title,
                    activation = { pulse?.value?.let { stageActivation(index, it, centers) } ?: 0f },
                    onIconPositioned = { coords ->
                        val parent = column ?: return@PipelineSection
                        if (!coords.isAttached || !parent.isAttached) return@PipelineSection
                        val center = parent.localPositionOf(coords, Offset(coords.size.width / 2f, coords.size.height / 2f))
                        if (centers[index] != center) centers[index] = center
                    },
                    content = stage.rows,
                )
            }
        }
        PipelineRule(Modifier)
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.pipeline_done),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun pcmBits(encoding: Int): Int? = when (encoding) {
    C.ENCODING_PCM_16BIT -> 16
    C.ENCODING_PCM_24BIT -> 24
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
    else -> null
}

/** Whether Android could play this AudioTrack format without the mixer (API 33+), else null. */
private fun directPlaybackSupport(track: AudioSink.AudioTrackConfig): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching {
        val format = AudioFormat.Builder()
            .setEncoding(track.encoding)
            .setSampleRate(track.sampleRate)
            .setChannelMask(track.channelConfig)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        AudioManager.getDirectPlaybackSupport(format, attributes) != AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED
    }.getOrNull()
}

/** The output's kind, with the Bluetooth transport where it is known. */
private fun routeText(device: AudioDeviceInfo, context: android.content.Context): String {
    val kind = context.getString(
        when (outputKind(device.type)) {
            OutputKind.PHONE -> R.string.audio_output_phone
            OutputKind.WIRED -> R.string.audio_output_wired
            OutputKind.USB -> R.string.audio_output_usb
            OutputKind.BLUETOOTH -> R.string.audio_output_bluetooth
            OutputKind.HDMI -> R.string.audio_output_hdmi
            null -> R.string.audio_output_system
        },
    )
    val transport = when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> "LE Audio"
        AudioDeviceInfo.TYPE_HEARING_AID -> "ASHA"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "ARC"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "eARC"
        else -> null
    }
    return transport?.let { "$kind ($it)" } ?: kind
}

/** What a USB DAC or HDMI sink says it accepts; the phone's own outputs report nothing useful. */
private fun capabilityText(device: AudioDeviceInfo): String? {
    val kind = outputKind(device.type)
    if (kind != OutputKind.USB && kind != OutputKind.HDMI) return null
    val encodings = device.encodings.toList().mapNotNull { enc -> pcmBits(enc)?.let { encodingText(enc) } }.distinct()
    val rates = device.sampleRates.filter { it > 0 }.sorted()
    val rateRange = when {
        rates.isEmpty() -> null
        rates.size == 1 -> rateText(rates.first())
        else -> "${rateText(rates.first())}–${rateText(rates.last())}"
    }
    return listOfNotNull(encodings.joinToString("/").takeIf { it.isNotEmpty() }, rateRange)
        .joinToString(" · ")
        .takeIf { it.isNotEmpty() }
}

private class Stage(@DrawableRes val icon: Int, val title: String, val rows: @Composable () -> Unit)

/** The bus between the stage icons, the pulse travelling down it, and a ring per node. */
private fun DrawScope.drawSignalFlow(centers: Map<Int, Offset>, pulse: Float?) {
    val points = (0 until 5).map { centers[it] ?: return }
    val x = points.first().x
    val node = 11.5.dp.toPx()
    val pulseY = pulse?.let { points.first().y + (it / 0.85f).coerceAtMost(1f) * (points.last().y - points.first().y) }
    val visibility = pulse?.let { if (it <= 0.85f) 1f else (1f - (it - 0.85f) / 0.1f).coerceIn(0f, 1f) } ?: 0f
    val tail = 38.dp.toPx()

    for (i in 0 until points.lastIndex) {
        val top = points[i].y + node
        val bottom = points[i + 1].y - node
        if (bottom <= top) continue
        drawLine(Color.White.copy(alpha = if (pulse != null) 0.26f else 0.18f), Offset(x, top), Offset(x, bottom), 2.dp.toPx())
        if (pulseY == null || visibility <= 0.01f) continue
        val start = maxOf(top, pulseY - tail)
        val end = minOf(bottom, pulseY + 8.dp.toPx())
        if (end <= start) continue
        drawLine(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.86f to Color.White.copy(alpha = 0.95f * visibility),
                1f to Color.Transparent,
                startY = pulseY - tail,
                endY = pulseY + 8.dp.toPx(),
            ),
            Offset(x, start),
            Offset(x, end),
            2.25.dp.toPx(),
        )
        if (pulseY in top..bottom) {
            drawCircle(Color.White.copy(alpha = 0.3f * visibility), 6.dp.toPx(), Offset(x, pulseY))
            drawCircle(Color.White.copy(alpha = visibility), 1.75.dp.toPx(), Offset(x, pulseY))
        }
    }
    points.forEachIndexed { i, point ->
        val act = pulse?.let { stageActivation(i, it, centers) } ?: 0f
        val center = Offset(x, point.y)
        drawCircle(NodeColor, node, center)
        drawCircle(Color.White.copy(alpha = 0.35f + 0.55f * act), node, center, style = Stroke(1.dp.toPx()))
        if (act > 0.02f) {
            drawCircle(Color.White.copy(alpha = 0.2f * act), node + 1.2.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
    }
}

/** How lit stage [index] is, 0..1, as the pulse passes its icon. */
private fun stageActivation(index: Int, progress: Float, centers: Map<Int, Offset>): Float {
    val first = centers[0]?.y ?: return 0f
    val last = centers[4]?.y ?: return 0f
    val y = centers[index]?.y ?: return 0f
    val pulseY = first + (progress / 0.85f).coerceAtMost(1f) * (last - first)
    val radius = 72f
    val d = abs(pulseY - y)
    if (d > radius) return 0f
    val fadeOut = if (index == 4 && progress > 0.85f) (1f - (progress - 0.85f) / 0.1f).coerceIn(0f, 1f) else 1f
    return (0.5f - 0.5f * kotlin.math.cos((1f - d / radius) * Math.PI.toFloat())) * fadeOut
}

@Composable
private fun PipelineSection(
    @DrawableRes icon: Int,
    title: String,
    activation: () -> Float,
    onIconPositioned: (LayoutCoordinates) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(15.dp)
                    .onGloballyPositioned(onIconPositioned)
                    .graphicsLayerAlpha { 0.8f + 0.2f * activation() },
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.W600,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 3.dp).graphicsLayerAlpha { 0.65f + 0.32f * activation() },
            )
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

private fun Modifier.graphicsLayerAlpha(alpha: () -> Float) = graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }

@Composable
private fun PipelineRow(label: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)) { append("$label: ") }
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.82f))) { append(value) }
        },
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Composable
private fun PipelineRule(modifier: Modifier = Modifier.padding(start = 52.dp, end = 16.dp)) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
}

/** 44100 -> "44.1 kHz". */
internal fun rateText(hz: Int): String = "${"%.1f".format(Locale.ROOT, hz / 1000f).removeSuffix(".0")} kHz"

internal fun encodingText(encoding: Int): String = when (encoding) {
    C.ENCODING_PCM_16BIT -> "PCM16"
    C.ENCODING_PCM_24BIT -> "PCM24"
    C.ENCODING_PCM_32BIT -> "PCM32"
    C.ENCODING_PCM_FLOAT -> "Float32"
    C.ENCODING_OPUS -> "Opus"
    C.ENCODING_AAC_LC -> "AAC"
    C.ENCODING_MP3 -> "MP3"
    C.ENCODING_E_AC3, C.ENCODING_E_AC3_JOC -> "E-AC-3"
    else -> "#$encoding"
}

/** AudioTrack buffer as milliseconds and frames, or the raw size for compressed (offloaded) encodings. */
internal fun bufferText(bytes: Int, encoding: Int, channels: Int, sampleRate: Int): String {
    val bytesPerSample = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> return "${bytes / 1024} KiB"
    }
    val frames = bytes / (bytesPerSample * channels.coerceAtLeast(1))
    if (frames <= 0 || sampleRate <= 0) return "${bytes / 1024} KiB"
    return "${frames * 1000L / sampleRate} ms ($frames frames)"
}
