package com.metrolist.music.listentogether

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.ListenTogetherIsHostKey
import com.metrolist.music.constants.ListenTogetherRoomCodeKey
import com.metrolist.music.constants.ListenTogetherServerUrlKey
import com.metrolist.music.constants.ListenTogetherSessionTimestampKey
import com.metrolist.music.constants.ListenTogetherSessionTokenKey
import com.metrolist.music.constants.ListenTogetherUserIdKey
import com.metrolist.music.constants.ListenTogetherUsernameKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListenTogetherServersTest {
    @Test
    fun `default is Utaloom and migrating legacy endpoints clears only room credentials`() {
        val expected = "wss://utaloom.caliph.dev/ws"
        assertEquals(expected, ListenTogetherServers.defaultServerUrl)
        assertEquals("cabrata", ListenTogetherServers.findByUrl(expected)?.operator)

        for (oldUrl in listOf(null, "", "wss://metroserver.meowery.eu/ws", "wss://metroserverx.meowery.eu/ws")) {
            val preferences = mutablePreferencesOf(
                ListenTogetherUsernameKey to "listener",
                ListenTogetherSessionTokenKey to "old-server-token",
                ListenTogetherRoomCodeKey to "OLDROOM",
                ListenTogetherUserIdKey to "old-user",
                ListenTogetherIsHostKey to true,
                ListenTogetherSessionTimestampKey to 100L,
            )
            oldUrl?.let { preferences[ListenTogetherServerUrlKey] = it }

            ListenTogetherServers.migratePreferences(preferences)

            assertEquals(expected, preferences[ListenTogetherServerUrlKey])
            assertEquals("listener", preferences[ListenTogetherUsernameKey])
            assertNull(preferences[ListenTogetherSessionTokenKey])
            assertNull(preferences[ListenTogetherRoomCodeKey])
            assertNull(preferences[ListenTogetherUserIdKey])
            assertNull(preferences[ListenTogetherIsHostKey])
            assertNull(preferences[ListenTogetherSessionTimestampKey])
        }
    }

    @Test
    fun `current and custom servers retain their room sessions`() {
        for (url in listOf(
            "wss://utaloom.caliph.dev/ws",
            "wss://private.example/ws",
            "wss://metroserverx.meowery.eu.example/ws",
            "wss://private.example/ws?next=metroserver.meowery.eu",
        )) {
            val preferences = mutablePreferencesOf(
                ListenTogetherServerUrlKey to " $url ",
                ListenTogetherSessionTokenKey to "same-server-token",
                ListenTogetherRoomCodeKey to "SAMEROOM",
            )

            ListenTogetherServers.migratePreferences(preferences)

            assertEquals(url, preferences[ListenTogetherServerUrlKey])
            assertEquals("same-server-token", preferences[ListenTogetherSessionTokenKey])
            assertEquals("SAMEROOM", preferences[ListenTogetherRoomCodeKey])
        }
    }
}
