package com.metrolist.music.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import com.metrolist.music.ui.component.DefaultDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.MusicAddonsKey
import com.metrolist.music.playback.AddonAuthRequiredException
import com.metrolist.music.playback.MusicAddon
import com.metrolist.music.playback.MusicAddons
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.component.SettingsSwitch
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.metrolist.music.ui.component.IconButton as NavigationIconButton

private val WorkingGreen = Color(0xFF2E9E5B)

/** Air between the add-on cards; part of what a card measures as its height while dragged. */
private val ROW_GAP = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSourcesSettings(navController: NavController) {
    val (saved, onSavedChange) = rememberPreference(MusicAddonsKey, defaultValue = "")
    val addons = remember(saved) { MusicAddons.decode(saved) }
    val save = { list: List<MusicAddon> -> onSavedChange(MusicAddons.encode(list)) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var qualityDialogUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var editingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Per add-on URL: absent while checking, "" when working, otherwise what failed.
    val statuses = remember { mutableStateMapOf<String, String>() }
    val currentAddons by rememberUpdatedState(addons)
    LaunchedEffect(addons.map { it.url }) {
        val refreshed = addons.mapNotNull { addon ->
            try {
                MusicAddons.check(addon).also { statuses[addon.url] = "" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                statuses[addon.url] =
                    if (e is AddonAuthRequiredException) context.getString(R.string.music_source_auth_unsupported)
                    else e.message ?: e.javaClass.simpleName
                null
            }
        }.associateBy { it.url }
        // Merge onto the list as it is now, so a switch or quality changed during the check survives.
        val merged = currentAddons.map { current ->
            refreshed[current.url]?.let { fresh ->
                fresh.copy(
                    enabled = current.enabled,
                    params = fresh.params + current.params.filterKeys { it == MusicAddons.QUALITY },
                )
            } ?: current
        }
        if (merged != currentAddons) save(merged)
    }

    addons.firstOrNull { it.url == qualityDialogUrl }?.let { addon ->
        ListDialog(onDismiss = { qualityDialogUrl = null }) {
            items(addon.qualities) { quality ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) {
                            save(
                                addons.map {
                                    if (it.url == addon.url) it.copy(params = it.params + (MusicAddons.QUALITY to quality.value)) else it
                                },
                            )
                            qualityDialogUrl = null
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = quality.value == addon.quality, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(quality.label)
                }
            }
        }
    }

    if (showAddDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.music_source_add)) },
            placeholder = { Text("https://…/manifest.json") },
            keyboardType = KeyboardType.Uri,
            onDone = { link ->
                scope.launch {
                    try {
                        val addon = MusicAddons.add(link)
                        save(addons.filter { it.url != addon.url } + addon)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message =
                            if (e is AddonAuthRequiredException) R.string.music_source_auth_unsupported
                            else R.string.music_source_add_failed
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }

    addons.firstOrNull { it.url == editingUrl }?.let { addon ->
        AddonEditorDialog(
            addon = addon,
            onSave = { fresh ->
                save(addons.map { if (it.url == addon.url) fresh.copy(enabled = addon.enabled) else it }.distinctBy { it.url })
                editingUrl = null
            },
            onCopy = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(addon.name, "${addon.url}/manifest.json"))
                Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
            },
            onRemove = {
                save(addons - addon)
                editingUrl = null
            },
            onDismiss = { editingUrl = null },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.music_sources_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    title = { Text("YouTube Music") },
                    description = { Text(stringResource(R.string.music_source_youtube_desc)) },
                    trailingContent = {
                        Text(
                            text = stringResource(R.string.music_source_always_on),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ),
            ),
        )
        Spacer(Modifier.height(ROW_GAP))
        ReorderableAddons(addons = addons, onReorder = save) { addon, handle ->
            val status = statuses[addon.url]
            val statusColor = when {
                status == null -> MaterialTheme.colorScheme.onSurfaceVariant
                status.isEmpty() -> WorkingGreen
                else -> MaterialTheme.colorScheme.error
            }
            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        borderColor = statusColor.takeIf { status != null },
                        leadingContent = handle,
                        title = { Text(addon.name) },
                        description = {
                            Column {
                                val qualityLabel = addon.qualities.firstOrNull { it.value == addon.quality }?.label ?: addon.quality
                                Text(
                                    listOfNotNull(
                                        addon.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                        stringResource(
                                            if (addon.downloadsAllowed) R.string.music_source_downloads_allowed
                                            else R.string.music_source_streaming_only,
                                        ),
                                    ).joinToString(" · "),
                                )
                                if (qualityLabel != null) {
                                    Text(
                                        text = stringResource(R.string.music_source_quality, qualityLabel),
                                        color = if (addon.qualities.size > 1) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                        modifier = Modifier.clickable(enabled = addon.qualities.size > 1, role = Role.Button) {
                                            qualityDialogUrl = addon.url
                                        },
                                    )
                                }
                                Text(
                                    text = when {
                                        status == null -> stringResource(R.string.music_source_checking)
                                        status.isEmpty() -> stringResource(R.string.music_source_working)
                                        else -> stringResource(R.string.music_source_error, status)
                                    },
                                    color = statusColor,
                                )
                            }
                        },
                        trailingContent = {
                            SettingsSwitch(
                                checked = addon.enabled,
                                onCheckedChange = { enabled ->
                                    save(addons.map { if (it.url == addon.url) it.copy(enabled = enabled) else it })
                                },
                                showIcons = false,
                                modifier = Modifier.semantics { contentDescription = addon.name },
                            )
                        },
                        onClick = { editingUrl = addon.url },
                    ),
                ),
            )
        }
        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.add),
                    title = { Text(stringResource(R.string.music_source_add)) },
                    description = { Text(stringResource(R.string.music_source_add_desc)) },
                    onClick = { showAddDialog = true },
                ),
            ),
        )
        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.music_sources)) },
        navigationIcon = {
            NavigationIconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.back_button_desc))
            }
        },
    )
}

