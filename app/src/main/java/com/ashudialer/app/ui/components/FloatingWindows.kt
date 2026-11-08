/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE file in the project root. This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.RecentCall
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Personalize > Dialer > Animation speed. "fast" opens in roughly 60% of the standard time, "smooth" takes about 40%
 * longer and settles more gently. (Personalize > Motion & touch > Animation speed still scales everything on top.)
 */
fun dialerSpeedFactor(id: String): Float = when (id) {
    "fast" -> 1.6f
    "smooth" -> 0.72f
    else -> 1f
}

/**
 * The round shortcut buttons above the bottom bar, and the solid windows they open.
 *
 *  - a solid round button sits at the bottom-right, just above the bar (Dialer on Recents, Recents elsewhere);
 *  - tapping it opens a SOLID card that grows out of that very button - one spring drives the scale (pivoting on the
 *    button's own centre), the fade and, for the dialer, the keys arriving one after another;
 *  - the page behind is only dimmed, the card itself is opaque. A swipe down on its handle, a tap outside, the
 *    button (now a close X) or Back closes it, and it shrinks back into the button;
 *  - [springOpen] off = a quick fade instead of the spring, [speed] = see [dialerSpeedFactor].
 */
@Composable
fun FloatingQuickAccess(
    palette: DialerPalette,
    showDialerFab: Boolean,
    showRecentsFab: Boolean,
    dialerOpen: Boolean,
    recentsOpen: Boolean,
    onToggleDialer: () -> Unit,
    onToggleRecents: () -> Unit,
    onDismiss: () -> Unit,
    dialerContent: @Composable (entrance: () -> Float, onInfo: (Float, Boolean) -> Unit) -> Unit,
    recentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    springOpen: Boolean = true,
    speed: Float = 1f
) {
    val dialerProgress = rememberWindowProgress(dialerOpen, springOpen, speed)
    val recentsProgress = rememberWindowProgress(recentsOpen, springOpen, speed)
    // A window stays composed while it is open OR still animating shut. These three reads only change at the start
    // and the end of an animation, so the frames in between never recompose this function.
    val dialerShown = dialerOpen || dialerProgress.isRunning || dialerProgress.targetValue > 0f
    val recentsShown = recentsOpen || recentsProgress.isRunning || recentsProgress.targetValue > 0f
    val bothFabs = showRecentsFab && showDialerFab
    val bottomGap = if (bothFabs) 132.dp else 78.dp
    val dialerEntrance = remember(dialerProgress) { { dialerProgress.value } }
    // reported by the dialer: how much of its top may stay folded away at rest, and whether suggestions are showing
    var dialerTrimDp by remember { mutableFloatStateOf(0f) }
    var dialerExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The window's FULL height. At rest it shows only the lower part of it (see SolidWindow), so a tall value costs
        // nothing; it is what leaves room for two suggestion rows AND comfortable keys when numbers are typed.
        val dialerFraction = if (maxHeight < 640.dp) 0.94f else 0.90f

        if (dialerShown || recentsShown) {
            // The dim layer: a plain scrim (the page is only darkened, never faded to an empty backdrop).
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val k = maxOf(dialerProgress.value, recentsProgress.value).coerceIn(0f, 1f)
                        drawRect(Color.Black.copy(alpha = (if (palette.isDark) 0.52f else 0.34f) * k))
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        if (dialerShown) {
            SolidWindow(
                palette = palette,
                progress = dialerProgress,
                heightFraction = dialerFraction,
                bottomGap = bottomGap,
                originFromBottom = 46.dp,
                onDismiss = onDismiss,
                trimDp = dialerTrimDp,
                expanded = dialerExpanded,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                dialerContent(dialerEntrance) { trim, expanded ->
                    dialerTrimDp = trim
                    dialerExpanded = expanded
                }
            }
        }

        if (recentsShown) {
            SolidWindow(
                palette = palette,
                progress = recentsProgress,
                heightFraction = 0.66f,
                bottomGap = bottomGap,
                originFromBottom = if (bothFabs) 112.dp else 46.dp,
                onDismiss = onDismiss,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                recentsContent()
            }
        }

        // the buttons
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showRecentsFab) {
                QuickButton(palette.accent, open = recentsOpen, sizeDp = if (showDialerFab) 48 else 60, onClick = onToggleRecents) {
                    Icon(Icons.Filled.History, contentDescription = "Recents", tint = Color.White, modifier = Modifier.size(if (showDialerFab) 22.dp else 26.dp))
                }
            }
            if (showDialerFab) {
                QuickButton(palette.callGreen, open = dialerOpen, sizeDp = 60, onClick = onToggleDialer) {
                    Icon(Icons.Filled.Dialpad, contentDescription = "Dialer", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/**
 * One 0..1 value per window, driven by a spring on the way in (a soft settle, no wobble) and a short ease-in on the
 * way out. Everything the window does - scale, fade, the scrim, the keys arriving - reads THIS value in the draw
 * phase, so the whole animation costs no recomposition at all.
 */
@Composable
private fun rememberWindowProgress(
    open: Boolean,
    springy: Boolean,
    speed: Float
): Animatable<Float, AnimationVector1D> {
    val style = LocalAshuStyle.current
    val progress = remember { Animatable(0f) }
    val animationsOn = style.animationsOn
    val motion = style.motionScale.coerceIn(0.4f, 2f)
    LaunchedEffect(open, springy, speed, animationsOn, motion) {
        when {
            !animationsOn -> progress.snapTo(if (open) 1f else 0f)
            open && springy -> progress.animateTo(
                1f,
                // time scales with 1/sqrt(stiffness), so stiffness follows the square of the speed factor
                spring(dampingRatio = 0.80f, stiffness = 330f * speed * speed * motion * motion)
            )
            open -> progress.animateTo(1f, tween((260 / (speed * motion)).toInt(), easing = FastOutSlowInEasing))
            else -> progress.animateTo(0f, tween((230 / (speed * motion)).toInt(), easing = FastOutLinearInEasing))
        }
    }
    return progress
}

/**
 * The opaque floating card. It scales around the button's own centre (so it really grows out of it and shrinks back
 * into it), carries a soft shadow, and a handle at the top that can be dragged down to close it.
 *
 * Its content is always laid out at the card's FULL height, bottom-aligned, so nothing inside ever has to re-flow. At
 * rest ([expanded] = false) only the lower part is visible - everything except the top [trimDp] - and the card grows
 * upward to its full height when [expanded] turns true (the dialer does that while it has suggestions to show).
 */
@Composable
private fun SolidWindow(
    palette: DialerPalette,
    progress: Animatable<Float, AnimationVector1D>,
    heightFraction: Float,
    bottomGap: Dp,
    originFromBottom: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    trimDp: Float = 0f,
    expanded: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    // Every theme gets a fully opaque card: the card colour is flattened onto the page colour first.
    val solid = remember(palette) { palette.cardBackground.compositeOver(palette.solidBackground) }
    val edge = remember(palette, solid) { palette.cardBorder.compositeOver(solid) }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    // 0 = resting height, 1 = full height. No overshoot (a card that bounces taller than its own content looks broken).
    // The very first report from the dialer is applied instantly, so the card never visibly shrinks while it opens.
    val reveal = remember { Animatable(1f) }
    var trimSeen by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, trimDp) {
        val target = if (expanded || trimDp <= 0f) 1f else 0f
        if (!trimSeen && trimDp > 0f) {
            trimSeen = true
            reveal.snapTo(target)
        } else {
            reveal.animateTo(target, spring(dampingRatio = 1f, stiffness = 420f))
        }
    }

    Layout(
        content = {
            Box(Modifier.fillMaxSize()) { content() }
            // The handle: purely a grip - drag it down and the card follows your finger; let go past ~100dp to close.
            Box(
                Modifier
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (drag.value > 100.dp.toPx()) {
                                        onDismiss()
                                    } else {
                                        drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f) } }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(palette.textSecondary.copy(alpha = 0.32f))
                )
            }
        },
        modifier = modifier
            .padding(bottom = bottomGap)
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .graphicsLayer {
                val e = progress.value.coerceAtLeast(0f)
                val s = 0.14f + 0.86f * e
                scaleX = s
                scaleY = s
                alpha = (e * 2.4f).coerceIn(0f, 1f)
                translationY = drag.value
                // pivot = the centre of the round button: 38dp in from the card's right edge, and just below its bottom edge
                transformOrigin = TransformOrigin(
                    pivotFractionX = 1f - 38.dp.toPx() / size.width,
                    pivotFractionY = 1f + (bottomGap - originFromBottom).toPx() / size.height
                )
            }
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(solid)
            .border(1.dp, edge, shape)
            // touches inside the card must never fall through to the scrim (which would close it)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val fullHeight = (constraints.maxHeight * heightFraction).roundToInt()
        val body = measurables[0].measure(Constraints.fixed(width, fullHeight))
        val gripHeight = 22.dp.roundToPx()
        val grip = measurables[1].measure(Constraints.fixed(width, gripHeight))
        val restHeight = (fullHeight - trimDp.dp.roundToPx()).coerceIn(fullHeight / 2, fullHeight)
        val visibleHeight = (restHeight + (fullHeight - restHeight) * reveal.value).roundToInt().coerceIn(restHeight, fullHeight)
        layout(width, visibleHeight) {
            body.placeRelative(0, visibleHeight - fullHeight)
            grip.placeRelative(0, 0)
        }
    }
}

