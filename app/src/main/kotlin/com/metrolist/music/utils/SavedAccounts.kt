/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.SavedYouTubeAccountsKey
import com.metrolist.music.constants.VisitorDataKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One signed-in YouTube Music identity: the same fields the active login keeps, plus its avatar. */
data class SavedAccount(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
    val name: String,
    val email: String,
    val channelHandle: String,
    val avatar: String?,
) {
    /** A Google login's brand channels share one cookie, so the channel's dataSyncId tells them apart. */
    val id: String get() = dataSyncId.ifBlank { "$email\n$name" }
}

fun Preferences.savedAccounts(): List<SavedAccount> = decodeSavedAccounts(this[SavedYouTubeAccountsKey])

/** The account the active login keys describe, or null when signed out. */
fun Preferences.activeAccount(avatar: String? = null): SavedAccount? {
    val cookie = this[InnerTubeCookieKey].orEmpty()
    if (parseCookieString(cookie)["SAPISID"].isNullOrBlank()) return null
    return SavedAccount(
        cookie = cookie,
        visitorData = this[VisitorDataKey].orEmpty(),
        dataSyncId = this[DataSyncIdKey].orEmpty(),
        authUser = this[InnerTubeAuthUserKey] ?: "0",
        name = this[AccountNameKey].orEmpty(),
        email = this[AccountEmailKey].orEmpty(),
        channelHandle = this[AccountChannelHandleKey].orEmpty(),
        avatar = avatar,
    )
}

/** Records the active login in the saved list, keeping an avatar already known for it. */
fun MutablePreferences.saveActiveAccount(avatar: String? = null) {
    val active = activeAccount(avatar) ?: return
    val saved = savedAccounts()
    val previous = saved.firstOrNull { it.id == active.id }
    val merged = active.copy(avatar = avatar ?: previous?.avatar)
    this[SavedYouTubeAccountsKey] = encodeSavedAccounts(
        if (previous == null) saved + merged else saved.map { if (it.id == active.id) merged else it },
    )
}

/** Drops the active login from the saved list; the caller clears the active keys. */
fun MutablePreferences.forgetActiveAccount() {
    val active = activeAccount() ?: return
    this[SavedYouTubeAccountsKey] = encodeSavedAccounts(savedAccounts().filterNot { it.id == active.id })
}

fun MutablePreferences.removeSavedAccount(id: String) {
    this[SavedYouTubeAccountsKey] = encodeSavedAccounts(savedAccounts().filterNot { it.id == id })
}

/** Makes [account] the active login. The app restarts afterwards so every screen reloads as it. */
fun MutablePreferences.activateAccount(account: SavedAccount) {
    this[InnerTubeCookieKey] = account.cookie
    this[VisitorDataKey] = account.visitorData
    this[DataSyncIdKey] = account.dataSyncId
    this[InnerTubeAuthUserKey] = account.authUser
    this[AccountNameKey] = account.name
    this[AccountEmailKey] = account.email
    this[AccountChannelHandleKey] = account.channelHandle
}

/** Cold-starts the app, as login does, so no view model keeps the previous account's data. */
fun restartApp(context: Context) {
    context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        ?.let(context::startActivity)
    Runtime.getRuntime().exit(0)
}

internal fun encodeSavedAccounts(accounts: List<SavedAccount>): String = JSONArray().apply {
    accounts.forEach { account ->
        put(
            JSONObject()
                .put("cookie", account.cookie)
                .put("visitorData", account.visitorData)
                .put("dataSyncId", account.dataSyncId)
                .put("authUser", account.authUser)
                .put("name", account.name)
                .put("email", account.email)
                .put("channelHandle", account.channelHandle)
                .put("avatar", account.avatar ?: JSONObject.NULL),
        )
    }
}.toString()

internal fun decodeSavedAccounts(json: String?): List<SavedAccount> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            SavedAccount(
                cookie = item.optString("cookie").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                visitorData = item.optString("visitorData"),
                dataSyncId = item.optString("dataSyncId"),
                authUser = item.optString("authUser", "0"),
                name = item.optString("name"),
                email = item.optString("email"),
                channelHandle = item.optString("channelHandle"),
                avatar = item.optString("avatar").takeIf { !item.isNull("avatar") && it.isNotBlank() },
            )
        }
    } catch (e: JSONException) {
        reportException(e)
        emptyList()
    }
}
