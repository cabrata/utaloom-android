/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.Preferences
import com.metrolist.music.constants.EnableBetterLyricsKey
import com.metrolist.music.constants.EnableKugouKey
import com.metrolist.music.constants.EnableLrcLibKey
import com.metrolist.music.constants.EnableLyricsPlus
import com.metrolist.music.constants.EnablePaxsenixKey
import com.metrolist.music.constants.EnableYouTubeLyricsKey
import com.metrolist.music.constants.EnableYouTubeSubtitleLyricsKey
import com.metrolist.music.constants.EnableZemerKey
import com.metrolist.music.constants.LyricsProviderOrderKey

object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "BetterLyrics" to BetterLyricsProvider,
        "Paxsenix" to PaxsenixLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "KuGou" to KuGouLyricsProvider,
        "LyricsPlus" to LyricsPlusProvider,
        "Zemer" to ZemerLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTube" to YouTubeLyricsProvider,
    )

    val providerNames = providerMap.keys.toList()

    val providerEnabledKeys = mapOf(
        "BetterLyrics" to EnableBetterLyricsKey,
        "LrcLib" to EnableLrcLibKey,
        "KuGou" to EnableKugouKey,
        "Paxsenix" to EnablePaxsenixKey,
        "LyricsPlus" to EnableLyricsPlus,
        "Zemer" to EnableZemerKey,
        "YouTubeSubtitle" to EnableYouTubeSubtitleLyricsKey,
        "YouTube" to EnableYouTubeLyricsKey,
    )

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun getProviderName(provider: LyricsProvider): String? =
        providerMap.entries.find { it.value == provider }?.key

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) {
            return getDefaultProviderOrder()
        }
        val saved = orderString.split(",").map { it.trim() }.filter { it in providerNames }.distinct()
        return saved + getDefaultProviderOrder().filter { it !in saved }
    }

    fun serializeProviderOrder(providers: List<String>): String {
        return providers.filter { it in providerNames }.distinct().joinToString(",")
    }

    fun getDefaultProviderOrder(): List<String> = listOf(
        "BetterLyrics",
        "LrcLib",
        "KuGou",
        "Paxsenix",
        "LyricsPlus",
        "Zemer",
        "YouTubeSubtitle",
        "YouTube",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> {
        val order = deserializeProviderOrder(orderString)
        return order.mapNotNull { getProviderByName(it) }
    }

    fun getEnabledProviders(preferences: Preferences): List<LyricsProvider> =
        deserializeProviderOrder(preferences[LyricsProviderOrderKey].orEmpty())
            .filter { preferences[providerEnabledKeys.getValue(it)] ?: true }
            .mapNotNull(::getProviderByName)
}
