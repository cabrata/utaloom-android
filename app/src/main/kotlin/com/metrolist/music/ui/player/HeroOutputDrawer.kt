/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.AudioOutputExtraRows
import com.metrolist.music.ui.glass.LocalAppBackdrop
import com.metrolist.music.ui.glass.backdrop.Backdrop
import com.metrolist.music.ui.glass.backdrop.drawBackdrop
import com.metrolist.music.ui.glass.backdrop.effects.blur
import com.metrolist.music.ui.glass.backdrop.effects.colorControls
import kotlin.math.roundToInt

private val DrawerShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
private val PopupShape = RoundedCornerShape(14.dp)
private val RowShape = RoundedCornerShape(16.dp)
private val GlassFill = Color(0xFF1C1C1E)

/**
 * Opens the output drawer, asking for Bluetooth permission on the first tap. The permission only lets
 * the rows name paired headsets, so the drawer opens either way.
 */
@Composable
internal fun rememberAudioOutputPicker(onOpen: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onOpen() }
    return {
        if (bluetoothNamesAllowed(context)) onOpen() else ask.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }
}

private fun bluetoothNamesAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

/** Material 3 player: the headphones button and the output sheet it opens. */
@Composable
internal fun AudioOutputButton(tint: Color, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val openOutputs = rememberAudioOutputPicker { open = true }
    val glyph = rememberOutputGlyph()
    IconButton(onClick = openOutputs, modifier = modifier) {
        Icon(painterResource(glyph.res), stringResource(R.string.audio_output), tint = tint, modifier = Modifier.size(glyph.size))
    }
    if (open) AudioOutputSheet(onDismiss = { open = false })
}

/** The drawer's rows in a Material 3 sheet, coloured by the theme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioOutputSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var pipelineOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.audio_output),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
            )
            OutputDrawerBody(playerConnection, glass = false, onOpenPipeline = { pipelineOpen = true })
        }
    }
    if (pipelineOpen) {
        rememberPipelineQuality(playerConnection)?.let { (info, label) ->
            AudioPipelineDialog(playerConnection, info, label) { pipelineOpen = false }
        }
    }
}

/** Outputs, the flavor's extra rows (Cast), volume and the pipeline entry, shared by the drawer and the sheet. */
@Composable
private fun OutputDrawerBody(playerConnection: PlayerConnection, glass: Boolean, onOpenPipeline: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        AudioOutputDeviceRows(playerConnection)
        AudioOutputExtraRows()
        Spacer(Modifier.height(4.dp))
        OutputVolumeRow(playerConnection, glass)
        AudioPipelineRow(playerConnection, onClick = onOpenPipeline)
    }
}

/**
 * BitChord's output drawer for the Liquid Glass player: rises off the bottom edge over a scrim and drags
 * down to dismiss. With a [backdrop] of the player (Android 12+) it frosts the cover and controls behind
 * it as BitChord's menu does; otherwise it uses the cached cover blur. Drawn inside the player rather
 * than in a window so the frost sees what is behind it. The Audio Pipeline opens
 * over it as a card of the same material.
 */
@Composable
internal fun HeroOutputDrawer(visible: Boolean, thumbnailUrl: String?, backdrop: Backdrop?, onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var pipelineOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = visible) { if (pipelineOpen) pipelineOpen = false else onDismiss() }

    val frost = rememberHeroFrost(thumbnailUrl)
    var overlayPos by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var panelOffset by remember { mutableStateOf(IntOffset.Zero) }
    var drag by remember { mutableFloatStateOf(0f) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val dragOffset by animateFloatAsState(drag, spring(stiffness = Spring.StiffnessMediumLow), label = "outputDrawerDrag")
    LaunchedEffect(visible) {
        if (!visible) {
            pipelineOpen = false
            drag = 0f
        }
    }

    CompositionLocalProvider(LocalContentColor provides Color.White, LocalAppBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    overlayPos = it.positionInWindow()
                    overlaySize = it.size
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
                val fade = if (panelHeight > 0) (1f - dragOffset / panelHeight).coerceIn(0f, 1f) else 1f
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f * fade))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
                exit = slideOutVertically(tween(180)) { it },
            ) {
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .onSizeChanged { panelHeight = it.height }
                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                        .onGloballyPositioned { panelOffset = (it.positionInWindow() - overlayPos).round() }
                        .playerGlass(backdrop != null, DrawerShape, frost, { overlaySize }, { panelOffset })
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp),
                ) {
                    // Only the header drags: the rows below scroll and would swallow the gesture anyway.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = { if (panelHeight > 0 && drag > panelHeight * 0.25f) onDismiss() else drag = 0f },
                                    onDragCancel = { drag = 0f },
                                ) { _, delta -> drag = (drag + delta).coerceAtLeast(0f) }
                            }
                            .padding(top = 10.dp, bottom = 14.dp),
                    ) {
                        Box(
                            Modifier
                                .padding(bottom = 12.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                        )
                        Text(
                            text = stringResource(R.string.audio_output),
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        )
                    }
                    OutputDrawerBody(playerConnection, glass = true, onOpenPipeline = { pipelineOpen = true })
                }
            }
            if (pipelineOpen) {
                PipelinePopup(playerConnection, backdrop != null, frost, { overlaySize }, overlayPos) { pipelineOpen = false }
            }
        }
    }
}

