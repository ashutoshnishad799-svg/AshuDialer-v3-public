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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.AUTO_THEME_ID
import com.ashudialer.app.ui.theme.AllPalettes
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * The small pill at the top of the screen that shows the current theme and opens the picker.
 * v2.1: a quiet hairline pill with a swatch of the current theme (was an outlined button with a palette icon).
 */
@Composable
fun ThemePickerButton(
    onClick: () -> Unit,
    currentThemeId: String = "",
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val label = if (currentThemeId == AUTO_THEME_ID) "System" else palette.displayName
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .bounceClick(pressedScale = 0.96f, onClick = onClick)
            .clip(shape)
            .background(palette.cardBackground)
            .border(1.dp, palette.cardBorder, shape)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(palette.swatchStart, palette.swatchEnd)))
                .border(1.dp, palette.cardBorder, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Change theme",
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private val themeFilters = listOf("All", "Light", "Dark", "Glass")

/**
 * The theme chooser. Each theme is a small live preview of itself (background, list rows, button, bar) instead of a plain
 * colour dot, so you can see what you are picking. Filter chips narrow the list; cards glide in one after another and the
 * chosen one gets an animated ring and tick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    currentThemeId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalDialerPalette.current
    var filter by remember { mutableIntStateOf(0) }
    val shown = remember(filter) {
        AllPalettes.filter {
            when (filter) {
                1 -> !it.isDark
                2 -> it.isDark
                3 -> it.glass
                else -> true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBackground
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Appearance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Text(
                "Tap a theme to apply it right away.",
                fontSize = 13.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                themeFilters.forEachIndexed { i, name ->
                    FilterPill(name, filter == i, palette) { filter = i }
                }
            }
            Spacer(Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.heightIn(max = 440.dp)
            ) {
                if (filter == 0) {
                    item {
                        ThemeCard(
                            label = "System",
                            selected = currentThemeId == AUTO_THEME_ID,
                            accent = palette.accent,
                            idleBorder = palette.cardBorder,
                            labelColor = palette.textPrimary,
                            modifier = Modifier.staggerIn(0)
                        ) { onSelect(AUTO_THEME_ID) }
                    }
                }
                itemsIndexed(shown) { index, p ->
                    ThemeCard(
                        label = p.displayName,
                        selected = p.id == currentThemeId,
                        accent = p.accent,
                        idleBorder = palette.cardBorder,
                        labelColor = palette.textPrimary,
                        preview = p,
                        modifier = Modifier.staggerIn(index + 1)
                    ) { onSelect(p.id) }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, palette: DialerPalette, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.accentSoft.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "pill-bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else palette.textPrimary,
        animationSpec = tween(200),
        label = "pill-fg"
    )
    Text(
        label,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .bounceClick(pressedScale = 0.94f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/** One theme: a mini screen of itself, its name, and an animated ring + tick when it is the chosen one. */
@Composable
private fun ThemeCard(
    label: String,
    selected: Boolean,
    accent: Color,
    idleBorder: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    preview: DialerPalette? = null,
    onClick: () -> Unit
) {
    val style = LocalAshuStyle.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "theme-card-scale"
    )
    val badge by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "theme-card-badge"
    )
    val ring by animateColorAsState(
        targetValue = if (selected) accent else idleBorder,
        animationSpec = tween(style.ms(200)),
        label = "theme-card-ring"
    )
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier.bounceClick(pressedScale = 0.95f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, ring, shape)
        ) {
            if (preview != null) MiniScreen(preview) else SystemMiniScreen()
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .graphicsLayer { scaleX = badge; scaleY = badge; alpha = badge }
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** A tiny drawing of the app in this theme: title, three list rows, a round button and the bottom bar. */
@Composable
private fun MiniScreen(p: DialerPalette) {
    Box(Modifier.fillMaxSize().background(p.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 9.dp)) {
            Box(Modifier.width(34.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.textPrimary.copy(alpha = 0.85f)))
            Spacer(Modifier.height(8.dp))
            repeat(3) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.55f)))
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Box(Modifier.width(30.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.textPrimary.copy(alpha = 0.7f)))
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(20.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.textSecondary.copy(alpha = 0.7f)))
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.navBackground)
                    .border(1.dp, p.cardBorder, RoundedCornerShape(6.dp))
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 9.dp, bottom = 26.dp)
                .size(15.dp)
                .clip(CircleShape)
                .background(p.accent)
        )
    }
}

/** "System" follows the phone's light / dark setting, so its card is half light and half dark. */
@Composable
private fun SystemMiniScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color(0xFFF3F5F9),
                    0.5f to Color(0xFFF3F5F9),
                    0.5f to Color(0xFF14171C),
                    1f to Color(0xFF14171C)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.SettingsBrightness, contentDescription = null, tint = Color(0xFF7A8294), modifier = Modifier.size(26.dp))
    }
}
