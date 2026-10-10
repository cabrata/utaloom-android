package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.lyrics.LyricsProviderRegistry
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.ui.component.IconButton as NavigationIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProviderSettings(navController: NavController) {
    val (savedOrder, onOrderChange) = rememberPreference(LyricsProviderOrderKey, defaultValue = "")
    val order = LyricsProviderRegistry.deserializeProviderOrder(savedOrder)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.lyrics_providers_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Material3SettingsGroup(
            items = order.mapIndexed { index, providerId ->
                key(providerId) {
                    val provider = LyricsProviderRegistry.getProviderByName(providerId)!!
                    val (enabled, onEnabledChange) = rememberPreference(
                        LyricsProviderRegistry.providerEnabledKeys.getValue(providerId),
                        defaultValue = true,
                    )
                    val description = when (providerId) {
                        "BetterLyrics" -> R.string.enable_better_lyrics_desc
                        "LrcLib" -> R.string.enable_lrclib_desc
                        "KuGou" -> R.string.enable_kugou_desc
                        "Paxsenix" -> R.string.enable_paxsenix_desc
                        "LyricsPlus" -> R.string.enable_lyricsplus_desc
                        "Zemer" -> R.string.enable_zemer_desc
                        "YouTubeSubtitle" -> R.string.youtube_subtitle_lyrics_desc
                        else -> R.string.youtube_lyrics_desc
                    }
                    fun move(to: Int) {
                        onOrderChange(
                            LyricsProviderRegistry.serializeProviderOrder(
                                order.toMutableList().apply { add(to, removeAt(index)) },
                            ),
                        )
                    }
                    Material3SettingsItem(
                        title = { Text(provider.name) },
                        description = { Text(stringResource(description)) },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = onEnabledChange,
                                    modifier = Modifier.padding(end = 8.dp).semantics {
                                        contentDescription = provider.name
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = { move(index - 1) }, enabled = index > 0) {
                                        Icon(
                                            painterResource(R.drawable.arrow_upward),
                                            contentDescription = stringResource(R.string.lyrics_provider_move_up, provider.name),
                                        )
                                    }
                                    IconButton(onClick = { move(index + 1) }, enabled = index < order.lastIndex) {
                                        Icon(
                                            painterResource(R.drawable.arrow_downward),
                                            contentDescription = stringResource(R.string.lyrics_provider_move_down, provider.name),
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { onEnabledChange(!enabled) },
                    )
                }
            },
        )
        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.lyrics_providers)) },
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
