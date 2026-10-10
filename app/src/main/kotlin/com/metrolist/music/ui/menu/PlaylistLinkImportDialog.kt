/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.utils.PlaylistImportService
import kotlinx.coroutines.flow.filterNotNull

/**
 * "Import from Spotify or YouTube Music": link dialog, then a progress dialog that can be sent to the background.
 * The import itself runs in [PlaylistImportService], so its notification keeps tracking it either way.
 */
@Composable
fun PlaylistLinkImportDialog(
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val progress by PlaylistImportService.progress.collectAsState()
    var started by rememberSaveable { mutableStateOf(progress != null) }

    LaunchedEffect(Unit) {
        PlaylistImportService.result.filterNotNull().collect { event ->
            if (!started) return@collect
            when (event) {
                is PlaylistImportService.Done -> {
                    val msg = context.getString(R.string.import_from_link_done, event.songs, event.missing) +
                        if (event.truncated) "\n" + context.getString(R.string.import_from_link_truncated) else ""
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    navController.navigate("local_playlist/${event.playlistId}")
                }
                is PlaylistImportService.Failed -> Toast.makeText(
                    context,
                    event.message ?: context.getString(R.string.import_from_link_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
            PlaylistImportService.clearResult()
            onDismiss()
        }
    }

    if (!started) {
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.link), contentDescription = null) },
            title = { Text(stringResource(R.string.import_from_link)) },
            placeholder = { Text(stringResource(R.string.import_from_link_hint)) },
            keyboardType = KeyboardType.Uri,
            autoDismiss = false,
            onDismiss = onDismiss,
            onDone = { url ->
                PlaylistImportService.start(context, url)
                started = true
            },
        )
    } else {
        val p = progress
        // Cancelled from the notification while this dialog was open: nothing more will arrive.
        val result by PlaylistImportService.result.collectAsState()
        LaunchedEffect(p, result) { if (p == null && result == null) onDismiss() }
        LoadingScreen(
            isVisible = true,
            value = if (p == null || p.total == 0) 0 else p.done * 100 / p.total,
            songTitle = p?.title,
            detail = p?.takeIf { it.total > 0 }?.let { stringResource(R.string.import_from_link_progress, it.done, it.total, it.done * 100 / it.total) },
            indeterminate = p == null || p.total == 0,
            onCancel = {
                PlaylistImportService.cancel(context)
                onDismiss()
            },
            onBackground = onDismiss,
        )
    }
}
