package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.EnableGoogleCastKey
import com.metrolist.music.utils.rememberPreference

@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val playerConnection = LocalPlayerConnection.current
    val castHandler = playerConnection?.service?.castConnectionHandler
    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val (enabled) = rememberPreference(EnableGoogleCastKey, defaultValue = true)
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (enabled) {
            castHandler?.initialize()
        } else if (isCasting) {
            castHandler?.disconnect()
        }
    }

    if (!enabled) return

    IconButton(onClick = { showDialog = true }, modifier = modifier) {
        Icon(
            painter = painterResource(if (isCasting) R.drawable.cast_connected else R.drawable.cast),
            contentDescription = stringResource(R.string.google_cast),
            tint = if (isCasting) Color.White else tintColor,
            modifier = Modifier.size(26.dp),
        )
    }

    if (showDialog) {
        CastSheet(
            isCasting = isCasting,
            onStop = { castHandler?.disconnect() },
            onDismiss = { showDialog = false },
        )
    }
}

/** Receivers on this network, discovered only while the sheet is open since active scans cost battery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastSheet(
    isCasting: Boolean,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context) }
    val selector = remember(context) { runCatching { CastContext.getSharedInstance(context).mergedSelector }.getOrNull() }
    var routes by remember { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }
    // Set on tap so the sheet closes itself once that receiver connects, not when opened mid-cast.
    var picked by remember { mutableStateOf(false) }

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
    LaunchedEffect(picked, isCasting) {
        if (picked && isCasting) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.google_cast),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W600,
                )
                Text(
                    text = stringResource(R.string.cast_dialog_subtitle),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            CastRule()
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (routes.isEmpty()) {
                    CastSearching()
                } else {
                    routes.forEach { route ->
                        CastDeviceRow(route) {
                            if (!route.isSelected) {
                                picked = true
                                route.select()
                            }
                        }
                    }
                }
                if (isCasting) {
                    CastRule()
                    Text(
                        text = stringResource(R.string.cast_stop),
                        color = Color(0xFFFF6B63),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W600,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                onStop()
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (connected) 0.16f else 0.08f)),
        ) {
            Icon(
                painter = painterResource(if (connected) R.drawable.cast_connected else R.drawable.cast),
                contentDescription = null,
                tint = Color.White.copy(alpha = if (connected) 1f else 0.8f),
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = route.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (connected) FontWeight.W600 else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connecting || connected) {
                Text(
                    text = stringResource(if (connecting) R.string.cast_connecting else R.string.cast_casting),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.5.sp,
                )
            }
        }
        when {
            connecting -> CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 1.75.dp,
                modifier = Modifier.size(16.dp),
            )
            connected -> Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CastSearching() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        CircularProgressIndicator(
            color = Color.White.copy(alpha = 0.7f),
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.cast_searching), color = Color.White, fontSize = 15.sp)
        Text(
            text = stringResource(R.string.cast_searching_hint),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CastRule() = HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
