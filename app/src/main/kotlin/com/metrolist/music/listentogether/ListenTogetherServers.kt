/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.listentogether

import androidx.datastore.preferences.core.MutablePreferences
import com.metrolist.music.constants.ListenTogetherIsHostKey
import com.metrolist.music.constants.ListenTogetherRoomCodeKey
import com.metrolist.music.constants.ListenTogetherServerUrlKey
import com.metrolist.music.constants.ListenTogetherSessionTimestampKey
import com.metrolist.music.constants.ListenTogetherSessionTokenKey
import com.metrolist.music.constants.ListenTogetherUserIdKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String
)

object ListenTogetherServers {
    private const val ServersJson = """
        [
          {
            "name": "Utaloom",
            "url": "wss://utaloom.caliph.dev/ws",
            "location": "utaloom.caliph.dev",
            "operator": "cabrata"
          }
        ]
    """

    private val json = Json { ignoreUnknownKeys = true }

    val servers: List<ListenTogetherServer> by lazy {
        json.decodeFromString(ServersJson)
    }

    val defaultServerUrl: String
        get() = servers.first().url

    internal fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return defaultServerUrl
        val host = runCatching { URI(trimmed).host }.getOrNull()
        return if (host.equals("metroserver.meowery.eu", ignoreCase = true) ||
            host.equals("metroserverx.meowery.eu", ignoreCase = true) ||
            host.equals("metrolist.caliph.dev", ignoreCase = true)
        ) defaultServerUrl else trimmed
    }

    internal fun migratePreferences(preferences: MutablePreferences) {
        val configured = preferences[ListenTogetherServerUrlKey]
        val normalized = normalizeUrl(configured.orEmpty())
        if (normalized == configured) return
        preferences[ListenTogetherServerUrlKey] = normalized
        if (configured != null && normalized == configured.trim()) return
        // Room credentials belong to their original server, not the new endpoint.
        preferences.remove(ListenTogetherSessionTokenKey)
        preferences.remove(ListenTogetherRoomCodeKey)
        preferences.remove(ListenTogetherUserIdKey)
        preferences.remove(ListenTogetherIsHostKey)
        preferences.remove(ListenTogetherSessionTimestampKey)
    }

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
