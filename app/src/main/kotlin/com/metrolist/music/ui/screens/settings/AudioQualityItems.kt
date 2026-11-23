/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.constants.AudioQualityCellularKey
import com.metrolist.music.constants.AudioQualityKey
import com.metrolist.music.constants.AudioQualityWifiKey
import com.metrolist.music.playback.QualityPrefs
import com.metrolist.music.playback.StreamQuality
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

/**
 * BitChord's "Audio quality" group for the front of Settings: where music comes from, then the ceiling on Wi-Fi
 * and the ceiling on mobile data, the one in use marked. Choosing opens the options right here (best first, a
 * line of what each costs under it), not a page of its own. Also emits the dialog while one is open.
 */
@Composable
fun rememberAudioQualityItems(onOpenSources: () -> Unit): List<Material3SettingsItem> {
    val (legacy, _) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)
    val (wifiName, setWifi) = rememberPreference(AudioQualityWifiKey, defaultValue = "")
    val (cellularName, setCellular) = rememberPreference(AudioQualityCellularKey, defaultValue = "")
    val prefs = QualityPrefs(
        legacy = legacy,
        wifi = StreamQuality.entries.find { it.name == wifiName },
        cellular = StreamQuality.entries.find { it.name == cellularName },
    )
    val metered = rememberNetworkMetered()
    // Which connection's options are open, or null.
    var editing by remember { mutableStateOf<Boolean?>(null) }

    editing?.let { wifi ->
        EnumDialog(
            onDismiss = { editing = null },
            onSelect = {
                if (wifi) setWifi(it.name) else setCellular(it.name)
                editing = null
            },
            title = stringResource(if (wifi) R.string.audio_quality_on_wifi else R.string.audio_quality_on_mobile_data),
            current = prefs.effective(wifi),
            values = StreamQuality.entries,
            valueText = { qualityLabel(it) },
            valueDescription = { qualityDetail(it) },
        )
    }

    val inUse = stringResource(R.string.audio_quality_in_use)
    return listOf(
        Material3SettingsItem(
            icon = painterResource(R.drawable.graphic_eq),
            title = { Text(stringResource(R.string.music_sources)) },
            targetKey = R.string.music_sources,
            description = { Text(stringResource(R.string.music_sources_selection_desc)) },
            onClick = onOpenSources,
        ),
        Material3SettingsItem(
            icon = painterResource(R.drawable.wifi),
            title = { Text(stringResource(R.string.audio_quality_on_wifi)) },
            targetKey = R.string.audio_quality,
            description = {
                Text(qualityLabel(prefs.effective(true)) + if (metered == false) " · $inUse" else "")
            },
            onClick = { editing = true },
        ),
        Material3SettingsItem(
            icon = painterResource(R.drawable.signal_cellular),
            title = { Text(stringResource(R.string.audio_quality_on_mobile_data)) },
            description = {
                Text(qualityLabel(prefs.effective(false)) + if (metered == true) " · $inUse" else "")
            },
            onClick = { editing = false },
        ),
    )
}

@Composable
private fun qualityLabel(quality: StreamQuality): String = stringResource(
    when (quality) {
        StreamQuality.LOSSLESS -> R.string.audio_quality_lossless
        StreamQuality.HIGH -> R.string.audio_quality_high
        StreamQuality.LOW -> R.string.audio_quality_low
    },
)

@Composable
private fun qualityDetail(quality: StreamQuality): String = stringResource(
    when (quality) {
        StreamQuality.LOSSLESS -> R.string.audio_quality_lossless_detail
        StreamQuality.HIGH -> R.string.audio_quality_high_detail
        StreamQuality.LOW -> R.string.audio_quality_low_detail
    },
)

/** Whether the connection in use is metered, kept current while the screen is open. */
@Composable
private fun rememberNetworkMetered(): Boolean? {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(ConnectivityManager::class.java) } ?: return null
    var metered by remember(manager) { mutableStateOf<Boolean?>(manager.isActiveNetworkMetered) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { metered = manager.isActiveNetworkMetered }
            override fun onLost(network: Network) { metered = manager.isActiveNetworkMetered }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                metered = manager.isActiveNetworkMetered
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return metered
}
