package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.EnableGoogleCastKey
import com.metrolist.music.ui.player.OutputRow
import com.metrolist.music.utils.rememberPreference

/**
 * GMS build: a Google Cast row in the output drawer that expands the receiver
 * picker inline. Scanning runs only while it is open.
 */
@Composable
fun AudioOutputExtraRows() {
    val playerConnection = LocalPlayerConnection.current
    val castHandler = playerConnection?.service?.castConnectionHandler
    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val (castEnabled) = rememberPreference(EnableGoogleCastKey, defaultValue = true)
    var expanded by remember { mutableStateOf(false) }

    if (!castEnabled) {
        return
    }

    val castName by castHandler?.castDeviceName?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    OutputRow(
        title = castName ?: stringResource(R.string.google_cast),
        subtitle = stringResource(if (isCasting) R.string.cast_casting else R.string.cast_row_subtitle),
        iconRes = if (isCasting) R.drawable.cast_connected else R.drawable.cast,
        active = isCasting,
        chevron = true,
        onClick = { expanded = !expanded },
    )

    if (expanded) {
        if (isCasting) {
            Text(
                text = stringResource(R.string.cast_stop),
                color = Color(0xFFFF6B63),
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        castHandler?.disconnect()
                        playerConnection?.let { connection ->
                            if (connection.player.playbackState != androidx.media3.common.Player.STATE_IDLE) {
                                connection.player.playWhenReady = true
                            }
                        }
                    }
                    .padding(start = 40.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            )
        } else {
            CastPicker()
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Receivers on this network, discovered only while shown since active scans cost battery. */
@Composable
private fun CastPicker() {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context) }
    val selector = remember(context) {
        runCatching { CastContext.getSharedInstance(context).mergedSelector }.getOrNull()
    }
    var routes by remember { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }

    DisposableEffect(router, selector) {
        if (selector == null) return@DisposableEffect onDispose {}
        fun refresh() {
            routes = router.routes.filter { !it.isDefaultOrBluetooth && it.isEnabled && it.matchesSelector(selector) }
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
            override fun onRouteUnselected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
        onDispose { router.removeCallback(callback) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (routes.isEmpty()) {
            CastSearching()
        } else {
            routes.forEach { route ->
                CastDeviceRow(route) {
                    if (!route.isSelected) route.select()
                }
            }
        }
    }
}

@Composable
private fun CastDeviceRow(route: MediaRouter.RouteInfo, onClick: () -> Unit) {
    val connecting = route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
    val connected = route.isSelected && route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 40.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalContentColor.current.copy(alpha = if (connected) 0.16f else 0.08f)),
        ) {
            Icon(
                painter = painterResource(if (connected) R.drawable.cast_connected else R.drawable.cast),
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = if (connected) 1f else 0.8f),
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = route.name,
                color = LocalContentColor.current,
                fontSize = 15.sp,
                fontWeight = if (connected) FontWeight.W600 else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connecting || connected) {
                Text(
                    text = stringResource(if (connecting) R.string.cast_connecting else R.string.cast_casting),
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    fontSize = 12.5.sp,
                )
            }
        }
        when {
            connecting -> CircularProgressIndicator(
                color = LocalContentColor.current.copy(alpha = 0.8f),
                strokeWidth = 1.75.dp,
                modifier = Modifier.size(16.dp),
            )
            connected -> Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CastSearching() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        CircularProgressIndicator(
            color = LocalContentColor.current.copy(alpha = 0.7f),
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.cast_searching), color = LocalContentColor.current, fontSize = 15.sp)
        Text(
            text = stringResource(R.string.cast_searching_hint),
            color = LocalContentColor.current.copy(alpha = 0.6f),
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