/**
 * BitChord's menu frost (menuBackdropFrost): the player behind, blurred 24dp with its hue drawn up 1.8x,
 * under a charcoal slab at 80% and a hairline rim. No lens or highlight. Without a recorded backdrop
 * (below Android 12) the cached cover frost stands in.
 */
@Composable
private fun Modifier.playerGlass(
    liveGlass: Boolean,
    shape: RoundedCornerShape,
    frost: HeroFrost,
    overlaySize: () -> IntSize,
    offset: () -> IntOffset,
): Modifier {
    val backdrop = LocalAppBackdrop.current
    val material = if (liveGlass && backdrop != null) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(saturation = 1.8f)
                blur(24.dp.toPx() * FROST_SCALE)
            },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(GlassFill.copy(alpha = 0.80f)) },
            // Sampled small: the blur hides the upscale, and the effect runs over a fraction of the pixels.
            backdropScale = FROST_SCALE,
        ).clip(shape)
    } else {
        clip(shape).heroFrost(frost, overlaySize, offset)
    }
    return material.border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
}

private const val FROST_SCALE = 0.25f

/** The pipeline card centred over the player, in the drawer's material. */
@Composable
private fun PipelinePopup(
    playerConnection: PlayerConnection,
    liveGlass: Boolean,
    frost: HeroFrost,
    overlaySize: () -> IntSize,
    overlayPos: Offset,
    onDismiss: () -> Unit,
) {
    val (info, label) = rememberPipelineQuality(playerConnection) ?: return
    var cardOffset by remember { mutableStateOf(IntOffset.Zero) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AudioPipelineCard(
            playerConnection = playerConnection,
            info = info,
            qualityLabel = label,
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(24.dp)
                .width(320.dp)
                .onGloballyPositioned { cardOffset = (it.positionInWindow() - overlayPos).round() }
                .playerGlass(liveGlass, PopupShape, frost, overlaySize, { cardOffset })
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )
    }
}

@Composable
private fun AudioOutputDeviceRows(playerConnection: PlayerConnection) {
    val context = LocalContext.current
    val devices by playerConnection.audioOutputDevices.collectAsStateWithLifecycle()
    val selectedId by playerConnection.selectedAudioDeviceId.collectAsStateWithLifecycle()
    val isCasting by playerConnection.service.castConnectionHandler?.isCasting?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val activeId = if (isCasting) null else activeOutputDevice(context, devices, selectedId)?.id
    devices.forEach { device ->
        val active = device.id == activeId
        OutputRow(
            title = outputDeviceName(device, context),
            subtitle = if (active) stringResource(R.string.audio_output_playing) else null,
            iconRes = outputDeviceIcon(device, context),
            active = active,
            onClick = { playerConnection.selectAudioDevice(device.id) },
        )
    }
}

/** Cast volume while casting, the phone's media volume otherwise; the glass slider on glass, Material's elsewhere. */
@Composable
private fun OutputVolumeRow(playerConnection: PlayerConnection, glass: Boolean) {
    val cast = playerConnection.service.castConnectionHandler
    val isCasting by cast?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castVolume by cast?.castVolume?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(1f) }
    val media = rememberMediaVolume()
    if (!isCasting && media == null) return
    val content = LocalContentColor.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(content.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(R.drawable.volume_up), null, tint = content.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        val level = if (isCasting) castVolume else media?.level ?: 0f
        val onChange: (Float) -> Unit = {
            if (isCasting) {
                cast?.setVolume(it)
            } else {
                media?.dragging = true
                media?.set(it)
            }
        }
        val onFinished = { media?.dragging = false }
        val sliderModifier = Modifier.weight(1f).padding(start = 12.dp)
        if (glass) {
            ThinSlider(value = level, onValueChange = onChange, onValueChangeFinished = onFinished, modifier = sliderModifier)
        } else {
            Slider(value = level.coerceIn(0f, 1f), onValueChange = onChange, onValueChangeFinished = onFinished, modifier = sliderModifier)
        }
    }
}

