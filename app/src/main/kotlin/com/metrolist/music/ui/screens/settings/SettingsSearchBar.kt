/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalChangelogState
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.SettingsSearchHighlight

@Composable
fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_settings_hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

private enum class Special { DefaultLinks, Changelog }

private class Entry(
    val icon: Int,
    val titleRes: Int,
    val pageRes: Int,
    val route: String?,
    val keywords: String,
    val special: Special? = null,
) {
    // Strings are resolved once with the index, so typing only runs plain contains checks.
    lateinit var title: String
    lateinit var page: String
    // Top-level pages have nothing to scroll to; inner settings are marked by their title id.
    val targetKey: Int? get() = if (pageRes in SECTIONS) null else titleRes
}

private val SECTIONS = setOf(
    R.string.settings_section_ui,
    R.string.settings_section_player_content,
    R.string.settings_section_privacy,
    R.string.settings_section_storage,
    R.string.settings_section_system,
)

/**
 * Flat result list for [query]. A tap opens the setting's page, scrolls to it and marks it.
 * shortcut: the index is hand-written to mirror the settings pages; a new setting needs a line
 * here and `targetKey` on its row.
 */
@Composable
fun SettingsSearchResults(query: String, navController: NavController) {
    val context = LocalContext.current
    val showChangelog = LocalChangelogState.current
    val entries = remember(context) { buildIndex(context) }
    val needle = query.trim()
    val hits = remember(entries, needle) {
        entries.filter {
            it.title.contains(needle, true) || it.page.contains(needle, true) || it.keywords.contains(needle, true)
        }
    }

    if (hits.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        return
    }

    Material3SettingsGroup(
        title = stringResource(R.string.settings_search_results),
        items = hits.map { entry ->
            Material3SettingsItem(
                icon = painterResource(entry.icon),
                title = { Text(entry.title) },
                description = { Text(entry.page) },
                onClick = {
                    when (entry.special) {
                        Special.DefaultLinks -> openDefaultLinks(context)
                        Special.Changelog -> showChangelog.value = true
                        null -> {
                            SettingsSearchHighlight.pendingKey = entry.targetKey
                            entry.route?.let(navController::navigate)
                        }
                    }
                },
            )
        },
    )
}

private fun openDefaultLinks(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.open_app_settings_error, Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, R.string.open_app_settings_error, Toast.LENGTH_LONG).show()
    }
}

