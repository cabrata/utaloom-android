package com.metrolist.music.utils

import android.app.Application
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeCookieKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, manifest = Config.NONE)
class SavedAccountsTest {
    private fun login(cookie: String, dataSyncId: String, name: String) = mutablePreferencesOf().apply {
        this[InnerTubeCookieKey] = cookie
        this[DataSyncIdKey] = dataSyncId
        this[AccountNameKey] = name
    }

    @Test
    fun `saving keeps one entry per channel and switching restores its keys`() {
        val prefs = login("SAPISID=a", "chan-a", "Alice")
        prefs.saveActiveAccount("https://avatar/a")
        prefs.saveActiveAccount()
        prefs[InnerTubeCookieKey] = "SAPISID=b"
        prefs[DataSyncIdKey] = "chan-b"
        prefs[AccountNameKey] = "Bob"
        prefs.saveActiveAccount()

        val saved = prefs.savedAccounts()
        assertEquals(listOf("chan-a", "chan-b"), saved.map { it.id })
        assertEquals("https://avatar/a", saved.first().avatar)

        prefs.activateAccount(saved.first())
        assertEquals("SAPISID=a", prefs[InnerTubeCookieKey])
        assertEquals("Alice", prefs[AccountNameKey])
    }

    @Test
    fun `logging out forgets only the active account and signed out saves nothing`() {
        val prefs = login("SAPISID=a", "chan-a", "Alice")
        prefs.saveActiveAccount()
        prefs.activateAccount(prefs.savedAccounts().first().copy(cookie = "SAPISID=b", dataSyncId = "chan-b"))
        prefs.saveActiveAccount()
        prefs.forgetActiveAccount()
        assertEquals(listOf("chan-a"), prefs.savedAccounts().map { it.id })

        val signedOut = login("", "", "")
        signedOut.saveActiveAccount()
        assertNull(signedOut.activeAccount())
        assertEquals(emptyList<SavedAccount>(), signedOut.savedAccounts())
    }
}