/** A solid round button with a soft shadow; its icon turns into an X while its window is open. */
@Composable
private fun QuickButton(
    color: Color,
    open: Boolean,
    sizeDp: Int,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val style = LocalAshuStyle.current
    val spin by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = tween(style.ms(260), easing = FastOutSlowInEasing),
        label = "quick-spin"
    )
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.40f)
            )
            .clip(CircleShape)
            .background(color)
            .bounceClick(pressedScale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = open, animationSpec = tween(style.ms(180)), label = "quick-icon") { isOpen ->
            Box(Modifier.graphicsLayer { rotationZ = if (isOpen) spin - 90f else 0f }) {
                if (isOpen) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(26.dp))
                } else {
                    icon()
                }
            }
        }
    }
}

/** The compact list inside the floating Recents window. */
@Composable
fun FloatingRecentsList(
    palette: DialerPalette,
    recents: List<RecentCall>,
    onCall: (RecentCall) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val time = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent calls", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            Text(
                "Open all",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accentSoft)
                    .bounceClick(onClick = onOpenAll)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        if (recents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recent calls yet", fontSize = 14.sp, color = palette.textSecondary)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(recents.take(25), key = { it.id }) { call ->
                    val missed = call.direction == CallDirection.MISSED || call.direction == CallDirection.REJECTED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCall(call) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = call.displayName.ifBlank { call.phoneNumber }, photoUri = call.photoUri, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                call.displayName.ifBlank { call.phoneNumber },
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (missed) palette.danger else palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (call.direction) {
                                        CallDirection.INCOMING -> Icons.Filled.CallReceived
                                        CallDirection.OUTGOING -> Icons.Filled.CallMade
                                        else -> Icons.Filled.CallMissed
                                    },
                                    contentDescription = null,
                                    tint = if (missed) palette.danger else palette.textSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    time.format(Date(call.timestampMillis)) + if (call.callCount > 1) "  \u2022  ${call.callCount} calls" else "",
                                    fontSize = 12.5.sp,
                                    color = palette.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(palette.callGreen.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call", tint = palette.callGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