private fun buildIndex(context: Context): List<Entry> = buildList {
    fun e(icon: Int, title: Int, page: Int, route: String?, keywords: String = "", special: Special? = null) =
        add(Entry(icon, title, page, route, keywords, special))

    val ui = R.string.settings_section_ui
    val pc = R.string.settings_section_player_content
    val sys = R.string.settings_section_system
    val ap = R.string.appearance
    val pl = R.string.player_and_audio
    val ct = R.string.content

    // Pages
    e(R.drawable.palette, ap, ui, "settings/appearance", "interface")
    e(R.drawable.play, pl, pc, "settings/player", "playback")
    e(R.drawable.language, ct, pc, "settings/content", "video country")
    e(R.drawable.lyrics, R.string.lyrics_providers, pc, "settings/lyrics", "source")
    e(R.drawable.graphic_eq, R.string.music_sources, pc, "settings/content/music_sources", "custom api url")
    e(R.drawable.translate, R.string.ai_lyrics_translation, pc, "settings/ai", "translation")
    e(R.drawable.ic_android_auto, R.string.android_auto, pc, "settings/android_auto", "car")
    e(R.drawable.integration, R.string.account_integrations, pc, "settings/integrations", "services")
    e(R.drawable.security, R.string.privacy, R.string.settings_section_privacy, "settings/privacy", "security")
    e(R.drawable.storage, R.string.storage, R.string.settings_section_storage, "settings/storage", "space")
    e(R.drawable.restore, R.string.backup_restore, R.string.settings_section_storage, "settings/backup_restore", "export import")
    e(R.drawable.link, R.string.default_links, sys, null, "open supported links", Special.DefaultLinks)
    if (BuildConfig.UPDATER_AVAILABLE) {
        e(R.drawable.update, R.string.updater, sys, "settings/updater", "version")
        e(R.drawable.update, R.string.check_for_updates, R.string.updater, "settings/updater", "auto check")
        e(R.drawable.update, R.string.update_notifications, R.string.updater, "settings/updater", "notification")
    }
    e(R.drawable.newspaper, R.string.changelog, sys, null, "release notes", Special.Changelog)
    e(R.drawable.info, R.string.about, sys, "settings/about", "version credits")

    // Appearance
    val a = "settings/appearance"
    e(R.drawable.palette, R.string.enable_high_refresh_rate, ap, a, "hz frame rate smooth")
    e(R.drawable.palette, R.string.enable_landscape_scaling, ap, a, "rotate screen scale")
    e(R.drawable.palette, R.string.enable_dynamic_theme, ap, a, "colors album artwork")
    e(R.drawable.palette, R.string.enable_dynamic_icon, ap, a, "launcher icon color")
    e(R.drawable.palette, R.string.theme, ap, a, "light dark system")
    e(R.drawable.play, R.string.app_style, ap, a, "utaloom glass liquid material3 theme style")
    e(R.drawable.play, R.string.new_mini_player_design, ap, a, "mini player")
    e(R.drawable.play, R.string.new_player_design, ap, a, "player")
    e(R.drawable.play, R.string.player_background_style, ap, a, "gradient blur mesh")
    e(R.drawable.play, R.string.spotify_sp_dc_title, ap, a, "spotify canvas login cookie")
    e(R.drawable.play, R.string.hide_player_thumbnail, ap, a, "hide art cover")
    e(R.drawable.play, R.string.crop_album_art, ap, a, "cover square")
    e(R.drawable.play, R.string.player_buttons_style, ap, a, "round square")
    e(R.drawable.play, R.string.player_slider_style, ap, a, "progress bar wavy squiggly")
    e(R.drawable.play, R.string.enable_swipe_thumbnail, ap, a, "swipe change song")
    e(R.drawable.play, R.string.swipe_sensitivity, ap, a, "swipe threshold")
    e(R.drawable.lyrics, R.string.experimental_lyrics, ap, a, "beta lyrics")
    e(R.drawable.lyrics, R.string.lyrics_glow_effect, ap, a, "glow")
    e(R.drawable.lyrics, R.string.lyrics_animation_style_title, ap, a, "animation fade slide")
    e(R.drawable.lyrics, R.string.lyrics_text_size, ap, a, "font size")
    e(R.drawable.lyrics, R.string.lyrics_line_spacing, ap, a, "spacing")
    e(R.drawable.lyrics, R.string.lyrics_text_position, ap, a, "position left center right")
    e(R.drawable.lyrics, R.string.respect_agent_positioning, ap, a, "duet agent")
    e(R.drawable.lyrics, R.string.lyrics_click_change, ap, a, "tap seek")
    e(R.drawable.lyrics, R.string.lyrics_auto_scroll, ap, a, "follow")
    e(R.drawable.lyrics, R.string.hide_status_bar_fullscreen, ap, a, "fullscreen status bar")
    e(R.drawable.settings, R.string.default_open_tab, ap, a, "home library")
    e(R.drawable.settings, R.string.default_lib_chips, ap, a, "chips library")
    e(R.drawable.settings, R.string.swipe_song_to_add, ap, a, "gesture add")
    e(R.drawable.settings, R.string.swipe_song_to_remove, ap, a, "gesture remove")
    e(R.drawable.settings, R.string.force_bottom_navbar, ap, a, "bottom bar")
    e(R.drawable.settings, R.string.slim_navbar, ap, a, "compact")
    e(R.drawable.settings, R.string.listen_together_in_top_bar, ap, a, "top bar shortcut")
    e(R.drawable.grid_view, R.string.grid_cell_size, ap, a, "grid size")
    e(R.drawable.grid_view, R.string.display_density, ap, a, "density")
    e(R.drawable.cached, R.string.show_liked_playlist, ap, a, "auto playlist")
    e(R.drawable.cached, R.string.show_downloaded_playlist, ap, a, "auto playlist")
    e(R.drawable.cached, R.string.show_top_playlist, ap, a, "auto playlist")
    e(R.drawable.cached, R.string.show_cached_playlist, ap, a, "auto playlist")
    e(R.drawable.cached, R.string.show_uploaded_playlist, ap, a, "auto playlist")

    // Player and audio
    val p = "settings/player"
    e(R.drawable.graphic_eq, R.string.audio_quality, pl, "settings", "bitrate quality wifi mobile data lossless")
    e(R.drawable.graphic_eq, R.string.crossfade, pl, p, "blend")
    e(R.drawable.graphic_eq, R.string.crossfade_duration, pl, p, "seconds")
    e(R.drawable.graphic_eq, R.string.crossfade_gapless, pl, p, "gapless")
    e(R.drawable.history, R.string.history_duration, pl, p, "recently played")
    e(R.drawable.graphic_eq, R.string.skip_silence, pl, p, "quiet")
    e(R.drawable.graphic_eq, R.string.skip_silence_instant, pl, p, "instant")
    e(R.drawable.graphic_eq, R.string.audio_normalization, pl, p, "loudness volume")
    e(R.drawable.graphic_eq, R.string.loudness_level, pl, p, "target db")
    e(R.drawable.graphic_eq, R.string.audio_offload, pl, p, "dsp offload")
    e(R.drawable.speed, R.string.varispeed, pl, p, "speed")
    e(R.drawable.speed, R.string.audio_track_playback_params, pl, p, "tempo pitch")
    e(R.drawable.cast, R.string.google_cast, pl, p, "chromecast")
    e(R.drawable.player_forward, R.string.seek_seconds_addup, pl, p, "double tap forward")
    e(R.drawable.speed, R.string.enable_automatic_sleeptimer, pl, p, "sleep timer")
    e(R.drawable.speed, R.string.sleep_timer_repeat, pl, p, "repeat sleep")
    e(R.drawable.speed, R.string.sleep_timer_stop_after_current_song_title, pl, p, "stop end")
    e(R.drawable.speed, R.string.sleep_timer_fade_out_title, pl, p, "fade")
    e(R.drawable.queue_music, R.string.persistent_queue, pl, p, "restore queue")
    e(R.drawable.queue_music, R.string.auto_load_more, pl, p, "radio")
    e(R.drawable.radio, R.string.auto_radio_queue, pl, p, "radio")
    e(R.drawable.play, R.string.autoplay, pl, p, "similar next")
    e(R.drawable.queue_music, R.string.disable_load_more_when_repeat_all, pl, p, "repeat")
    e(R.drawable.download, R.string.auto_download_on_like, pl, p, "like download")
    e(R.drawable.shuffle, R.string.enable_similar_content, pl, p, "similar content")
    e(R.drawable.shuffle, R.string.persistent_shuffle_title, pl, p, "remember shuffle")
    e(R.drawable.shuffle, R.string.remember_shuffle_and_repeat, pl, p, "remember")
    e(R.drawable.shuffle, R.string.shuffle_playlist_first, pl, p, "shuffle first")
    e(R.drawable.shuffle, R.string.smart_shuffle, pl, p, "smart")
    e(R.drawable.shuffle, R.string.prevent_duplicate_tracks_in_queue, pl, p, "duplicate queue")
    e(R.drawable.player_forward, R.string.auto_skip_next_on_error, pl, p, "error skip")
    e(R.drawable.settings, R.string.stop_music_on_task_clear, pl, p, "recents clear")
    e(R.drawable.volume_mute, R.string.pause_music_when_media_is_muted, pl, p, "mute pause")
    e(R.drawable.settings, R.string.resume_on_bluetooth_connect, pl, p, "bluetooth resume")
    e(R.drawable.settings, R.string.keep_screen_on_when_player_is_expanded, pl, p, "screen wake")

    // Content
    val c = "settings/content"
    e(R.drawable.settings, R.string.offline_mode, ct, c, "no internet")
    e(R.drawable.language, R.string.content_language, ct, c, "language")
    e(R.drawable.language, R.string.content_country, ct, c, "region")
    e(R.drawable.hide_image, R.string.hide_explicit, ct, c, "explicit filter")
    e(R.drawable.hide_image, R.string.hide_video_songs, ct, c, "hide video")
    e(R.drawable.hide_image, R.string.hide_youtube_shorts, ct, c, "shorts")
    e(R.drawable.playlist_add, R.string.add_to_playlist_position, ct, c, "playlist order")
    e(R.drawable.person, R.string.show_artist_description, ct, c, "artist bio")
    e(R.drawable.person, R.string.show_artist_subscriber_count, ct, c, "subscribers")
    e(R.drawable.person, R.string.show_artist_monthly_listeners, ct, c, "listeners")
    e(R.drawable.language, R.string.app_language, ct, c, "app language")
    e(R.drawable.wifi_proxy, R.string.enable_proxy, ct, c, "proxy network")
    e(R.drawable.wifi_proxy, R.string.config_proxy, ct, c, "proxy host port")
    e(R.drawable.lyrics, R.string.lyrics_romanization, ct, c, "romaji hangul pinyin furigana")
    e(R.drawable.newspaper, R.string.show_most_stats_playlists, ct, c, "stats playlist")
    e(R.drawable.newspaper, R.string.show_encore_card, ct, c, "encore recap")
    e(R.drawable.newspaper, R.string.encore_recap_view, ct, c, "recap style")
    e(R.drawable.shuffle, R.string.randomize_home_order, ct, c, "shuffle home")
    e(R.drawable.speed, R.string.top_length, ct, c, "top limit charts")
    e(R.drawable.person, R.string.set_quick_picks, ct, c, "quick picks")

    // Romanization
    val rm = "settings/content/romanization"
    e(R.drawable.lyrics, R.string.lyrics_romanize_as_main, R.string.lyrics_romanization, rm, "romanize main")
    e(R.drawable.lyrics, R.string.line_by_line_option_title, R.string.lyrics_romanization, rm, "line by line")

    // Android Auto
    val aa = "settings/android_auto"
    e(R.drawable.ic_android_auto, R.string.android_auto_target_playlist, R.string.android_auto, aa, "target")
    e(R.drawable.ic_android_auto, R.string.android_auto_youtube_playlists, R.string.android_auto, aa, "show")
    e(R.drawable.ic_android_auto, R.string.android_auto_search_local_songs_limit, R.string.android_auto, aa, "search limit")

    // AI
    val ai = "settings/ai"
    val aiT = R.string.ai_lyrics_translation
    e(R.drawable.translate, R.string.ai_provider, aiT, ai, "openrouter openai deepl")
    e(R.drawable.translate, R.string.ai_translation_mode, aiT, ai, "mode literal free")
    e(R.drawable.translate, R.string.ai_api_key, aiT, ai, "key token")
    e(R.drawable.translate, R.string.ai_base_url, aiT, ai, "endpoint")
    e(R.drawable.translate, R.string.ai_model, aiT, ai, "model")
    e(R.drawable.translate, R.string.ai_system_prompt, aiT, ai, "prompt")
    e(R.drawable.translate, R.string.ai_target_language, aiT, ai, "language")
    e(R.drawable.translate, R.string.ai_deepl_formality, aiT, ai, "formal informal")

    // Privacy
    val pv = "settings/privacy"
    e(R.drawable.history, R.string.pause_listen_history, R.string.privacy, pv, "pause history")
    e(R.drawable.delete_history, R.string.clear_listen_history, R.string.privacy, pv, "clear history")
    e(R.drawable.manage_search, R.string.pause_search_history, R.string.privacy, pv, "pause search")
    e(R.drawable.delete_history, R.string.clear_search_history, R.string.privacy, pv, "clear search")
    e(R.drawable.hide_image, R.string.disable_screenshot, R.string.privacy, pv, "screenshot block")

    // Storage
    val st = "settings/storage"
    e(R.drawable.download, R.string.downloaded_songs, R.string.storage, st, "downloaded size")
    e(R.drawable.download, R.string.download_quality, R.string.storage, st, "download quality")
    e(R.drawable.download, R.string.wifi_only_downloads, R.string.storage, st, "wifi only")
    e(R.drawable.delete_history, R.string.clear_all_downloads, R.string.storage, st, "clear downloads")
    e(R.drawable.cached, R.string.enable_song_cache, R.string.storage, st, "song cache")
    e(R.drawable.cached, R.string.max_song_cache_size, R.string.storage, st, "cache limit")
    e(R.drawable.delete_history, R.string.clear_song_cache, R.string.storage, st, "clear cache")
    e(R.drawable.cached, R.string.max_image_cache_size, R.string.storage, st, "image cache")
    e(R.drawable.delete_history, R.string.clear_image_cache, R.string.storage, st, "clear image")

    // Backup and restore
    val br = "settings/backup_restore"
    e(R.drawable.backup, R.string.action_backup, R.string.backup_restore, br, "backup export")
    e(R.drawable.restore, R.string.action_restore, R.string.backup_restore, br, "restore")
    e(R.drawable.download, R.string.import_online, R.string.backup_restore, br, "import link")
    e(R.drawable.download, R.string.import_csv, R.string.backup_restore, br, "csv")

    // Discord
    val dc = "settings/integrations/discord"
    val dcT = R.string.discord_integration
    e(R.drawable.discord, dcT, R.string.account_integrations, dc, "rich presence")
    e(R.drawable.discord, R.string.enable_discord_rpc, dcT, dc, "rich presence")
    e(R.drawable.discord, R.string.discord_advanced_mode, dcT, dc, "custom")
    e(R.drawable.discord, R.string.discord_activity_type, dcT, dc, "activity")
    e(R.drawable.discord, R.string.discord_activity_name, dcT, dc, "name")
    e(R.drawable.discord, R.string.discord_state, dcT, dc, "state")
    e(R.drawable.discord, R.string.discord_details, dcT, dc, "details")
    e(R.drawable.discord, R.string.discord_status, dcT, dc, "status")

    // Last.fm
    val lf = "settings/integrations/lastfm"
    val lfT = R.string.lastfm_integration
    e(R.drawable.person, lfT, R.string.account_integrations, lf, "last.fm scrobble")
    e(R.drawable.person, R.string.enable_scrobbling, lfT, lf, "scrobble")
    e(R.drawable.person, R.string.lastfm_now_playing, lfT, lf, "now playing")
    e(R.drawable.person, R.string.last_fm_send_likes, lfT, lf, "loved sync")
    e(R.drawable.person, R.string.scrobble_min_track_duration, lfT, lf, "min duration")
    e(R.drawable.person, R.string.scrobble_delay_percent, lfT, lf, "delay percent")
    e(R.drawable.person, R.string.scrobble_delay_minutes, lfT, lf, "delay minutes")

    // Listen Together
    val lt = "settings/integrations/listen_together"
    val ltT = R.string.listen_together
    e(R.drawable.person, ltT, R.string.account_integrations, lt, "party room")
    e(R.drawable.person, R.string.listen_together_server_url, ltT, lt, "server")
    e(R.drawable.person, R.string.listen_together_username, ltT, lt, "username")
    e(R.drawable.person, R.string.listen_together_auto_approval_joins, ltT, lt, "auto join")
    e(R.drawable.person, R.string.listen_together_auto_approval_suggestions, ltT, lt, "auto suggest")
    e(R.drawable.person, R.string.listen_together_sync_volume, ltT, lt, "sync volume")
    e(R.drawable.person, R.string.listen_together_view_logs, ltT, lt, "logs")
    e(R.drawable.person, R.string.listen_together_blocked_users, ltT, lt, "block")
}.onEach {
    it.title = context.getString(it.titleRes)
    it.page = context.getString(it.pageRes)
}
