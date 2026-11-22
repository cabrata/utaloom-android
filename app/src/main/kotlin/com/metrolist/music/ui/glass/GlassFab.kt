package com.metrolist.music.ui.glass

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.GlassShapes

/** One action of a [GlassFabRow] or a standalone [GlassFab]. */
class GlassFabAction(
    @DrawableRes val icon: Int,
    val contentDescription: String?,
    val onClick: () -> Unit,
)

/**
 * A floating page action in Utaloom Glass: a round frosted control instead of a Material FAB.
 * Lives inside the page, so it uses the lightweight glass rather than sampling the backdrop.
 */
@Composable
fun GlassFab(action: GlassFabAction, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .lightweightLiquidGlass(CircleShape, fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = action.onClick),
    ) {
        Icon(
            painter = painterResource(action.icon),
            contentDescription = action.contentDescription,
            tint = glassContentColor(),
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/** Several page actions sharing one glass capsule, the way iOS groups toolbar buttons. */
@Composable
fun GlassFabRow(actions: List<GlassFabAction>, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(52.dp)
            .lightweightLiquidGlass(GlassShapes.Capsule, fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        actions.forEach { action ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(56.dp)
                    .height(52.dp)
                    .clickable(role = Role.Button, onClick = action.onClick),
            ) {
                Icon(
                    painter = painterResource(action.icon),
                    contentDescription = action.contentDescription,
                    tint = glassContentColor(),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
