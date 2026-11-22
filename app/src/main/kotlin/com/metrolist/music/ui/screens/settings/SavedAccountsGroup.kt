/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.SavedAccount
import com.metrolist.music.utils.activateAccount
import com.metrolist.music.utils.activeAccount
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.removeSavedAccount
import com.metrolist.music.utils.restartApp
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.utils.saveActiveAccount
import com.metrolist.music.utils.savedAccounts
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Every YouTube Music login the user added: tap one to switch to it, or add another. */
@Composable
fun SavedAccountsGroup(
    navController: NavController,
    accountImageUrl: String?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(context) {
        context.dataStore.data
            .map { it.savedAccounts() to it.activeAccount()?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val (accounts, activeId) = state ?: return
    // Signed out with nothing saved: the login row above already covers it.
    if (accounts.isEmpty() && activeId == null) return

    // Logins made before accounts were saved, or through the token editor, join the list here.
    LaunchedEffect(activeId, accountImageUrl) {
        val active = accounts.firstOrNull { it.id == activeId }
        if (activeId != null && (active == null || (accountImageUrl != null && active.avatar != accountImageUrl))) {
            context.safeDataStoreEdit { it.saveActiveAccount(accountImageUrl) }
        }
    }

    Material3SettingsGroup(
        title = stringResource(R.string.youtube_accounts),
        items = accounts.map { account ->
            val isActive = account.id == activeId
            Material3SettingsItem(
                leadingContent = { AccountAvatar(account) },
                title = { Text(account.name.ifBlank { account.email }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                description = account.email.takeIf { it.isNotBlank() && account.name.isNotBlank() }?.let { email ->
                    { Text(email, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                trailingContent = {
                    if (isActive) {
                        Icon(
                            painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.active_account),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        IconButton(onClick = {
                            scope.launch { context.safeDataStoreEdit { it.removeSavedAccount(account.id) } }
                        }) {
                            Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.remove_account))
                        }
                    }
                },
                onClick = if (isActive) null else {
                    {
                        scope.launch {
                            val switched = context.safeDataStoreEdit {
                                it.saveActiveAccount()
                                it.activateAccount(account)
                            }
                            if (switched) restartApp(context)
                        }
                    }
                },
            )
        } + Material3SettingsItem(
            icon = painterResource(R.drawable.add),
            title = { Text(stringResource(R.string.add_account)) },
            onClick = {
                onClose()
                navController.navigate("add_account")
            },
        ),
        useLowContrast = true,
    )
}

@Composable
private fun AccountAvatar(account: SavedAccount) {
    if (account.avatar != null) {
        AsyncImage(
            model = account.avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(36.dp).clip(CircleShape),
        )
    } else {
        Box(Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.account), contentDescription = null, modifier = Modifier.size(36.dp))
        }
    }
}
