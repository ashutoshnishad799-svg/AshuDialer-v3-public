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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private fun tabIcon(tab: DialerTab): ImageVector = when (tab) {
    DialerTab.CONTACTS -> Icons.Filled.Person
    DialerTab.RECENT -> Icons.Filled.Phone
    DialerTab.DIALER -> Icons.Filled.Dialpad
    DialerTab.PROTECT -> Icons.Filled.Shield
    DialerTab.MORE -> Icons.Filled.MoreHoriz
}

/**
 * Personalize > Bottom bar > Order.
 *
 * Every tab is a tile that shows its icon and name. Press a tile and slide it left or right: the other tiles glide out of
 * the way while you drag (the order changes LIVE, not when you let go) and a miniature of the real bar underneath
 * follows along. A tapped tile can also be nudged with the two arrow buttons.
 *
 * Auto-save ON: the new order is stored the moment you let go. Auto-save OFF: changes stay on this screen until you tap
 * Save (or Undo to throw them away).
 */
@Composable
fun NavOrderEditor(
    palette: DialerPalette,
    orderCsv: String,
    showDialer: Boolean,
    showRecent: Boolean,
    autoSave: Boolean,
    onOrderSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalAshuStyle.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val savedOrder = remember(orderCsv) { parseNavOrder(orderCsv) }
    var working by remember { mutableStateOf(savedOrder) }
    var dragging by remember { mutableStateOf<DialerTab?>(null) }
    var selected by remember { mutableStateOf<DialerTab?>(null) }
    val currentAutoSave by rememberUpdatedState(autoSave)
    val currentSave by rememberUpdatedState(onOrderSave)

    // Follow the stored order whenever it changes from outside (Reset, restore ...) and nobody is dragging.
    LaunchedEffect(savedOrder) { if (dragging == null) working = savedOrder }

    fun isHidden(tab: DialerTab) = (tab == DialerTab.DIALER && !showDialer) || (tab == DialerTab.RECENT && !showRecent)
    fun commitIfAuto() { if (currentAutoSave) currentSave(serializeNavOrder(working)) }
    fun nudge(tab: DialerTab, by: Int) {
        val from = working.indexOf(tab)
        val to = (from + by).coerceIn(0, working.lastIndex)
        if (from < 0 || from == to) return
        working = working.toMutableList().also { it.removeAt(from); it.add(to, tab) }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        commitIfAuto()
    }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(78.dp)) {
            val count = working.size.coerceAtLeast(1)
            val tileW = maxWidth / count
            val tileWpx = with(LocalDensity.current) { tileW.toPx() }

            working.forEachIndexed { index, tab ->
                key(tab) {
                    val isDragging = dragging == tab
                    val isSelected = selected == tab
                    val hidden = isHidden(tab)
                    val slideX = remember { Animatable(index * tileWpx) }
                    LaunchedEffect(index, tileWpx, isDragging) {
                        if (!isDragging) slideX.animateTo(index * tileWpx, spring(dampingRatio = 0.82f, stiffness = 520f))
                    }
                    val borderColor by animateColorAsState(
                        targetValue = if (isDragging || isSelected) palette.accent else palette.cardBorder,
                        animationSpec = tween(style.ms(160)),
                        label = "nav-tile-border"
                    )
                    val shape = RoundedCornerShape(14.dp)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(slideX.value.roundToInt(), 0) }
                            .width(tileW)
                            .fillMaxHeight()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                val s = if (isDragging) 1.07f else 1f
                                scaleX = s
                                scaleY = s
                                alpha = if (hidden) 0.55f else 1f
                            }
                            .padding(horizontal = 3.dp)
                            .clip(shape)
                            .background(if (isDragging) palette.accentSoft else palette.accentSoft.copy(alpha = 0.45f))
                            .border(if (isDragging || isSelected) 2.dp else 1.dp, borderColor, shape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selected = if (selected == tab) null else tab }
                            .pointerInput(tab, tileWpx) {
                                var fingerX = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = tab
                                        selected = tab
                                        fingerX = working.indexOf(tab) * tileWpx
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        fingerX = (fingerX + dragAmount.x).coerceIn(0f, (working.size - 1) * tileWpx)
                                        val x = fingerX
                                        scope.launch { slideX.snapTo(x) }
                                        val target = ((x + tileWpx / 2f) / tileWpx).toInt().coerceIn(0, working.lastIndex)
                                        val from = working.indexOf(tab)
                                        if (target != from) {
                                            working = working.toMutableList().also { it.removeAt(from); it.add(target, tab) }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        commitIfAuto()
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        commitIfAuto()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(tabIcon(tab), contentDescription = tab.label, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (hidden) "hidden" else tab.label,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hidden) palette.textSecondary else palette.textPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Live preview of your bar", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
        Spacer(Modifier.height(6.dp))
        NavPreviewPill(palette, working.filter { !isHidden(it) })

        val sel = selected
        if (sel != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Move ${sel.label}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                NudgeButton(palette, left = true, enabled = working.indexOf(sel) > 0) { nudge(sel, -1) }
                Spacer(Modifier.width(8.dp))
                NudgeButton(palette, left = false, enabled = working.indexOf(sel) < working.lastIndex) { nudge(sel, 1) }
            }
        }

        val dirty = working != savedOrder
        if (!autoSave && dirty) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Not saved yet", fontSize = 12.sp, color = palette.textSecondary, modifier = Modifier.weight(1f))
                Text(
                    "Undo",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                        .bounceClick { working = savedOrder; selected = null }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Text(
                    "Save order",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accent)
                        .bounceClick { onOrderSave(serializeNavOrder(working)) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NudgeButton(palette: DialerPalette, left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.accentSoft.copy(alpha = if (enabled) 0.8f else 0.3f))
            .bounceClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (left) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            contentDescription = if (left) "Move left" else "Move right",
            tint = if (enabled) palette.textPrimary else palette.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** A small copy of the real bottom bar, in the current order. The first tab is drawn as the selected one. */
@Composable
private fun NavPreviewPill(palette: DialerPalette, tabs: List<DialerTab>) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.accentSoft.copy(alpha = 0.35f))
            .border(1.dp, palette.cardBorder, shape)
            .padding(6.dp)
            .animateContentSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { i, tab ->
            key(tab) {
                val on = i == 0
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (on) palette.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        tabIcon(tab),
                        contentDescription = tab.label,
                        tint = if (on) palette.accent else palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
