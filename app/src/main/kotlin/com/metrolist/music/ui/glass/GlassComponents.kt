/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord
 */

package com.metrolist.music.ui.glass

import com.metrolist.music.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.GlassShapes
import com.metrolist.music.ui.theme.isUtaloomGlass

/** Hairline separator inset past artwork (0.5dp). */
private val HAIRLINE_WIDTH = 0.5f.dp

/** Page horizontal padding, matching BitChord. */
private val PAGE_GUTTER = 16.dp

/** Artwork + margin where row divider starts. */
private val ROW_DIVIDER_INSET = PAGE_GUTTER + 68.dp

/**
 * Section header in glass mode: title with optional navigating chevron "›",
 * matching Apple Music shelf headers.
 *
 * In Material3 mode, this falls back to the original behavior.
 */
@Composable
fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onNavigate: (() -> Unit)? = null,
) {
    if (!isUtaloomGlass) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = onNavigate != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNavigate?.invoke() }
            )
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (onNavigate != null) {
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * iOS-style search field for glass mode.
 *
 * Rounded rect background, magnifier icon, secondary-label placeholder text.
 * No background change — uses tertiary surface color (grouped cell gray on iOS).
 */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search),
    onSearch: (String) -> Unit = {},
) {
    if (!isUtaloomGlass) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = PAGE_GUTTER)
            .clip(GlassShapes.Capsule)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 8.dp),
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

/**
 * iOS capsule-style filter chip for glass mode.
 *
 * Uses tertiary fill when unselected, accent fill with white text when selected,
 * matching Apple Music's appearance. Squircle shape via [GlassShapes.Capsule].
 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isUtaloomGlass) return

    Box(
        modifier = modifier
            .height(28.dp)
            .clip(GlassShapes.Capsule)
            .background(
                if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
