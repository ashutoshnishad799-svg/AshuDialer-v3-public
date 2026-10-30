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
package com.ashudialer.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.AppSettings
import com.ashudialer.app.data.PKey
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.components.staggerIn
import com.ashudialer.app.ui.theme.AUTO_THEME_ID
import com.ashudialer.app.ui.theme.AllPalettes
import com.ashudialer.app.ui.theme.AshuStyle
import com.ashudialer.app.ui.theme.DefaultPalettes
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlin.math.roundToInt

private val AccentPresets = listOf(
    "Azure" to Color(0xFF2F6BFF),
    "Indigo" to Color(0xFF5B5BD6),
    "Violet" to Color(0xFF8B5CF6),
    "Rose" to Color(0xFFE0457B),
    "Crimson" to Color(0xFFE5484D),
    "Orange" to Color(0xFFF76B15),
    "Amber" to Color(0xFFF5A524),
    "Emerald" to Color(0xFF18A957),
    "Teal" to Color(0xFF0EA5A4),
    "Slate" to Color(0xFF64748B)
)

/**
 * v4 Personalize - every look-and-feel control of the app in one place, with a live preview on top
 * that restyles instantly (it reads the same LocalDialerPalette / LocalAshuStyle every real screen
 * reads, so what you see here is exactly what you get).
 *
 * The two DEFAULT themes are plain White and plain Black ("System" follows the phone). Liquid Glass,
 * Frost Glass, the aurora background and everything else here are optional extras.
 */