/** Drills into the pipeline; the subtitle is what the AudioTrack was opened with, read live. */
@Composable
private fun AudioPipelineRow(playerConnection: PlayerConnection, onClick: () -> Unit) {
    val pipelines by playerConnection.service.audioPipeline.collectAsStateWithLifecycle()
    val track = pipelines[playerConnection.player]?.track
    OutputRow(
        title = stringResource(R.string.audio_pipeline),
        subtitle = when {
            track == null -> stringResource(R.string.audio_pipeline_subtitle)
            track.offload -> "${stringResource(R.string.pipeline_offload)} · ${rateText(track.sampleRate)}"
            else -> "${encodingText(track.encoding)} · ${rateText(track.sampleRate)}"
        },
        iconRes = R.drawable.graphic_eq,
        chevron = true,
        onClick = onClick,
    )
}

/**
 * One drawer row, BitChord's: a rounded card that lights up and takes a check while it is the one
 * playing. [chevron] rows drill into something and stay tappable.
 */
@Composable
internal fun OutputRow(
    title: String,
    subtitle: String?,
    iconRes: Int,
    active: Boolean = false,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    val content = LocalContentColor.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(content.copy(alpha = if (active) 0.10f else 0.05f))
            .clickable(enabled = !active || chevron, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(content.copy(alpha = if (active) 0.16f else 0.08f)),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = content.copy(alpha = if (active) 1f else 0.7f),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = content.copy(alpha = if (active) 1f else 0.85f),
                fontSize = 16.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = content.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        when {
            chevron -> Icon(painterResource(R.drawable.navigate_next), null, tint = content.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
            active -> Icon(painterResource(R.drawable.check), null, tint = content, modifier = Modifier.size(20.dp))
        }
    }
}

internal fun outputDeviceIcon(device: AudioDeviceInfo, context: Context): Int = when (outputKind(device.type)) {
    OutputKind.BLUETOOTH -> when (bluetoothForm(device, context)) {
        BluetoothForm.BUDS -> R.drawable.ic_output_buds
        BluetoothForm.SPEAKER -> R.drawable.ic_output_speaker
        BluetoothForm.CAR -> R.drawable.ic_output_car
        BluetoothForm.HEADPHONES, BluetoothForm.UNKNOWN -> R.drawable.headphones
    }
    OutputKind.WIRED, OutputKind.USB -> R.drawable.headphones
    OutputKind.HDMI -> R.drawable.ic_output_tv
    OutputKind.PHONE, null -> R.drawable.volume_up
}

internal enum class BluetoothForm { BUDS, HEADPHONES, SPEAKER, CAR, UNKNOWN }

private val HeadphoneNames = listOf(
    "headphone", "airpods max", "wh-", "qc35", "qc45", "quietcomfort ultra headphones", "momentum 4",
    "studio pro", "solo", "major iv", "major v", "monitor ii",
)
private val BudsNames = listOf(
    "buds", "airpods", "pods", "earbud", "earphone", "wf-", "tws", "liberty", "freebuds", "nothing ear",
    "jabra elite", "motif", "minor iv", "earfun",
)
private val SpeakerNames = listOf(
    "speaker", "soundbar", "sound bar", "emberton", "kilburn", "stanmore", "acton", "woburn", "middleton",
    "willen", "tufton", "stockwell", "uxbridge", "jbl flip", "jbl charge", "jbl go", "jbl clip", "jbl xtreme",
    "jbl boombox", "jbl pulse", "partybox", "wonderboom", "megaboom", "boom 3", "everboom", "hyperboom",
    "soundlink", "srs-", "ult field", "soundcore motion", "soundcore flare", "soundcore boom", "stormbox",
    "beats pill", "sonos", "homepod", "echo", "nest audio", "bose home", "beosound", "beoplay a", "boombox",
)

/**
 * What a Bluetooth output is, so its glyph can say so. LE speakers and hearing aids say it themselves; else the
 * name, worn things first so "Echo Buds" lands on the head; else the paired device's class. That last read
 * needs BLUETOOTH_CONNECT and is skipped without it, leaving [BluetoothForm.UNKNOWN] (the plain headphones).
 */
internal fun bluetoothForm(device: AudioDeviceInfo, context: Context): BluetoothForm {
    when (device.type) {
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> return BluetoothForm.SPEAKER
        AudioDeviceInfo.TYPE_HEARING_AID -> return BluetoothForm.BUDS
    }
    val name = device.productName?.toString()?.lowercase().orEmpty()
    when {
        HeadphoneNames.any { it in name } -> return BluetoothForm.HEADPHONES
        BudsNames.any { it in name } -> return BluetoothForm.BUDS
        SpeakerNames.any { it in name } -> return BluetoothForm.SPEAKER
    }
    if (!bluetoothNamesAllowed(context) || !BluetoothAdapter.checkBluetoothAddress(device.address)) return BluetoothForm.UNKNOWN
    val deviceClass = runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter
            ?.getRemoteDevice(device.address)?.bluetoothClass?.deviceClass
    }.getOrNull()
    return when (deviceClass) {
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET, BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BluetoothForm.BUDS
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> BluetoothForm.HEADPHONES
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER, BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO, BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
        -> BluetoothForm.SPEAKER
        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> BluetoothForm.CAR
        else -> BluetoothForm.UNKNOWN
    }
}

