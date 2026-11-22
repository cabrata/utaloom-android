/*
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */
package com.metrolist.music.ui.glass

import androidx.compose.foundation.background
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.AppBarHeight

/**
 * Apple Music's top of a tab: the large title on the left and round glass controls on the right,
 * over a fade from the page colour so content scrolling up dissolves under the status bar.
 * Sits outside the recorded page, so its buttons are real liquid glass.
 */
@Composable
fun GlassTopBar(
    title: String,
    accountImageUrl: String?,
    onMenuClick: () -> Unit,
    onAccountClick: () -> Unit,
    hasUpdateBadge: Boolean,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    val page = MaterialTheme.colorScheme.background
    val fade = remember(page) {
        Brush.verticalGradient(0f to page, 0.75f to page.copy(alpha = 0.94f), 1f to page.copy(alpha = 0f))
    }
    // Scrolled: the profile slides off the edge and the menu takes its slot; at the top both return.
    val slide by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "topBarSlide",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(fade)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(AppBarHeight)
            .padding(start = 20.dp, end = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        GlassCircleButton(
            onClick = onMenuClick,
            modifier = Modifier.offset { IntOffset((slide * (BUTTON_SIZE + BUTTON_GAP).toPx()).roundToInt(), 0) },
        ) {
            Icon(
                painter = painterResource(R.drawable.menu),
                contentDescription = stringResource(R.string.open_menu),
                tint = glassContentColor(),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(BUTTON_GAP))
        val profileModifier = Modifier
            .offset { IntOffset((slide * (BUTTON_SIZE + 24.dp).toPx()).roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - slide }
        BadgedBox(badge = { if (hasUpdateBadge) Badge() }, modifier = profileModifier) {
            if (accountImageUrl != null) {
                // The photo itself is the button; a glass ring around it only adds clutter.
                AsyncImage(
                    model = accountImageUrl,
                    contentDescription = stringResource(R.string.account),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(BUTTON_SIZE)
                        .clip(CircleShape)
                        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, CircleShape)
                        .clickable(role = Role.Button, onClick = onAccountClick),
                )
            } else {
                GlassCircleButton(onClick = onAccountClick) {
                    Icon(
                        painter = painterResource(R.drawable.account),
                        contentDescription = stringResource(R.string.account),
                        tint = glassContentColor(),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private val BUTTON_SIZE = 40.dp
private val BUTTON_GAP = 10.dp

@Composable
private fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(BUTTON_SIZE)
            .liquidGlass(CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        content = content,
    )
}
