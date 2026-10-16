/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.canvas.extractSpDc
import com.metrolist.music.constants.SpotifySpDcCookieKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasCaptured by remember { mutableStateOf(false) }

    fun checkAndSaveCookie(url: String?) {
        if (hasCaptured) return
        val cookieManager = CookieManager.getInstance()
        val cookie1 = cookieManager.getCookie(url ?: "https://open.spotify.com").orEmpty()
        val cookie2 = cookieManager.getCookie("https://open.spotify.com").orEmpty()
        val cookie3 = cookieManager.getCookie("https://accounts.spotify.com").orEmpty()
        val fullCookies = "$cookie1; $cookie2; $cookie3"

        val spDc = extractSpDc(fullCookies)
        if (!spDc.isNullOrBlank()) {
            hasCaptured = true
            coroutineScope.launch {
                context.safeDataStoreEdit { settings ->
                    settings[SpotifySpDcCookieKey] = spDc
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.spotify_login_success),
                    Toast.LENGTH_SHORT,
                ).show()
                navController.navigateUp()
            }
        }
    }

    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            navController.navigateUp()
        }
    }

    Box(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webViewContext ->
                WebView(webViewContext).apply {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme
                            if (scheme == "http" || scheme == "https") {
                                checkAndSaveCookie(request.url.toString())
                                return false
                            }
                            return true
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            checkAndSaveCookie(url)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            checkAndSaveCookie(url)
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            checkAndSaveCookie(url)
                        }

                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            Timber.tag("SpotifyLogin").w("SSL Error ignored: %s", error)
                            handler?.proceed()
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    webViewRef = this
                    loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                }
            },
        )

        if (isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        TopAppBar(
            title = { Text(stringResource(R.string.spotify_login_title)) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                    )
                }
            },
        )
    }
}