/**
 * Add-on cards a finger can reorder, by the grip at the start of each. The mechanics are BitChord's: the gesture
 * keeps only how far the finger has travelled and which slot it began in, so where a card is drawn and which slot
 * it occupies are both derived from those two numbers and cannot drift apart however many swaps happen. The drag
 * sits on the grip alone, since a drag anywhere on a card would make the page impossible to scroll past, and the
 * order is written back once the gesture ends, not on every frame of it.
 *
 * Cards are keyed by link so the gesture follows its add-on from slot to slot. A single add-on has nothing to
 * reorder, so it gets no grip.
 */
@Composable
private fun ReorderableAddons(
    addons: List<MusicAddon>,
    onReorder: (List<MusicAddon>) -> Unit,
    row: @Composable (MusicAddon, (@Composable () -> Unit)?) -> Unit,
) {
    if (addons.isEmpty()) return
    if (addons.size == 1) {
        Column(Modifier.fillMaxWidth().padding(bottom = ROW_GAP)) { row(addons.first(), null) }
        return
    }

    var liveOrder by remember(addons) { mutableStateOf(addons) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var totalDrag by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableIntStateOf(0) }
    var slotShift by remember { mutableFloatStateOf(0f) }
    // Heights differ per card (a status line can wrap), so each is measured; frozen during a gesture so a
    // relayout mid-drag cannot move the boundaries the drag is measured against.
    val heights = remember { mutableStateMapOf<String, Float>() }
    var lockedHeights by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val handleDescription = stringResource(R.string.drag_to_reorder)

    liveOrder.forEach { addon ->
        key(addon.url) {
            val isDragging = addon.url == dragged
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .onSizeChanged { heights[addon.url] = it.height.toFloat() }
                    .graphicsLayer { translationY = if (isDragging) totalDrag - slotShift else 0f }
                    .padding(bottom = ROW_GAP),
            ) {
                row(addon) {
                    Icon(
                        painter = painterResource(R.drawable.drag_handle),
                        contentDescription = handleDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragged = addon.url
                                        totalDrag = 0f
                                        slotShift = 0f
                                        startIndex = liveOrder.indexOfFirst { it.url == addon.url }
                                        lockedHeights = heights.toMap()
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val height = { url: String -> lockedHeights[url] ?: 0f }
                                        var at = liveOrder.indexOfFirst { it.url == addon.url }
                                        if (at < 0 || lockedHeights.isEmpty()) return@detectDragGestures
                                        // Held past either end the card stops there under the finger.
                                        val others = liveOrder.filterNot { it.url == addon.url }
                                        totalDrag = (totalDrag + delta.y).coerceIn(
                                            -others.take(startIndex).sumOf { height(it.url).toDouble() }.toFloat(),
                                            others.drop(startIndex).sumOf { height(it.url).toDouble() }.toFloat(),
                                        )
                                        // A loop: one pointer event can cover several cards when the finger is quick.
                                        while (true) {
                                            val offset = totalDrag - slotShift
                                            val below = liveOrder.getOrNull(at + 1)?.let { height(it.url) }
                                            val above = liveOrder.getOrNull(at - 1)?.let { height(it.url) }
                                            if (below != null && below > 0f && offset > below * SWAP_THRESHOLD) {
                                                liveOrder = liveOrder.toMutableList().apply { add(at + 1, removeAt(at)) }
                                                slotShift += below
                                                at++
                                            } else if (above != null && above > 0f && offset < -above * SWAP_THRESHOLD) {
                                                liveOrder = liveOrder.toMutableList().apply { add(at - 1, removeAt(at)) }
                                                slotShift -= above
                                                at--
                                            } else {
                                                break
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragged = null
                                        totalDrag = 0f
                                        slotShift = 0f
                                        if (liveOrder != addons) onReorder(liveOrder)
                                    },
                                    onDragCancel = {
                                        dragged = null
                                        totalDrag = 0f
                                        slotShift = 0f
                                        liveOrder = addons
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}

/**
 * How far past a neighbour the finger carries a card before the two trade places, as a share of the neighbour's
 * height. More than half on purpose: at exactly half a card that has just swapped sits on the boundary of swapping
 * back, and the shake of a real finger would flip it back and forth.
 */
private const val SWAP_THRESHOLD = 0.6f

/**
 * Edits one add-on: its link, with a test that reads the manifest and says what came back, Save, Copy link and
 * Remove. The test runs against what is typed, so a corrected link can be tried before it replaces the old one.
 */
@Composable
private fun AddonEditorDialog(
    addon: MusicAddon,
    onSave: (MusicAddon) -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable(addon.url) { mutableStateOf(addon.url) }
    var testing by remember { mutableStateOf(false) }
    // Null before a test; otherwise what it found, and whether that is usable.
    var result by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val canSubmit = url.isNotBlank() && !testing

    /** Reads the manifest behind [url]; null when it failed, with the reason already in [result]. */
    suspend fun probe(): MusicAddon? = try {
        MusicAddons.add(url).also {
            result = listOfNotNull(it.name, it.version.takeIf(String::isNotBlank)?.let { v -> "v$v" })
                .joinToString(" · ") to true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val reason = if (e is AddonAuthRequiredException) context.getString(R.string.music_source_auth_unsupported)
        else e.message ?: e.javaClass.simpleName
        result = context.getString(R.string.music_source_error, reason) to false
        null
    }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.music_source_edit)) },
    ) {
        Text(
            text = addon.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                result = null
            },
            placeholder = { Text("https://…/manifest.json") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = when {
                testing -> stringResource(R.string.music_source_testing)
                else -> result?.first.orEmpty()
            },
            color = if (result?.second == false) MaterialTheme.colorScheme.error else WorkingGreen,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        )
        EditorAction(stringResource(R.string.music_source_test), enabled = canSubmit) {
            scope.launch {
                testing = true
                probe()
                testing = false
            }
        }
        EditorAction(stringResource(R.string.save), enabled = canSubmit, emphasised = true) {
            if (MusicAddons.normalizeUrl(url) == addon.url) {
                onDismiss()
            } else {
                scope.launch {
                    testing = true
                    val fresh = probe()
                    testing = false
                    if (fresh != null) onSave(fresh)
                }
            }
        }
        EditorAction(stringResource(R.string.copy_link), onClick = onCopy)
        EditorAction(stringResource(R.string.music_source_remove_action), destructive = true, onClick = onRemove)
    }
}

/** One stacked action of the editor, BitChord's way: a full-width label under a hairline. */
@Composable
private fun EditorAction(
    label: String,
    enabled: Boolean = true,
    emphasised: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = label,
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                destructive -> MaterialTheme.colorScheme.error
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}
