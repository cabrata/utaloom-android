/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.ui.screens.WebLoginHeader
import com.metrolist.music.canvas.extractSpDc
import com.metrolist.music.constants.SpotifySpDcCookieKey
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<Int?>(null) }
    var hasCaptured by remember { mutableStateOf(false) }

    fun checkAndSaveCookie() {
        if (hasCaptured) return
        val cookieManager = CookieManager.getInstance()
        val cookie2 = cookieManager.getCookie("https://open.spotify.com").orEmpty()
        val cookie3 = cookieManager.getCookie("https://accounts.spotify.com").orEmpty()
        val fullCookies = "$cookie2; $cookie3"

        val spDc = extractSpDc(fullCookies)
        if (!spDc.isNullOrBlank()) {
            hasCaptured = true
            coroutineScope.launch {
                val saved = context.safeDataStoreEdit { settings ->
                    settings[SpotifySpDcCookieKey] = spDc
                }
                if (!saved) {
                    hasCaptured = false
                    Toast.makeText(context, R.string.spotify_login_save_error, Toast.LENGTH_LONG).show()
                    return@launch
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

    fun goBack() {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            navController.navigateUp()
        }
    }

    fun reload() {
        webViewRef?.let { view ->
            loadError = null
            progress = 0
            isLoading = true
            view.reload()
        }
    }

    BackHandler(onBack = ::goBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        WebLoginHeader(
            title = stringResource(R.string.spotify_login_title),
            subtitle = stringResource(
                when {
                    hasCaptured -> R.string.spotify_login_saving
                    loadError != null -> R.string.spotify_login_error_hint
                    isLoading -> R.string.spotify_login_loading
                    else -> R.string.spotify_login_instructions
                },
            ),
            onBack = navController::navigateUp,
        ) {
            IconButton(onClick = ::reload, enabled = webViewRef != null) {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = stringResource(R.string.spotify_login_reload),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            allowFileAccess = false
                            allowContentAccess = false
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme
                                if (scheme == "http" || scheme == "https") {
                                    checkAndSaveCookie()
                                    return false
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loadError = null
                                progress = 0
                                isLoading = true
                                checkAndSaveCookie()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                checkAndSaveCookie()
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                checkAndSaveCookie()
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = R.string.spotify_login_load_error
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = R.string.spotify_login_load_error
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.cancel()
                                isLoading = false
                                loadError = R.string.spotify_login_tls_error
                            }
                        }

                        webViewRef = this
                        loadUrl("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                    }
                },
                update = { view ->
                    view.visibility = if (loadError == null) View.VISIBLE else View.INVISIBLE
                },
                onRelease = { view ->
                    if (webViewRef === view) webViewRef = null
                    view.destroy()
                },
            )

            if (loadError == null && (isLoading || hasCaptured)) {
                val progressModifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                if (hasCaptured || progress == 0) {
                    LinearProgressIndicator(modifier = progressModifier)
                } else {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = progressModifier,
                    )
                }
            }

            loadError?.let { error ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.error),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.spotify_login_error_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = ::reload) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}
