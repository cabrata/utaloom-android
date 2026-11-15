/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.music.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AppDrawerEntry(
    val label: String,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

/**
 * Modal drawer laid over the app; composed only while [visible] so its closed sheet never reaches
 * TalkBack or swallows edge swipes meant for the screens underneath.
 */
@Composable
fun AppDrawer(
    visible: Boolean,
    entries: List<AppDrawerEntry>,
    onDismiss: () -> Unit,
    pureBlack: Boolean = false,
) {
    if (!visible) return
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) {
        try {
            state.open()
        } catch (e: CancellationException) {
            // A tap on the scrim mid-animation interrupts open(); still wait for the close below.
            coroutineContext.ensureActive()
        }
        snapshotFlow { state.currentValue }.first { it == DrawerValue.Closed }
        onDismiss()
    }

    ModalNavigationDrawer(
        drawerState = state,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = state,
                drawerContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    Row(
                        Modifier
                            .semantics(mergeDescendants = true) { heading() }
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UtaloomLogo(Modifier.size(44.dp))
                        Text(
                            text = stringResource(R.string.app_name_brand),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    entries.forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(entry.label) },
                            icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                            selected = false,
                            onClick = {
                                entry.onClick()
                                scope.launch { state.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                            ),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        content = {},
    )
}

/** Utaloom mark on its dark badge; decorative, callers pair it with the brand name. */
@Composable
fun UtaloomLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = null,
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF1C1B1F))
            .padding(7.dp),
    )
}
