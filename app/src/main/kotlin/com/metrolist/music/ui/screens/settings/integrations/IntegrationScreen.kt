/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 * Layout ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */

package com.metrolist.music.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.DiscordUsernameKey
import com.metrolist.music.constants.EnableDiscordRPCKey
import com.metrolist.music.constants.EnableLastFMScrobblingKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.LastFMSessionKey
import com.metrolist.music.constants.LastFMUsernameKey
import com.metrolist.music.constants.SpotifySpDcCookieKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.SettingsSwitch
import com.metrolist.music.ui.screens.settings.LogoutDialog
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.HomeViewModel

/** One page for who is signed in and every outside service the app talks to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    val channelHandle by rememberPreference(AccountChannelHandleKey, "")
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    val (spotifySpDc, onSpotifySpDcChange) = rememberPreference(SpotifySpDcCookieKey, "")
    val discordUsername by rememberPreference(DiscordUsernameKey, "")
    val discordRpc by rememberPreference(EnableDiscordRPCKey, true)
    val lastfmSession by rememberPreference(LastFMSessionKey, "")
    val lastfmUsername by rememberPreference(LastFMUsernameKey, "")
    val (lastfmScrobbling, onLastfmScrobblingChange) = rememberPreference(EnableLastFMScrobblingKey, false)

    if (showLogoutDialog) {
        LogoutDialog(onDismiss = { showLogoutDialog = false }, onLoggedOut = { showLogoutDialog = false })
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Material3SettingsGroup(
            items = if (isLoggedIn) {
                listOf(
                    Material3SettingsItem(
                        leadingContent = {
                            AsyncImage(
                                model = accountImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                            )
                        },
                        title = { Text(accountName, style = MaterialTheme.typography.titleLarge) },
                        description = channelHandle.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        onClick = { navController.navigate("account") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.account),
                        title = { Text(stringResource(R.string.listen_as)) },
                        description = { Text(accountName) },
                        onClick = { navController.navigate("switch_channel") },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.logout),
                        title = { Text(stringResource(R.string.action_logout), color = MaterialTheme.colorScheme.error) },
                        onClick = { showLogoutDialog = true },
                    ),
                )
            } else {
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.login),
                        title = { Text(stringResource(R.string.login)) },
                        onClick = { navController.navigate("login") },
                    ),
                )
            },
        )
        if (isLoggedIn) GroupFooter(stringResource(R.string.account_profiles_help))

        Material3SettingsGroup(
            title = stringResource(R.string.spotify),
            items = listOfNotNull(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.spotify_logo),
                    title = { Text(stringResource(R.string.spotify)) },
                    description = {
                        Text(stringResource(if (spotifySpDc.isNotBlank()) R.string.integration_connected else R.string.tap_to_connect))
                    },
                    onClick = {
                        navController.navigate(if (spotifySpDc.isNotBlank()) "spotify_library" else "settings/spotify_login")
                    },
                ),
                if (spotifySpDc.isNotBlank()) {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.logout),
                        title = { Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error) },
                        onClick = { onSpotifySpDcChange("") },
                    )
                } else null,
            ),
        )
        GroupFooter(stringResource(R.string.spotify_connect_subtitle))

        Material3SettingsGroup(
            title = stringResource(R.string.rich_presence),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.discord),
                    title = { Text("Discord") },
                    description = {
                        Text(
                            when {
                                discordUsername.isBlank() -> stringResource(R.string.tap_to_connect)
                                !discordRpc -> stringResource(R.string.discord_presence_off)
                                else -> stringResource(R.string.discord_sharing_as, discordUsername)
                            }
                        )
                    },
                    onClick = { navController.navigate("settings/integrations/discord") },
                ),
            ),
        )
        GroupFooter(stringResource(R.string.rich_presence_footer))

        Material3SettingsGroup(
            title = stringResource(R.string.scrobbling),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.history),
                    title = { Text("Last.fm") },
                    description = {
                        Text(
                            if (lastfmSession.isBlank()) stringResource(R.string.tap_to_sign_in)
                            else stringResource(R.string.lastfm_signed_in_as, lastfmUsername)
                        )
                    },
                    trailingContent = {
                        SettingsSwitch(
                            checked = lastfmScrobbling && lastfmSession.isNotBlank(),
                            onCheckedChange = { checked ->
                                if (checked && lastfmSession.isBlank()) navController.navigate("settings/integrations/lastfm")
                                else onLastfmScrobblingChange(checked)
                            },
                        )
                    },
                    onClick = { navController.navigate("settings/integrations/lastfm") },
                ),
            ),
        )
        GroupFooter(stringResource(R.string.scrobbling_footer))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.account_integrations)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

@Composable
private fun GroupFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
    Spacer(Modifier.height(16.dp))
}
