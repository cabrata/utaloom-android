/*
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */
package com.metrolist.music.ui.glass

import androidx.compose.foundation.background
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
    modifier: Modifier = Modifier,
) {
    val page = MaterialTheme.colorScheme.background
    val fade = remember(page) {
        Brush.verticalGradient(0f to page, 0.75f to page.copy(alpha = 0.94f), 1f to page.copy(alpha = 0f))
    }
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
        GlassCircleButton(onClick = onMenuClick) {
            Icon(
                painter = painterResource(R.drawable.menu),
                contentDescription = stringResource(R.string.open_menu),
                tint = glassContentColor(),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        GlassCircleButton(onClick = onAccountClick) {
            BadgedBox(badge = { if (hasUpdateBadge) Badge() }) {
                if (accountImageUrl != null) {
                    AsyncImage(
                        model = accountImageUrl,
                        contentDescription = stringResource(R.string.account),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp).clip(CircleShape),
                    )
                } else {
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

@Composable
private fun GlassCircleButton(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .liquidGlass(CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        content = content,
    )
}