@Composable
fun PersonalizeScreen(
    settings: AppSettings,
    themeId: String,
    onThemeSelected: (String) -> Unit,
    onInt: (String, Int) -> Unit,
    onLong: (String, Long) -> Unit,
    onString: (String, String) -> Unit,
    onBool: (String, Boolean) -> Unit,
    onFontSizeIndex: (Int) -> Unit,
    onButtonDepth: (String) -> Unit,
    onVibrateOnButtonPress: (Boolean) -> Unit,
    onOpenIncomingCallStyle: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val style = LocalAshuStyle.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Personalize", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                Text("Make Ashu Phone look and feel like yours", fontSize = 12.5.sp, color = palette.textSecondary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LivePreview(palette, Modifier.staggerIn(0))

            // ---------------------------------------------------------------- theme
            PCard("Theme", "Auto follows your phone: White in light mode, Black in dark mode.", palette, Modifier.staggerIn(1)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeTile(
                        name = "Auto",
                        selected = themeId == AUTO_THEME_ID,
                        palette = palette,
                        brush = Brush.linearGradient(
                            listOf(
                                DefaultPalettes[0].swatchEnd.copy(alpha = 0.18f),
                                DefaultPalettes[1].swatchStart
                            )
                        ),
                        modifier = Modifier.weight(1f)
                    ) { onThemeSelected(AUTO_THEME_ID) }
                    DefaultPalettes.forEach { p ->
                        ThemeTile(
                            name = p.displayName,
                            selected = themeId == p.id,
                            palette = palette,
                            brush = Brush.linearGradient(listOf(p.swatchStart, p.swatchEnd)),
                            modifier = Modifier.weight(1f)
                        ) { onThemeSelected(p.id) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("More themes (optional)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AllPalettes.filter { p -> DefaultPalettes.none { it.id == p.id } }.forEach { p ->
                        ThemeTile(
                            name = p.displayName,
                            selected = themeId == p.id,
                            palette = palette,
                            brush = Brush.linearGradient(listOf(p.swatchStart, p.swatchEnd)),
                            modifier = Modifier.width(92.dp),
                            badge = if (p.glass) "Glass" else null
                        ) { onThemeSelected(p.id) }
                    }
                }
            }

            // ---------------------------------------------------------------- accent
            PCard("Accent colour", "Tints buttons, highlights and the selected tab. Your theme keeps its own backgrounds.", palette, Modifier.staggerIn(2)) {
                val current = settings.accentOverrideArgb
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Theme\ndefault",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (current == 0L) palette.solidBackground else palette.textPrimary,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (current == 0L) palette.accent else palette.accentSoft)
                            .bounceClick { onLong(PKey.ACCENT, 0L) }
                            .padding(top = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    AccentPresets.forEach { (name, color) ->
                        val argb = (color.toArgb().toLong() and 0xFFFFFFFFL)
                        val on = current == argb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (on) 3.dp else 1.dp, if (on) palette.textPrimary else palette.cardBorder, CircleShape)
                                .bounceClick { onLong(PKey.ACCENT, argb) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) Icon(Icons.Filled.Check, contentDescription = name, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // custom colour: a hue slider
                var hue by remember(current) { mutableFloatStateOf(0.6f) }
                val preview = Color.hsv(hue * 360f, 0.68f, 0.96f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(preview))
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        onValueChangeFinished = { onLong(PKey.ACCENT, (Color.hsv(hue * 360f, 0.68f, 0.96f).toArgb().toLong() and 0xFFFFFFFFL)) },
                        colors = sliderColors(palette),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Slide to mix your own colour", fontSize = 11.5.sp, color = palette.textSecondary)
            }

            // ---------------------------------------------------------------- glass
            PCard("Glass & depth", "How frosted surfaces look. Works on every theme; Glass themes add moving light behind it.", palette, Modifier.staggerIn(3)) {
                SliderRow("Frosting", "${settings.glassIntensityPct}%", settings.glassIntensityPct.toFloat(), 0f, 100f, palette) {
                    onInt(PKey.GLASS, it.roundToInt())
                }
                SwitchRow("Shiny glass edge", "A bright rim that catches the light", settings.specularEnabled, palette) { onBool(PKey.SPECULAR, it) }
                SwitchRow(
                    "Moving aurora light",
                    if (palette.glass) "Soft colour drifting behind the glass" else "Only used by Liquid Glass and Frost Glass themes",
                    settings.auroraEnabled,
                    palette
                ) { onBool(PKey.AURORA, it) }
                Spacer(Modifier.height(6.dp))
                Text("Button depth", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf("flat" to "Flat", "raised" to "Raised"),
                    selected = settings.buttonDepth,
                    palette = palette,
                    onSelect = onButtonDepth
                )
            }

            // ---------------------------------------------------------------- shape
            PCard("Shape & layout", null, palette, Modifier.staggerIn(4)) {
                SliderRow("Corner roundness", "${settings.cornerScalePct}%", settings.cornerScalePct.toFloat(), 40f, 180f, palette) {
                    onInt(PKey.CORNER, it.roundToInt())
                }
                Text("Avatar shape", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf(AshuStyle.AVATAR_CIRCLE to "Circle", AshuStyle.AVATAR_SQUIRCLE to "Squircle", AshuStyle.AVATAR_ROUNDED to "Rounded"),
                    selected = settings.avatarShape,
                    palette = palette
                ) { onString(PKey.AVATAR, it) }
                Spacer(Modifier.height(12.dp))
                Text("List spacing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf(AshuStyle.DENSITY_COMPACT to "Compact", AshuStyle.DENSITY_COMFORTABLE to "Comfortable", AshuStyle.DENSITY_ROOMY to "Roomy"),
                    selected = settings.listDensity,
                    palette = palette
                ) { onString(PKey.DENSITY, it) }
                Spacer(Modifier.height(12.dp))
                Text("Text size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf("0" to "Small", "1" to "Default", "2" to "Large"),
                    selected = settings.fontSizeIndex.coerceIn(0, 2).toString(),
                    palette = palette
                ) { onFontSizeIndex(it.toInt()) }
            }

            // ---------------------------------------------------------------- motion
            PCard("Motion & touch", "Animation speed applies to the whole app. Turn it off for a calm, instant feel.", palette, Modifier.staggerIn(5)) {
                Text("Animation speed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf("0" to "Off", "60" to "Calm", "100" to "Normal", "160" to "Lively"),
                    selected = listOf(0, 60, 100, 160).minByOrNull { kotlin.math.abs(it - settings.motionScalePct) }.toString(),
                    palette = palette
                ) { onInt(PKey.MOTION, it.toInt()) }
                Spacer(Modifier.height(8.dp))
                SwitchRow("List entrance animation", "Rows slide in one after another", settings.staggerLists, palette) { onBool(PKey.STAGGER, it) }
                SwitchRow("Swipe actions on recent calls", "Swipe right to call, left to message", settings.swipeActionsEnabled, palette) { onBool(PKey.SWIPE, it) }
                Spacer(Modifier.height(6.dp))
                Text("Haptic strength", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf("0" to "Off", "1" to "Light", "2" to "Normal", "3" to "Strong"),
                    selected = settings.hapticLevel.coerceIn(0, 3).toString(),
                    palette = palette
                ) { onInt(PKey.HAPTIC, it.toInt()) }
                SwitchRow("Vibrate on button press", "Dial pad and buttons", settings.vibrateOnButtonPress, palette, onVibrateOnButtonPress)
            }

            // ---------------------------------------------------------------- navigation + keypad
            PCard("Navigation & dial pad", null, palette, Modifier.staggerIn(6)) {
                Text("Bottom bar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf(AshuStyle.NAV_FLOATING to "Floating", AshuStyle.NAV_DOCKED to "Docked"),
                    selected = settings.navStyle,
                    palette = palette
                ) { onString(PKey.NAV_STYLE, it) }
                Spacer(Modifier.height(12.dp))
                Text("Tab names", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf(AshuStyle.LABELS_ALWAYS to "Always", AshuStyle.LABELS_SELECTED to "Selected", AshuStyle.LABELS_NEVER to "Hidden"),
                    selected = settings.navLabels,
                    palette = palette
                ) { onString(PKey.NAV_LABELS, it) }
                Spacer(Modifier.height(12.dp))
                Text("Dial pad keys", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf(AshuStyle.KEYPAD_GLASS to "Glass", AshuStyle.KEYPAD_FLAT to "Flat", AshuStyle.KEYPAD_OUTLINE to "Outline"),
                    selected = settings.keypadStyle,
                    palette = palette
                ) { onString(PKey.KEYPAD, it) }
            }

            // ---------------------------------------------------------------- calls
            PCard("Calls", null, palette, Modifier.staggerIn(7)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenIncomingCallStyle() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Incoming call look", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text("Choose how a ringing call appears", fontSize = 12.sp, color = palette.textSecondary)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary)
                }
            }

            Text(
                "Reset personalization",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = palette.danger,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(50))
                    .bounceClick(onClick = onReset)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ----------------------------------------------------------------------------------------------
//  Live preview
// ----------------------------------------------------------------------------------------------

@Composable
private fun LivePreview(palette: DialerPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, RoundedCornerShape(26.dp))
            .padding(16.dp)
    ) {
        Text("LIVE PREVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = "Aarav Sharma", photoUri = null, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Aarav Sharma", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Text("Mobile \u2022 2:41 PM \u2022 4:12", fontSize = 12.5.sp, color = palette.textSecondary)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(palette.callGreen.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = palette.callGreen, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("1", "2", "3").forEach { key ->
                Box(
                    modifier = Modifier.size(52.dp).liquidGlass(palette, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(palette, RoundedCornerShape(50), tintAlpha = 0.9f)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icons = listOf(Icons.Filled.Dialpad, Icons.Filled.History, Icons.Filled.Contacts, Icons.Filled.MoreHoriz)
            icons.forEachIndexed { i, icon ->
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == 1) palette.accentSoft else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = if (i == 1) palette.accent else palette.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
//  Building blocks
// ----------------------------------------------------------------------------------------------

@Composable
private fun PCard(
    title: String,
    subtitle: String?,
    palette: DialerPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.5.sp, color = palette.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ThemeTile(
    name: String,
    selected: Boolean,
    palette: DialerPalette,
    brush: Brush,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(brush)
                .border(if (selected) 3.dp else 1.dp, if (selected) palette.accent else palette.cardBorder, RoundedCornerShape(16.dp))
                .bounceClick(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(palette.accent),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = palette.solidBackground, modifier = Modifier.size(16.dp)) }
            }
            if (badge != null) {
                Text(
                    badge,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(name, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = palette.textPrimary, maxLines = 1)
    }
}

@Composable
private fun sliderColors(palette: DialerPalette) = SliderDefaults.colors(
    thumbColor = palette.accent,
    activeTrackColor = palette.accent,
    inactiveTrackColor = palette.accentSoft
)

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    palette: DialerPalette,
    onCommit: (Float) -> Unit
) {
    var live by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            Text(
                if (live == value) valueText else "${live.roundToInt()}%",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        }
        Slider(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = { onCommit(live) },
            valueRange = min..max,
            colors = sliderColors(palette)
        )
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, palette: DialerPalette, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = palette.textSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.solidBackground,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.textSecondary,
                uncheckedTrackColor = palette.accentSoft,
                uncheckedBorderColor = palette.cardBorder
            )
        )
    }
}

/** A pill-shaped selector: the chosen option slides its highlight in. */
@Composable
internal fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    palette: DialerPalette,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.accentSoft)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            val bg by animateColorAsState(if (on) palette.accent else Color.Transparent, label = "seg-bg")
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                color = if (on) palette.solidBackground else palette.textPrimary,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(id) }
                    .padding(vertical = 9.dp)
            )
        }
    }
}
