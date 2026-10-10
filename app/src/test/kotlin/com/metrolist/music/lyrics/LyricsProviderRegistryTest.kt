package com.metrolist.music.lyrics

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LyricsProviderRegistryTest {
    private val registry = LyricsProviderRegistry
    private val desktopOrder = listOf(
        "BetterLyrics", "LrcLib", "KuGou", "Paxsenix", "LyricsPlus", "Zemer", "YouTubeSubtitle", "YouTube",
    )

    @Test
    fun `fresh settings enable all desktop providers in desktop order`() {
        assertEquals(desktopOrder, registry.getDefaultProviderOrder())
        assertEquals(desktopOrder.toSet(), registry.providerNames.toSet())
        assertEquals(desktopOrder.toSet(), registry.providerEnabledKeys.keys)
        assertEquals(
            desktopOrder,
            registry.getEnabledProviders(emptyPreferences()).map(registry::getProviderName),
        )
    }

    @Test
    fun `old partial orders retain priority and append missing providers once`() {
        assertEquals(
            listOf("KuGou", "LrcLib") + desktopOrder.filter { it !in listOf("KuGou", "LrcLib") },
            registry.deserializeProviderOrder(" KuGou,unknown,LrcLib,KuGou, "),
        )
        assertEquals(desktopOrder, registry.deserializeProviderOrder(" "))
        assertEquals(desktopOrder, registry.deserializeProviderOrder("unknown"))
        assertEquals("YouTube,LrcLib", registry.serializeProviderOrder(listOf("YouTube", "unknown", "LrcLib", "YouTube")))
    }

    @Test
    fun `every provider including both YouTube sources can be disabled independently`() {
        for ((id, key) in registry.providerEnabledKeys) {
            val preferences = mutablePreferencesOf(key to false)
            val enabled = registry.getEnabledProviders(preferences).map(registry::getProviderName)
            assertEquals(desktopOrder.filter { it != id }, enabled)
        }
        val preferences = mutablePreferencesOf()
        registry.providerEnabledKeys.values.forEach { preferences[it] = false }
        assertTrue(registry.getEnabledProviders(preferences).isEmpty())
    }

    @Test
    fun `disabled provider keeps its priority when enabled again`() {
        val order = listOf("YouTube", "YouTubeSubtitle") + desktopOrder.filter { !it.startsWith("YouTube") }
        val preferences = mutablePreferencesOf(LyricsProviderOrderKey to registry.serializeProviderOrder(order))
        val youtubeKey = registry.providerEnabledKeys.getValue("YouTube")
        preferences[youtubeKey] = false
        assertEquals(order.drop(1), registry.getEnabledProviders(preferences).map(registry::getProviderName))
        preferences[youtubeKey] = true
        assertEquals(order, registry.getEnabledProviders(preferences).map(registry::getProviderName))
    }

    @Test
    fun `lyrics search cache separates query changes and enabled provider order`() {
        val query = LyricsSearchQuery("video", "title", "artist", 180, "album", desktopOrder)
        assertEquals(query, query.copy())
        assertNotEquals(query, query.copy(mediaId = "another-video"))
        assertNotEquals(query, query.copy(title = "corrected-title"))
        assertNotEquals(query, query.copy(artist = "corrected-artist"))
        assertNotEquals(query, query.copy(duration = 181))
        assertNotEquals(query, query.copy(album = null))
        assertNotEquals(query, query.copy(providers = desktopOrder.drop(1)))
        assertNotEquals(query, query.copy(providers = desktopOrder.reversed()))
    }

    @Test
    fun `saved switches and order are honored by providers and helper`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = context.dataStore.data.first()
        val order = desktopOrder.reversed()
        try {
            assertTrue(context.safeDataStoreEdit { preferences ->
                preferences[LyricsProviderOrderKey] = registry.serializeProviderOrder(order)
                registry.providerEnabledKeys.values.forEach { preferences[it] = true }
                preferences[registry.providerEnabledKeys.getValue("YouTube")] = false
                preferences[registry.providerEnabledKeys.getValue("YouTubeSubtitle")] = false
            })
            assertFalse(YouTubeLyricsProvider.isEnabled(context))
            assertFalse(YouTubeSubtitleLyricsProvider.isEnabled(context))
            assertTrue(LyricsPlusProvider.isEnabled(context))
            assertTrue(context.safeDataStoreEdit { preferences ->
                registry.providerEnabledKeys.values.forEach { preferences.remove(it) }
            })
            desktopOrder.forEach { assertTrue(registry.getProviderByName(it)!!.isEnabled(context)) }
            assertTrue(context.safeDataStoreEdit { preferences ->
                preferences[registry.providerEnabledKeys.getValue("YouTube")] = false
                preferences[registry.providerEnabledKeys.getValue("YouTubeSubtitle")] = false
            })
            val observer = com.metrolist.music.utils.NetworkConnectivityObserver(context)
            try {
                val helper = LyricsHelper(context, observer)
                assertEquals(
                    order.filter { !it.startsWith("YouTube") },
                    helper.preferred.first().map(registry::getProviderName),
                )
            } finally {
                observer.unregister()
            }
        } finally {
            context.safeDataStoreEdit { preferences ->
                preferences.remove(LyricsProviderOrderKey)
                original[LyricsProviderOrderKey]?.let { preferences[LyricsProviderOrderKey] = it }
                for (key in registry.providerEnabledKeys.values) {
                    preferences.remove(key)
                    original[key]?.let { preferences[key] = it }
                }
            }
        }
    }
}
