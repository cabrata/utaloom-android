/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Layout follows BitChord's AccountProfileSelector (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */

package com.metrolist.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.App
import com.metrolist.music.R
import com.metrolist.music.ui.glass.GLASS_EDGE_COLOR
import com.metrolist.music.ui.glass.GLASS_EDGE_WIDTH
import com.metrolist.music.ui.glass.glassActive
import com.metrolist.music.ui.glass.continuous
import com.metrolist.music.ui.glass.liquidGlass
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

private val CardShape = RoundedCornerShape(28.dp).continuous()

/**
 * The top-bar avatar's account card: every saved YouTube Music login with the active one ticked,
 * then Add account, Manage accounts (rows turn into Sign out) and Settings. Tapping an account
 * switches to it; the app restarts so everything reloads as that account.
 */
@Composable
fun AccountSwitcher(
    accountImageUrl: String?,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(context) {
        context.dataStore.data
            .map { it.savedAccounts() to it.activeAccount()?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    var managing by remember { mutableStateOf(false) }
    BackHandler(onBack = onDismiss)

    fun select(account: SavedAccount) = scope.launch {
        val switched = context.safeDataStoreEdit {
            it.saveActiveAccount(accountImageUrl)
            it.activateAccount(account)
        }
        if (switched) restartApp(context)
    }

    fun signOut(account: SavedAccount, accounts: List<SavedAccount>, activeId: String?) = scope.launch {
        if (account.id != activeId) {
            context.safeDataStoreEdit { it.removeSavedAccount(account.id) }
            return@launch
        }
        // Signing out the active account hands over to the next saved one, as in BitChord.
        App.forgetAccount(context)
        accounts.firstOrNull { it.id != account.id }?.let { next ->
            context.safeDataStoreEdit { it.activateAccount(next) }
        }
        restartApp(context)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val (accounts, activeId) = state ?: return@Box
        val glass = glassActive()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 56.dp, start = 20.dp, end = 20.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .then(
                    if (glass) {
                        Modifier.liquidGlass(CardShape)
                    } else {
                        Modifier.clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    },
                )
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, CardShape)
                // Swallow taps so they don't fall through to the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 14.dp)) {
                item {
                    Text(
                        stringResource(R.string.switch_account),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 8.dp),
                    )
                }
                items(accounts, key = { it.id }) { account ->
                    val selected = account.id == activeId
                    AccountRow(
                        account = account,
                        selected = selected,
                        managing = managing,
                        onClick = {
                            when {
                                managing -> signOut(account, accounts, activeId)
                                selected -> onDismiss()
                                else -> select(account)
                            }
                        },
                    )
                }
                item { SwitcherAction(R.drawable.add, stringResource(R.string.add_account), onAddAccount) }
                if (accounts.isNotEmpty()) {
                    item {
                        SwitcherAction(
                            if (managing) R.drawable.done else R.drawable.close,
                            stringResource(if (managing) R.string.done else R.string.manage_accounts),
                        ) { managing = !managing }
                    }
                }
                item { SwitcherAction(R.drawable.settings, stringResource(R.string.settings), onOpenSettings) }
            }
        }
    }
}

@Composable
private fun AccountRow(account: SavedAccount, selected: Boolean, managing: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (account.avatar != null) {
            AsyncImage(
                model = account.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(38.dp).clip(CircleShape),
            )
        } else {
            Icon(painterResource(R.drawable.account), contentDescription = null, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account.name.ifBlank { account.email }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = account.channelHandle.ifBlank { account.email }
            if (subtitle.isNotBlank() && subtitle != account.name) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            managing -> Text(stringResource(R.string.action_logout), color = MaterialTheme.colorScheme.error)
            selected -> Icon(
                painterResource(R.drawable.check),
                contentDescription = stringResource(R.string.active_account),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SwitcherAction(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        // Same column as the avatars above, so the icons line up with them.
        Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

