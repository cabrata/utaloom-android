/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.theme.isUtaloomGlass
import com.metrolist.music.ui.theme.GlassShapes
import com.metrolist.music.ui.glass.liquidGlass
import com.metrolist.music.ui.glass.glassContentColor
import com.metrolist.music.ui.glass.glassIndicatorColor
import com.metrolist.music.ui.glass.FlatSelectionPill
import com.metrolist.music.ui.glass.GlassPillMotion
import com.metrolist.music.ui.glass.GlassSelectionPill
import com.metrolist.music.ui.glass.LocalAppBackdrop
import com.metrolist.music.ui.glass.LocalGlassExport
import com.metrolist.music.ui.glass.PILL_GROW_HEIGHT
import com.metrolist.music.ui.glass.glassActive
import com.metrolist.music.ui.glass.pillPlacement
import com.metrolist.music.ui.glass.backdrop.backdrops.layerBackdrop
import com.metrolist.music.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val glass = isUtaloomGlass
    val containerColor = if (glass) Color.Transparent else if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier.then(if (glass) Modifier.liquidGlass(shape = GlassShapes.Group) else Modifier),
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem || isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem && !isHomeHoldItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // Long presses are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val glass = isUtaloomGlass
    val colors = MaterialTheme.colorScheme
    val containerColor = if (glass) Color.Transparent else if (pureBlack) Color.Black else colors.surfaceContainerHigh
    val contentColor = if (glass) glassContentColor() else if (pureBlack) Color.White else colors.onSurfaceVariant
    val indicatorColor = if (glass) glassIndicatorColor() else if (pureBlack) colors.primary.copy(alpha = 0.24f) else colors.secondaryContainer
    val selectedColor = if (glass) contentColor else if (pureBlack) Color.White else colors.onSecondaryContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val selectedIndex = navigationItems.indexOfFirst { isRouteSelected(currentRoute, it.route, navigationItems) }
    val indicatorPosition = if (glass) 0f else animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "navIndicator",
    ).value

    // Glass mode: BitChord's selection pill, flat at rest and a lens over the bar while it travels.
    val coroutineScope = rememberCoroutineScope()
    val pill = remember(navigationItems) { GlassPillMotion(coroutineScope, selectedIndex.coerceAtLeast(0)) }
    val reduceMotion = coroutineScope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    pill.reduceMotion = reduceMotion
    DisposableEffect(pill) { onDispose { pill.stop() } }
    val lensPill = glass && glassActive() && LocalAppBackdrop.current != null
    val barGlass = rememberLayerBackdrop()
    val tabRow = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val tabCount = navigationItems.size.coerceAtLeast(1)
    val stepPx = rowSize.width.toFloat() / tabCount
    pill.stepDp = stepPx / density.density
    val barPadPx = with(density) { 4.dp.toPx() }
    val restSize = Size(stepPx, rowSize.height.toFloat())
    val liftedHeight = rowSize.height + barPadPx * 2 + with(density) { PILL_GROW_HEIGHT.toPx() }
    val liftedSize = if (rowSize.height > 0) Size(liftedHeight * stepPx / rowSize.height, liftedHeight) else restSize
    val pillCenter: (Float) -> Offset = { position ->
        val x = position * stepPx + stepPx / 2f
        Offset(if (isRtl) rowSize.width - x else x, rowSize.height / 2f)
    }
    val showPill = glass && selectedIndex >= 0 && stepPx > 0f
    var lastSelectedIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex, glass, reduceMotion, pill) {
        if (selectedIndex >= 0) {
            if (!glass || reduceMotion || lastSelectedIndex < 0) pill.snapTo(selectedIndex) else pill.animateTo(selectedIndex)
        }
        lastSelectedIndex = selectedIndex
    }
    val currentStepPx by rememberUpdatedState(stepPx)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentItems by rememberUpdatedState(navigationItems)
    val currentOnItemClick by rememberUpdatedState(onItemClick)

    // Floats inside the same slot the full-width bar used, so the mini player bounds stay unchanged.
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
      // Unclipped wrapper: the lifted lens pill stands past the bar's edges.
      Box(
        Modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(if (slimNav) 52.dp else 64.dp),
      ) {
       CompositionLocalProvider(LocalGlassExport provides if (lensPill) barGlass else null) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = if (glass) 0.dp else 6.dp,
            border = if (pureBlack && !glass) BorderStroke(1.dp, colors.outlineVariant) else null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (glass) Modifier.liquidGlass(shape = GlassShapes.Capsule) else Modifier),
        ) {
            BoxWithConstraints(Modifier.padding(4.dp)) {
                val itemWidth = maxWidth / navigationItems.size.coerceAtLeast(1)
                if (!glass && selectedIndex >= 0) {
                    Box(
                        Modifier
                            .graphicsLayer { translationX = itemWidth.toPx() * indicatorPosition }
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(indicatorColor),
                    )
                }
                if (showPill && (!lensPill || pill.isFlat)) {
                    FlatSelectionPill(
                        color = indicatorColor,
                        modifier = Modifier
                            .matchParentSize()
                            .pillPlacement(
                                center = { pillCenter(pill.position) },
                                size = { pill.liveSize(restSize, liftedSize) },
                            ),
                    )
                }
                Row(
                    Modifier
                        .fillMaxSize()
                        .selectableGroup()
                        .then(
                            if (!glass) Modifier else Modifier
                                .onSizeChanged { rowSize = it }
                                .then(if (lensPill) Modifier.layerBackdrop(tabRow) else Modifier)
                                .pointerInput(pill, isRtl) {
                                    var totalDrag = 0f
                                    var startIndex = 0
                                    var step = 0f
                                    var holding = false
                                    var lastTick = 0
                                    val direction = if (isRtl) -1f else 1f
                                    fun dragIndex() = (startIndex + totalDrag / step)
                                        .coerceIn(0f, (currentItems.size - 1).toFloat())
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            step = currentStepPx
                                            holding = currentSelectedIndex >= 0 && step > 0f
                                            if (!holding) return@detectHorizontalDragGestures
                                            totalDrag = 0f
                                            startIndex = currentSelectedIndex
                                            lastTick = startIndex
                                            pill.startDrag()
                                        },
                                        onDragCancel = {
                                            if (holding) pill.release(currentSelectedIndex)
                                            holding = false
                                        },
                                        onDragEnd = {
                                            if (!holding) return@detectHorizontalDragGestures
                                            holding = false
                                            val next = dragIndex().roundToInt()
                                            pill.release(next)
                                            if (next != currentSelectedIndex) currentOnItemClick(currentItems[next], false)
                                        },
                                        onHorizontalDrag = { _, delta ->
                                            if (!holding) return@detectHorizontalDragGestures
                                            totalDrag += delta * direction
                                            val at = dragIndex()
                                            pill.dragTo(at)
                                            if (at.roundToInt() != lastTick) {
                                                lastTick = at.roundToInt()
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                    )
                                },
                        ),
                ) {
                    navigationItems.forEach { screen ->
                        val isSelected = remember(currentRoute, screen.route) {
                            isRouteSelected(currentRoute, screen.route, navigationItems)
                        }
                        val currentIsSelected by rememberUpdatedState(isSelected)
                        val iconRes = remember(isSelected, screen) {
                            if (isSelected) screen.iconIdActive else screen.iconIdInactive
                        }

                        val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                        val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
                        val interactionSource = remember { MutableInteractionSource() }

                        // Long press detection using InteractionSource
                        if (isSearchItem || isHomeHoldItem) {
                            LaunchedEffect(interactionSource) {
                                var isLongClick = false
                                interactionSource.interactions.collectLatest { interaction ->
                                    when (interaction) {
                                        is PressInteraction.Press -> {
                                            isLongClick = false
                                            delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                            isLongClick = true
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                                        }
                                        is PressInteraction.Release -> {
                                            if (!isLongClick) {
                                                onItemClick(screen, currentIsSelected)
                                            }
                                        }
                                        is PressInteraction.Cancel -> {
                                            isLongClick = false
                                        }
                                    }
                                }
                            }
                        }

                        val itemColor = if (isSelected) selectedColor else contentColor
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .selectable(
                                    selected = isSelected,
                                    interactionSource = interactionSource,
                                    indication = ripple(),
                                    role = Role.Tab,
                                    onClick = {
                                        if (!isSearchItem && !isHomeHoldItem) {
                                            onItemClick(screen, currentIsSelected)
                                        }
                                        // Long presses are handled via InteractionSource
                                    },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = if (slimNav) stringResource(screen.titleId) else null,
                                tint = itemColor,
                            )
                            if (!slimNav) {
                                Text(
                                    text = stringResource(screen.titleId),
                                    color = itemColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
       }
        if (lensPill && showPill && !pill.isFlat) {
            val padPx = barPadPx
            GlassSelectionPill(
                motion = pill,
                fillColor = indicatorColor,
                barGlass = barGlass,
                tabRow = tabRow,
                modifier = Modifier
                    .matchParentSize()
                    .pillPlacement(
                        center = { pillCenter(pill.position) + Offset(padPx, padPx) },
                        size = { pill.liveSize(restSize, liftedSize) },
                    ),
            )
        }
      }
    }
}
