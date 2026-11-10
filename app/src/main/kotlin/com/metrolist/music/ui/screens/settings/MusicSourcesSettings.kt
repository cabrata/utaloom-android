package com.metrolist.music.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.IconButton
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
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.metrolist.music.ui.component.IconButton as NavigationIconButton

private val WorkingGreen = Color(0xFF2E9E5B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSourcesSettings(navController: NavController) {
    val (saved, onSavedChange) = rememberPreference(MusicAddonsKey, defaultValue = "")
    val addons = remember(saved) { MusicAddons.decode(saved) }
    val save = { list: List<MusicAddon> -> onSavedChange(MusicAddons.encode(list)) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var qualityDialogUrl by rememberSaveable { mutableStateOf<String?>(null) }
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
            items = addons.mapIndexed { index, addon ->
                val setEnabled = { enabled: Boolean ->
                    save(addons.toMutableList().apply { set(index, addon.copy(enabled = enabled)) })
                }
                val move = { to: Int -> save(addons.toMutableList().apply { add(to, removeAt(index)) }) }
                val status = statuses[addon.url]
                val statusColor = when {
                    status == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    status.isEmpty() -> WorkingGreen
                    else -> MaterialTheme.colorScheme.error
                }
                Material3SettingsItem(
                    borderColor = statusColor.takeIf { status != null },
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
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = addon.enabled,
                                onCheckedChange = setEnabled,
                                modifier = Modifier.padding(end = 8.dp).semantics {
                                    contentDescription = addon.name
                                },
                            )
                            Row(horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { move(index - 1) }, enabled = index > 0) {
                                    Icon(
                                        painterResource(R.drawable.arrow_upward),
                                        contentDescription = stringResource(R.string.lyrics_provider_move_up, addon.name),
                                    )
                                }
                                IconButton(onClick = { move(index + 1) }, enabled = index < addons.lastIndex) {
                                    Icon(
                                        painterResource(R.drawable.arrow_downward),
                                        contentDescription = stringResource(R.string.lyrics_provider_move_down, addon.name),
                                    )
                                }
                                IconButton(onClick = { save(addons - addon) }) {
                                    Icon(
                                        painterResource(R.drawable.delete),
                                        contentDescription = stringResource(R.string.music_source_remove, addon.name),
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText(addon.name, "${addon.url}/manifest.json"))
                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                    },
                )
            } + listOf(
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
