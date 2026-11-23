/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.LoudnessLevel
import com.metrolist.music.constants.LoudnessLevelKey
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.rememberEnumPreference
import androidx.compose.ui.platform.LocalLocale

@Composable
fun getLoudnessLevelLabel(loudnessLevel: LoudnessLevel): String {
    return when (loudnessLevel) {
        LoudnessLevel.AGGRESSIVE -> stringResource(R.string.loudness_level_aggressive)
        LoudnessLevel.LOUD -> stringResource(R.string.loudness_level_loud)
        LoudnessLevel.BALANCED -> stringResource(R.string.loudness_level_balanced)
        LoudnessLevel.QUIET -> stringResource(R.string.loudness_level_quiet)
    }
}

/** One line of the media details: an icon, what it is, and its value. [value] null means unknown. */
class MediaInfoRow(val icon: Int, val label: String, val value: String?)

/** The details sections; [loaded] turns true once the YouTube info has arrived. */
class MediaInfoSections(
    val general: List<MediaInfoRow>,
    val information: List<MediaInfoRow>,
    val description: String?,
    val loaded: Boolean,
)

/** Loads the song's details and arranges them; shared by the Material sheet and the Liquid Glass drawer. */
@Composable
fun rememberMediaInfoSections(videoId: String): MediaInfoSections {
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    val database = LocalDatabase.current
    var song by remember { mutableStateOf<Song?>(null) }
    var currentFormat by remember { mutableStateOf<FormatEntity?>(null) }

    val playerConnection = LocalPlayerConnection.current
    val currentStreamClient by playerConnection?.currentStreamClient?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current
    val artistNameAliases = LocalArtistNameAliases.current
    val loudnessLevel by rememberEnumPreference(LoudnessLevelKey, defaultValue = LoudnessLevel.BALANCED)
    val targetLufs: Float = loudnessLevel.targetLufs

    LaunchedEffect(Unit, videoId) { info = YouTube.getMediaInfo(videoId).getOrNull() }
    LaunchedEffect(Unit, videoId) { database.song(videoId).collect { song = it } }
    LaunchedEffect(Unit, videoId) { database.format(videoId).collect { currentFormat = it } }

    val measuredLufs: Double? = currentFormat?.perceptualLoudnessDb
        ?: currentFormat?.loudnessDb?.let { it + LoudnessLevel.AGGRESSIVE.targetLufs }
    val loudnessLabel = getLoudnessLevelLabel(loudnessLevel)
    val locale = LocalLocale.current.platformLocale

    val general = listOf(
        MediaInfoRow(R.drawable.music_note, stringResource(R.string.song_title), info?.title ?: song?.title),
        MediaInfoRow(
            R.drawable.person,
            stringResource(R.string.song_artists),
            song?.artists?.joinToString { ArtistNameAliases.resolve(artistNameAliases, it.id, it.name) }
                ?: info?.author?.let { ArtistNameAliases.resolve(artistNameAliases, null, it) },
        ),
        MediaInfoRow(R.drawable.media3_icon_bookmark_filled, stringResource(R.string.media_id), song?.id ?: info?.videoId),
    )
    val information = if (currentFormat != null) {
        listOf(
            MediaInfoRow(R.drawable.media3_icon_feed, stringResource(R.string.views), info?.viewCount?.let(::numberFormatter).orEmpty()),
            MediaInfoRow(R.drawable.media3_icon_thumb_up_unfilled, stringResource(R.string.likes), info?.like?.let(::numberFormatter).orEmpty()),
            MediaInfoRow(R.drawable.media3_icon_thumb_down_unfilled, stringResource(R.string.dislikes), info?.dislike?.let(::numberFormatter).orEmpty()),
            MediaInfoRow(R.drawable.key, "Itag", currentFormat?.itag?.toString()),
            MediaInfoRow(R.drawable.play, stringResource(R.string.stream_client), currentStreamClient),
            MediaInfoRow(R.drawable.info, stringResource(R.string.mime_type), currentFormat?.mimeType),
            MediaInfoRow(R.drawable.radio, stringResource(R.string.codecs), currentFormat?.codecs),
            MediaInfoRow(R.drawable.gradient, stringResource(R.string.bitrate), currentFormat?.bitrate?.let { "${it / 1000} Kbps" }),
            MediaInfoRow(R.drawable.contrast, stringResource(R.string.sample_rate), currentFormat?.sampleRate?.let { "$it Hz" }),
            MediaInfoRow(
                R.drawable.volume_up,
                stringResource(R.string.loudness),
                measuredLufs?.let { String.format(locale, "%.2f dB", it - targetLufs) },
            ),
            MediaInfoRow(R.drawable.volume_up, stringResource(R.string.loudness_level), loudnessLabel),
            MediaInfoRow(
                R.drawable.volume_mute,
                stringResource(R.string.volume),
                playerConnection?.let { "${(it.player.volume * 100).toInt()}%" },
            ),
            MediaInfoRow(
                R.drawable.content_copy,
                stringResource(R.string.file_size),
                currentFormat?.contentLength?.let { Formatter.formatShortFileSize(context, it) },
            ),
        )
    } else {
        emptyList()
    }
    return MediaInfoSections(general, information, info?.description, loaded = info != null)
}

/** Copies a detail to the clipboard, as tapping a row of the details does. */
fun copyMediaInfo(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("text", text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

@Composable
fun ShowMediaInfo(videoId: String) {
    if (videoId.isBlank() || videoId.isEmpty()) return

    val windowInsets = WindowInsets.systemBars
    val context = LocalContext.current
    val sections = rememberMediaInfoSections(videoId)
    val unknown = stringResource(R.string.unknown)

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier
            .padding(windowInsets.asPaddingValues())
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (sections.loaded) {
            item(contentType = "MediaDetails") {
                Column {
                    @Composable
                    fun List<MediaInfoRow>.toItems() = map { row ->
                        val text = row.value ?: unknown
                        Material3SettingsItem(
                            title = { Text(row.label) },
                            description = { Text(text) },
                            icon = painterResource(row.icon),
                            onClick = { copyMediaInfo(context, text) },
                        )
                    }

                    Material3SettingsGroup(title = stringResource(R.string.general), items = sections.general.toItems())
                    Spacer(Modifier.height(8.dp))
                    Material3SettingsGroup(title = stringResource(R.string.information), items = sections.information.toItems())
                    Spacer(Modifier.height(8.dp))
                    val descriptionText = sections.description ?: unknown
                    Material3SettingsGroup(
                        title = stringResource(R.string.description),
                        items = listOf(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.description)) },
                                description = { Text(descriptionText) },
                                onClick = { copyMediaInfo(context, descriptionText) },
                            ),
                        ),
                    )
                }
            }
        } else {
            item(contentType = "MediaInfoLoader") {
                ShimmerHost {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                    ) {
                        TextPlaceholder()
                    }
                }
            }
        }
    }
}