/** A glyph and the size that makes it match the others, as BitChord's output button does. */
internal class OutputGlyph(@DrawableRes val res: Int, val size: Dp)

/**
 * The output button's glyph follows where the music is going, as Apple Music's does: AirPlay for the phone itself
 * (the button's job there is to send it elsewhere), buds, speaker, car, headphones, TV, or Cast while casting.
 */
@Composable
internal fun rememberOutputGlyph(): OutputGlyph {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val devices by connection?.audioOutputDevices?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val selectedId by connection?.selectedAudioDeviceId?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val casting by connection?.service?.castConnectionHandler?.isCasting?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    if (casting) return OutputGlyph(R.drawable.cast_connected, 26.dp)
    val device = activeOutputDevice(context, devices, selectedId) ?: return OutputGlyph(R.drawable.ic_output_airplay, 24.dp)
    return when (outputKind(device.type)) {
        OutputKind.PHONE, null -> OutputGlyph(R.drawable.ic_output_airplay, 24.dp)
        OutputKind.BLUETOOTH -> when (bluetoothForm(device, context)) {
            BluetoothForm.BUDS -> OutputGlyph(R.drawable.ic_output_buds, 34.dp)
            BluetoothForm.SPEAKER -> OutputGlyph(R.drawable.ic_output_speaker, 28.dp)
            BluetoothForm.CAR -> OutputGlyph(R.drawable.ic_output_car, 28.dp)
            BluetoothForm.HEADPHONES, BluetoothForm.UNKNOWN -> OutputGlyph(R.drawable.headphones, 28.dp)
        }
        OutputKind.WIRED, OutputKind.USB -> OutputGlyph(R.drawable.headphones, 28.dp)
        OutputKind.HDMI -> OutputGlyph(R.drawable.ic_output_tv, 26.dp)
    }
}

internal enum class OutputKind { PHONE, WIRED, USB, BLUETOOTH, HDMI }

internal fun outputKind(type: Int): OutputKind? = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputKind.PHONE
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE,
    -> OutputKind.WIRED
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID,
    -> OutputKind.BLUETOOTH
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> OutputKind.HDMI
    else -> null
}

internal fun outputDeviceName(device: AudioDeviceInfo, context: Context): String {
    val kind = outputKind(device.type)
    // productName is the phone model for the built-in speaker and the wired jack.
    val own = device.productName?.toString()?.trim()?.takeIf { it.isNotBlank() && kind != OutputKind.PHONE && kind != OutputKind.WIRED }
    return own ?: context.getString(
        when (kind) {
            OutputKind.PHONE -> R.string.audio_output_phone
            OutputKind.WIRED -> R.string.audio_output_wired
            OutputKind.USB -> R.string.audio_output_usb
            OutputKind.BLUETOOTH -> R.string.audio_output_bluetooth
            OutputKind.HDMI -> R.string.audio_output_hdmi
            null -> R.string.audio_output_system
        },
    )
}

/**
 * The output the music is going to: the one picked here, else where Android routes media (API 33+),
 * else Android's own order when nothing was chosen: Bluetooth, wired, USB, HDMI, speaker.
 */
internal fun activeOutputDevice(context: Context, devices: List<AudioDeviceInfo>, selectedId: Int?): AudioDeviceInfo? {
    devices.firstOrNull { it.id == selectedId }?.let { return it }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val media = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val routed = runCatching { context.getSystemService(AudioManager::class.java)?.getAudioDevicesForAttributes(media) }
            .getOrNull().orEmpty()
        routed.firstNotNullOfOrNull { route ->
            devices.firstOrNull { it.id == route.id } ?: devices.firstOrNull { outputKind(it.type) == outputKind(route.type) }
        }?.let { return it }
    }
    return listOf(OutputKind.BLUETOOTH, OutputKind.WIRED, OutputKind.USB, OutputKind.HDMI, OutputKind.PHONE)
        .firstNotNullOfOrNull { kind -> devices.firstOrNull { outputKind(it.type) == kind } }
}
