/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.VarispeedKey
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.extensions.metadata
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.menu.AddToPlaylistDialog
import com.metrolist.music.ui.menu.ListenTogetherDialog
import com.metrolist.music.ui.menu.SpeedDialog
import com.metrolist.music.ui.menu.TempoPitchDialog
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The "…" button: reports where it sits so the glass menu can grow out of it. */
@Composable
internal fun HeroMenuButton(onOpen: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable { onOpen(bounds) },
    ) {
        Icon(painterResource(R.drawable.more_horiz), stringResource(R.string.more_options), tint = Color.White)
    }
}

/**
 * BitChord's IosMenuPopover for the Liquid Glass player: a frosted slab hung off the "…" button that grows
 * from its corner. The frost is the cover blur [HeroBackdrop] already cached, drawn under the panel, so
 * nothing is blurred live. The dialogs it opens live outside the panel and outlast it.
 */
@Composable
internal fun HeroGlassMenu(
    metadata: MediaMetadata?,
    anchor: Rect?,
    state: BottomSheetState,
    onDismiss: () -> Unit,
) {
    val open = anchor != null && metadata != null
    var shown by remember { mutableStateOf<Pair<MediaMetadata, Rect>?>(null) }
    if (open) shown = metadata!! to anchor!!

    // Held apart from [shown]: a dialog opened from the menu outlives the menu's exit.
    var dialog by remember { mutableStateOf<Pair<HeroMenuDialog, MediaMetadata>?>(null) }
    dialog?.let { (kind, song) -> HeroMenuDialogs(song, kind, state) { dialog = null } }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            // As in BitChord: the first frame composes every row; growing from it would jump across that frame.
            if (progress.value == 0f) {
                withFrameNanos { }
                withFrameNanos { }
            }
            progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f))
        } else {
            progress.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
            shown = null
        }
    }
    BackHandler(enabled = open, onBack = onDismiss)

    val (item, menuAnchor) = shown ?: return
    val frost = rememberHeroFrost(item.thumbnailUrl)
    val insets = WindowInsets.safeDrawing
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var panelOffset by remember { mutableStateOf(IntOffset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.positionInWindow() },
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = open, onClick = onDismiss),
            )
            Box(
                Modifier
                    .pointerInput(Unit) {}
                    .clip(MenuShape)
                    .heroFrost(frost, { overlaySize }, { panelOffset })
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), MenuShape),
                propagateMinConstraints = true,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    HeroMenuItems(item, state, onDismiss, onDialog = { dialog = it to item })
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 14.dp.roundToPx()
        val top = insets.getTop(this) + 8.dp.roundToPx()
        val bottom = height - insets.getBottom(this) - 12.dp.roundToPx()
        val scrim = measurables[0].measure(Constraints.fixed(width, height))
        val panelWidth = MenuWidth.roundToPx().coerceAtMost(width - margin * 2)
        val panel = measurables[1].measure(
            Constraints(minWidth = panelWidth, maxWidth = panelWidth, maxHeight = (bottom - top).coerceAtLeast(0)),
        )
        val a = menuAnchor.translate(-overlayOffset)
        val panelX = (a.right.roundToInt() - panel.width).coerceIn(margin, (width - margin - panel.width).coerceAtLeast(margin))
        val lowest = (bottom - panel.height).coerceAtLeast(top)
        val hangsDown = a.center.y < height / 2f
        val panelY = if (hangsDown) a.top.roundToInt().coerceIn(top, lowest) else (a.bottom.roundToInt() - panel.height).coerceIn(top, lowest)
        panelOffset = IntOffset(panelX, panelY)
        overlaySize = IntSize(width, height)
        layout(width, height) {
            scrim.place(0, 0)
            panel.placeWithLayer(panelX, panelY) {
                val p = progress.value
                alpha = (p * 2.5f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(1f, if (hangsDown) 0f else 1f)
                scaleX = lerp(0.3f, 1f, p)
                scaleY = scaleX
                // Drawn once at full size and only the picture scaled, as in BitChord.
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }
}

private enum class HeroMenuDialog { Playlist, ListenTogether, Artist, Tempo, Speed, SleepTimer, SaveQueue }

@Composable
private fun HeroMenuDialogs(metadata: MediaMetadata, dialog: HeroMenuDialog?, state: BottomSheetState, close: () -> Unit) {
    val database = LocalDatabase.current
    val navController = LocalNavController.current
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    AddToPlaylistDialog(
        isVisible = dialog == HeroMenuDialog.Playlist,
        onGetSong = {
            database.withTransaction { insert(metadata) }
            listOf(metadata.id)
        },
        onGetSongIds = { listOf(metadata.id) },
        onDismiss = close,
    )
    ListenTogetherDialog(visible = dialog == HeroMenuDialog.ListenTogether, mediaMetadata = metadata, onDismiss = close)
    when (dialog) {
        HeroMenuDialog.Tempo -> TempoPitchDialog(onDismiss = close)
        HeroMenuDialog.Speed -> SpeedDialog(onDismiss = close)
        HeroMenuDialog.SleepTimer -> SleepTimerDialog(onDismiss = close)
        HeroMenuDialog.SaveQueue -> SaveQueueDialog(onDismiss = close) { name ->
            close()
            val items = playerConnection?.queueWindows?.value?.mapNotNull { it.mediaItem.metadata }.orEmpty()
            if (items.isEmpty()) return@SaveQueueDialog
            scope.launch {
                Toast.makeText(context, saveQueueAsPlaylist(context, database, name, items), Toast.LENGTH_SHORT).show()
            }
        }
        HeroMenuDialog.Artist -> com.metrolist.music.ui.component.ListDialog(onDismiss = close) {
            items(metadata.artists.filter { it.id != null }.size) { i ->
                val artist = metadata.artists.filter { it.id != null }[i]
                Text(
                    text = artist.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            state.collapseSoft()
                            close()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        else -> Unit
    }
}

/** PlayerMenu's actions, laid out as BitChord's menu: quick tiles, then rows in groups. */
@Composable
private fun HeroMenuItems(
    metadata: MediaMetadata,
    state: BottomSheetState,
    onDismiss: () -> Unit,
    onDialog: (HeroMenuDialog) -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val downloadUtil = LocalDownloadUtil.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val scope = rememberCoroutineScope()
    val listenTogether = LocalListenTogetherManager.current
    val role by listenTogether?.role?.collectAsStateWithLifecycle(initialValue = RoomRole.NONE)
        ?: remember { mutableStateOf(RoomRole.NONE) }
    val guest = role == RoomRole.GUEST
    val librarySong by database.song(metadata.id).collectAsStateWithLifecycle(initialValue = null)
    val isPinned by database.speedDialDao.isPinned(metadata.id).collectAsStateWithLifecycle(initialValue = false)
    val download by downloadUtil.getDownload(metadata.id).collectAsStateWithLifecycle(initialValue = null)
    val varispeed by rememberPreference(VarispeedKey, defaultValue = false)
    val sleepTimeLeft = rememberSleepTimerTimeLeft()
    val systemEq = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val isPodcast = metadata.album?.let { !it.id.startsWith("MPREb_") } ?: false
    val linkedArtists = metadata.artists.filter { it.id != null }
    val go: (String) -> Unit = { route ->
        navController.navigate(route)
        state.collapseSoft()
        onDismiss()
    }

    val inLibrary = librarySong?.song?.inLibrary != null
    // Windows 11 context menu: the everyday actions as a strip of icon buttons above the list.
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        if (!guest) {
            val starting = stringResource(R.string.starting_radio)
            QuickAction(R.drawable.radio, stringResource(R.string.start_radio)) {
                Toast.makeText(context, starting, Toast.LENGTH_SHORT).show()
                playerConnection.startRadioSeamlessly()
                onDismiss()
            }
        }
        QuickAction(R.drawable.playlist_add, stringResource(R.string.add_to_playlist)) {
            onDialog(HeroMenuDialog.Playlist)
            onDismiss()
        }
        QuickAction(
            if (inLibrary) R.drawable.library_add_check else R.drawable.library_add,
            stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library),
        ) {
            playerConnection.toggleLibrary()
            onDismiss()
        }
        when (download?.state) {
            Download.STATE_COMPLETED -> QuickAction(R.drawable.offline, stringResource(R.string.remove_download)) {
                DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, metadata.id, false)
            }
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> QuickAction(null, stringResource(R.string.downloading)) {
                DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, metadata.id, false)
            }
            else -> QuickAction(R.drawable.download, stringResource(R.string.action_download)) {
                downloadUtil.download(metadata)
            }
        }
        QuickAction(R.drawable.link, stringResource(R.string.copy_link)) {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Song Link", "https://music.youtube.com/watch?v=${metadata.id}"))
            Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    Separator()
    if (linkedArtists.isNotEmpty() && !isPodcast) {
        MenuRow(R.drawable.artist, stringResource(R.string.view_artist), metadata.artists.joinToString { it.name }) {
            if (metadata.artists.size == 1) {
                go("artist/${metadata.artists[0].id}")
            } else {
                onDialog(HeroMenuDialog.Artist)
                onDismiss()
            }
        }
    }
    metadata.album?.let { album ->
        MenuRow(
            if (isPodcast) R.drawable.mic else R.drawable.album,
            stringResource(if (isPodcast) R.string.view_podcast else R.string.view_album),
            album.title,
        ) { go(if (isPodcast) "online_podcast/${album.id}" else "album/${album.id}") }
    }
    MenuRow(
        if (isPinned) R.drawable.remove else R.drawable.add,
        stringResource(if (isPinned) R.string.unpin_from_speed_dial else R.string.pin_to_speed_dial),
    ) {
        scope.launch(Dispatchers.IO) {
            if (isPinned) database.speedDialDao.delete(metadata.id)
            else database.speedDialDao.insert(SpeedDialItem.fromYTItem(metadata.toYTItem()))
        }
        onDismiss()
    }
    Separator()
    MenuRow(R.drawable.group, stringResource(R.string.listen_together)) {
        onDialog(HeroMenuDialog.ListenTogether)
        onDismiss()
    }
    if (guest) {
        MenuRow(R.drawable.replay, stringResource(R.string.resync)) {
            listenTogether?.requestSync()
            onDismiss()
        }
    }
    Separator()
    if (!guest) {
        MenuRow(R.drawable.bedtime, stringResource(R.string.sleep_timer), sleepTimeLeft?.let(::makeTimeString)) {
            if (sleepTimeLeft != null) playerConnection.service.sleepTimer?.clear()
            else onDialog(HeroMenuDialog.SleepTimer)
            onDismiss()
        }
    }
    MenuRow(R.drawable.queue_music, stringResource(R.string.save_queue_as_playlist)) {
        onDialog(HeroMenuDialog.SaveQueue)
        onDismiss()
    }
    MenuRow(R.drawable.info, stringResource(R.string.details)) {
        bottomSheetPageState.show { ShowMediaInfo(metadata.id) }
        onDismiss()
    }
    MenuRow(R.drawable.equalizer, stringResource(R.string.equalizer)) { go("equalizer") }
    MenuRow(R.drawable.graphic_eq, stringResource(R.string.system_equalizer)) {
        val session = playerConnection.player.audioSessionId
        if (session != C.AUDIO_SESSION_ID_UNSET && session > 0) {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            if (intent.resolveActivity(context.packageManager) != null) systemEq.launch(intent)
        }
        onDismiss()
    }
    MenuRow(R.drawable.tune, stringResource(R.string.advanced)) {
        onDialog(if (varispeed) HeroMenuDialog.Speed else HeroMenuDialog.Tempo)
        onDismiss()
    }
}

@Composable
private fun QuickAction(icon: Int?, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) MenuPressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        if (icon == null) CircularProgressIndicator(color = MenuLabel, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Icon(painterResource(icon), null, tint = MenuLabel, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, description: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) MenuPressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = MenuLabel, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, color = MenuLabel, fontSize = 17.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (description != null) {
                Text(description, color = MenuSecondary, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Separator() {
    Box(Modifier.fillMaxWidth().height(17.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
    }
}

private val MenuShape = RoundedCornerShape(30.dp)
private val FrostSaturation = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.8f) })

/** The cover blur [HeroBackdrop] cached, and how thick the slab over it must be for white labels. */
internal class HeroFrost(val image: ImageBitmap?, val fillAlpha: Float)

@Composable
internal fun rememberHeroFrost(thumbnailUrl: String?): HeroFrost {
    val image = thumbnailUrl?.let(BackdropBlurCache::get)
    // BitChord's slab is 1C1C1E at 0.60 over the blurred page. A pale cover would show through that
    // as pale grey under white labels, so the slab thickens with the cover's brightness.
    return remember(image) { HeroFrost(image, image?.let { lerp(0.60f, 0.86f, it.meanLuminance()) } ?: 0.94f) }
}

/**
 * The player's own backdrop (cover on top, mirrored below, as HeroBackdrop lays it), of which a panel at
 * [panelOffset] inside a full-player overlay of [overlaySize] shows its patch, hue drawn up like BitChord's menu.
 */
internal fun Modifier.heroFrost(frost: HeroFrost, overlaySize: () -> IntSize, panelOffset: () -> IntOffset): Modifier =
    drawBehind {
        frost.image?.let { image ->
            val (w, h) = overlaySize()
            val offset = panelOffset()
            val seam = w.coerceAtMost(h)
            val src = IntSize(image.width, image.height)
            translate(-offset.x.toFloat(), -offset.y.toFloat()) {
                drawImage(image, IntOffset.Zero, src, IntOffset.Zero, IntSize(w, seam), colorFilter = FrostSaturation, filterQuality = FilterQuality.Low)
                if (h > seam) {
                    scale(1f, -1f, pivot = Offset(0f, (seam + h) / 2f)) {
                        drawImage(image, IntOffset.Zero, src, IntOffset(0, seam), IntSize(w, h - seam), colorFilter = FrostSaturation, filterQuality = FilterQuality.Low)
                    }
                }
            }
        }
        drawRect(MenuFill.copy(alpha = frost.fillAlpha))
    }

/** Mean luminance (0..1) of a small bitmap, sampled on a grid. */
private fun ImageBitmap.meanLuminance(): Float {
    val pixels = toPixelMap()
    var sum = 0f
    var n = 0
    for (y in 0 until height step 4) for (x in 0 until width step 4) {
        sum += pixels[x, y].luminance()
        n++
    }
    return if (n == 0) 0f else sum / n
}
private val MenuWidth = 256.dp
private val MenuFill = Color(0xFF1C1C1E)
private val MenuLabel = Color.White.copy(alpha = 0.92f)
private val MenuSecondary = Color.White.copy(alpha = 0.60f)
private val MenuPressed = Color.White.copy(alpha = 0.10f)
