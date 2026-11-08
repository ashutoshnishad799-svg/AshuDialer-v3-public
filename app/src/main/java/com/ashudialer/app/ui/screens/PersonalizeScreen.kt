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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.AppSettings
import com.ashudialer.app.data.PKey
import com.ashudialer.app.ui.components.AppIconPickerRow
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.components.DialerTab
import com.ashudialer.app.ui.components.NavOrderEditor
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.glassListCard
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.components.parseNavOrder
import com.ashudialer.app.ui.components.serializeNavOrder
import com.ashudialer.app.ui.theme.AUTO_THEME_ID
import com.ashudialer.app.ui.theme.visiblePalettes
import com.ashudialer.app.ui.theme.AshuStyle
import com.ashudialer.app.ui.theme.DefaultPalettes
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.theme.withAccent
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
 * Values the person is changing RIGHT NOW. Every control writes here first (instantly, so the screen
 * restyles while the finger is still on a slider) and to the saved settings second (on release / tap).
 * null = "use what is saved".
 */
private data class Live(
    val theme: String? = null,
    val glass: Int? = null,
    val corner: Int? = null,
    val motion: Int? = null,
    val haptic: Int? = null,
    val accent: Long? = null,
    val avatar: String? = null,
    val density: String? = null,
    val navStyle: String? = null,
    val navLabels: String? = null,
    val navOrder: String? = null,
    val navShowDialer: Boolean? = null,
    val navShowRecent: Boolean? = null,
    val keypad: String? = null,
    val specular: Boolean? = null,
    val aurora: Boolean? = null,
    val swipe: Boolean? = null,
    val stagger: Boolean? = null,
    val fab: Boolean? = null,
    val dialerFloating: Boolean? = null,
    val dialerWide: Boolean? = null,
    val dialerLayout: String? = null,
    val fontSize: Int? = null,
    val depth: String? = null,
    val vibrate: Boolean? = null,
    val callNotif: String? = null,
    val callLook: String? = null,
    val navAutoSave: Boolean? = null,
    val moreThemes: Boolean? = null,
    val floatMorph: Boolean? = null,
    val autoMerge: Boolean? = null,
    val dialerSpeed: String? = null
)

/** Mixes a hue (0..1) with a "shade" (0 = deep, 0.5 = pure, 1 = pastel) into one colour. */
private fun shadeColor(hue: Float, shade: Float): Color {
    val h = (hue.coerceIn(0f, 1f) * 360f).coerceAtMost(359.9f)
    return if (shade < 0.5f) {
        Color.hsv(h, 0.80f, 0.35f + 0.60f * (shade / 0.5f))
    } else {
        val t = (shade - 0.5f) / 0.5f
        Color.hsv(h, 0.75f - 0.50f * t, 0.95f + 0.05f * t)
    }
}

/**
 * v2.0.1 Personalize.
 *  - Every control is LIVE: sliders restyle this whole screen while you drag, taps apply instantly, and
 *    the value is saved when you let go. (This screen re-provides the style / palette it shows from the
 *    values being changed, so nothing waits for storage.)
 *  - It is a lazy list: cards are built only when scrolled to, which is what made the old version heavy.
 *  - The colour picker is a real spectrum slider: the track IS the colour range.
 *
 * Defaults stay plain White and plain Black; everything here is optional.
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
    val basePalette = LocalDialerPalette.current
    val baseStyle = LocalAshuStyle.current
    var live by remember { mutableStateOf(Live()) }

    val glassPct = live.glass ?: settings.glassIntensityPct
    val cornerPct = live.corner ?: settings.cornerScalePct
    val motionPct = live.motion ?: settings.motionScalePct
    val hapticLv = live.haptic ?: settings.hapticLevel
    val avatar = live.avatar ?: settings.avatarShape
    val density = live.density ?: settings.listDensity
    val navStyle = live.navStyle ?: settings.navStyle
    val navLabels = live.navLabels ?: settings.navLabels
    val keypad = live.keypad ?: settings.keypadStyle
    val specular = live.specular ?: settings.specularEnabled
    val aurora = live.aurora ?: settings.auroraEnabled
    val swipe = live.swipe ?: settings.swipeActionsEnabled
    val stagger = live.stagger ?: settings.staggerLists
    val selectedTheme = live.theme ?: themeId
    val navOrder = live.navOrder ?: settings.navOrder
    val showDialer = live.navShowDialer ?: settings.navShowDialer
    val showRecent = live.navShowRecent ?: settings.navShowRecent
    val fabOn = live.fab ?: settings.quickFab
    val dialerFloating = live.dialerFloating ?: settings.dialerFloating
    val dialerWide = live.dialerWide ?: settings.dialerBoxWide
    val dialerLayout = live.dialerLayout ?: settings.dialerLayout
    val fontSize = live.fontSize ?: settings.fontSizeIndex.coerceIn(0, 2)
    val depth = live.depth ?: settings.buttonDepth
    val vibrate = live.vibrate ?: settings.vibrateOnButtonPress
    val callNotif = live.callNotif ?: settings.callNotifStyle
    val callLook = live.callLook ?: settings.callNotifLook
    val navAutoSave = live.navAutoSave ?: settings.navAutoSave
    val moreThemes = live.moreThemes ?: settings.moreThemes
    val floatMorph = live.floatMorph ?: settings.floatMorph
    val dialerSpeed = live.dialerSpeed ?: settings.dialerAnimSpeed
    val autoMerge = live.autoMerge ?: settings.autoMergeCalls

    val style = remember(baseStyle, glassPct, cornerPct, motionPct, hapticLv, avatar, density, navStyle, navLabels, keypad, specular, aurora, swipe, stagger) {
        baseStyle.copy(
            glassIntensity = glassPct / 100f,
            cornerScale = cornerPct / 100f,
            motionScale = motionPct / 100f,
            hapticLevel = hapticLv,
            avatarShape = avatar,
            density = density,
            navStyle = navStyle,
            navLabels = navLabels,
            keypadStyle = keypad,
            specular = specular,
            aurora = aurora,
            swipeActions = swipe,
            staggerLists = stagger
        )
    }
    val liveAccent = live.accent
    val palette = remember(basePalette, liveAccent) {
        if (liveAccent != null && liveAccent != 0L) basePalette.withAccent(Color(liveAccent.toInt())) else basePalette
    }

    // colour mixer state (kept across recompositions - the earlier version reset it and jumped back)
    val initial = remember {
        val argb = settings.accentOverrideArgb
        if (argb != 0L) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb.toInt(), hsv)
            val s = hsv[1]
            val v = hsv[2]
            val sh = if (v < 0.95f) ((v - 0.35f) / 0.60f * 0.5f).coerceIn(0f, 0.5f) else (0.5f + (0.75f - s) / 0.5f * 0.5f).coerceIn(0.5f, 1f)
            (hsv[0] / 360f) to sh
        } else 0.6f to 0.5f
    }
    var hue by remember { mutableFloatStateOf(initial.first) }
    var shade by remember { mutableFloatStateOf(initial.second) }
    fun applyMix(commit: Boolean) {
        val argb = shadeColor(hue, shade).toArgb().toLong() and 0xFFFFFFFFL
        live = live.copy(accent = argb)
        if (commit) onLong(PKey.ACCENT, argb)
    }

    CompositionLocalProvider(LocalAshuStyle provides style, LocalDialerPalette provides palette) {
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
                    Text("Everything you change here applies instantly", fontSize = 12.5.sp, color = palette.textSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "preview") { LivePreview(palette) }

                // ---------------------------------------------------------------- theme
                item(key = "theme") {
                    PCard("Theme", "Auto follows your phone: White in light mode, Black in dark mode.", palette) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemeTile(
                                name = "Auto",
                                selected = selectedTheme == AUTO_THEME_ID,
                                palette = palette,
                                brush = Brush.linearGradient(listOf(DefaultPalettes[0].swatchEnd.copy(alpha = 0.18f), DefaultPalettes[1].swatchStart)),
                                modifier = Modifier.weight(1f)
                            ) { live = live.copy(theme = AUTO_THEME_ID); onThemeSelected(AUTO_THEME_ID) }
                            DefaultPalettes.forEach { p ->
                                ThemeTile(
                                    name = p.displayName,
                                    selected = selectedTheme == p.id,
                                    palette = palette,
                                    brush = Brush.linearGradient(listOf(p.swatchStart, p.swatchEnd)),
                                    modifier = Modifier.weight(1f)
                                ) { live = live.copy(theme = p.id); onThemeSelected(p.id) }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("More themes (optional)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            visiblePalettes(moreThemes, selectedTheme).filter { p -> DefaultPalettes.none { it.id == p.id } }.forEach { p ->
                                ThemeTile(
                                    name = p.displayName,
                                    selected = selectedTheme == p.id,
                                    palette = palette,
                                    brush = Brush.linearGradient(listOf(p.swatchStart, p.swatchEnd)),
                                    modifier = Modifier.width(92.dp),
                                    badge = if (p.glass) "Glass" else null
                                ) { live = live.copy(theme = p.id); onThemeSelected(p.id) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        PrefSwitchRow(
                            "Show more colour themes",
                            "Emerald Night, Obsidian Gold, Midnight, Violet, Sunset and Rainbow. Off keeps the list clean.",
                            moreThemes, palette
                        ) { live = live.copy(moreThemes = it); onBool(PKey.MORE_THEMES, it) }
                    }
                }

                // ---------------------------------------------------------------- accent
                // ---------------------------------------------------------------- app icon
                item(key = "icon") {
                    PCard("App icon", "The icon on your home screen. Tap one to switch; some launchers need a few seconds.", palette) {
                        AppIconPickerRow(palette)
                    }
                }

                item(key = "accent") {
                    PCard("Accent colour", "Tints buttons, highlights and the selected tab. Drag the sliders and watch the preview.", palette) {
                        val current = live.accent ?: settings.accentOverrideArgb
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
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (current == 0L) palette.accent else palette.accentSoft)
                                    .bounceClick { live = live.copy(accent = 0L); onLong(PKey.ACCENT, 0L) }
                                    .padding(top = 7.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            AccentPresets.forEach { (name, color) ->
                                val argb = (color.toArgb().toLong() and 0xFFFFFFFFL)
                                val on = current == argb
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (on) 3.dp else 1.dp, if (on) palette.textPrimary else palette.cardBorder, CircleShape)
                                        .bounceClick {
                                            val hsv = FloatArray(3)
                                            android.graphics.Color.colorToHSV(argb.toInt(), hsv)
                                            hue = hsv[0] / 360f
                                            shade = 0.5f
                                            live = live.copy(accent = argb)
                                            onLong(PKey.ACCENT, argb)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (on) Icon(Icons.Filled.Check, contentDescription = name, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        val mixed = shadeColor(hue, shade)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(mixed).border(1.dp, palette.cardBorder, CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Your colour", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                                Text("#%06X".format(mixed.toArgb() and 0xFFFFFF), fontSize = 12.sp, color = palette.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Colour", fontSize = 12.sp, color = palette.textSecondary)
                        SpectrumSlider(
                            value = hue,
                            brush = Brush.horizontalGradient((0..12).map { Color.hsv((it * 30f).coerceAtMost(359f), 0.78f, 0.96f) }),
                            thumb = shadeColor(hue, 0.5f),
                            onChange = { hue = it; applyMix(false) },
                            onCommit = { hue = it; applyMix(true) }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Shade", fontSize = 12.sp, color = palette.textSecondary)
                        SpectrumSlider(
                            value = shade,
                            brush = Brush.horizontalGradient(listOf(shadeColor(hue, 0f), shadeColor(hue, 0.5f), shadeColor(hue, 1f))),
                            thumb = mixed,
                            onChange = { shade = it; applyMix(false) },
                            onCommit = { shade = it; applyMix(true) }
                        )
                    }
                }

                // ---------------------------------------------------------------- glass
                item(key = "glass") {
                    PCard("Glass & depth", "How frosted surfaces look. Moves live while you drag.", palette) {
                        LiveSlider("Frosting", "$glassPct%", glassPct.toFloat(), 0f, 100f, palette,
                            onLive = { live = live.copy(glass = it.roundToInt()) },
                            onCommit = { live = live.copy(glass = it.roundToInt()); onInt(PKey.GLASS, it.roundToInt()) })
                        PrefSwitchRow("Light edge", "A thin highlight along the top edge of glass surfaces", specular, palette) {
                            live = live.copy(specular = it); onBool(PKey.SPECULAR, it)
                        }
                        PrefSwitchRow(
                            "Background light",
                            if (palette.glass) "Soft pale light behind the glass. Still, so it costs no battery" else "Only used by the Liquid Glass and Frost Glass themes",
                            aurora, palette
                        ) { live = live.copy(aurora = it); onBool(PKey.AURORA, it) }
                        Spacer(Modifier.height(6.dp))
                        Text("Button depth", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(listOf("flat" to "Flat", "raised" to "Raised"), depth, palette) {
                            live = live.copy(depth = it); onButtonDepth(it)
                        }
                    }
                }

                // ---------------------------------------------------------------- shape + layout
                item(key = "shape") {
                    PCard("Shape & layout", "Drag a slider and the cards on this screen change shape right away.", palette) {
                        LiveSlider("Corner roundness", "$cornerPct%", cornerPct.toFloat(), 40f, 180f, palette,
                            onLive = { live = live.copy(corner = it.roundToInt()) },
                            onCommit = { live = live.copy(corner = it.roundToInt()); onInt(PKey.CORNER, it.roundToInt()) })
                        Text("Avatar shape", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(
                            listOf(AshuStyle.AVATAR_CIRCLE to "Circle", AshuStyle.AVATAR_SQUIRCLE to "Squircle", AshuStyle.AVATAR_ROUNDED to "Rounded"),
                            avatar, palette
                        ) { live = live.copy(avatar = it); onString(PKey.AVATAR, it) }
                        Spacer(Modifier.height(12.dp))
                        Text("List spacing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(
                            listOf(AshuStyle.DENSITY_COMPACT to "Compact", AshuStyle.DENSITY_COMFORTABLE to "Comfortable", AshuStyle.DENSITY_ROOMY to "Roomy"),
                            density, palette
                        ) { live = live.copy(density = it); onString(PKey.DENSITY, it) }
                        Spacer(Modifier.height(12.dp))
                        Text("Text size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(listOf("0" to "Small", "1" to "Default", "2" to "Large"), fontSize.toString(), palette) {
                            live = live.copy(fontSize = it.toInt()); onFontSizeIndex(it.toInt())
                        }
                    }
                }

                // ---------------------------------------------------------------- motion
                item(key = "motion") {
                    PCard("Motion & touch", "Animation speed applies to the whole app, including screen changes. Off = instant.", palette) {
                        Text("Animation speed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(
                            listOf("0" to "Off", "60" to "Smooth", "100" to "Standard", "160" to "Fast"),
                            listOf(0, 60, 100, 160).minByOrNull { kotlin.math.abs(it - motionPct) }.toString(),
                            palette
                        ) { live = live.copy(motion = it.toInt()); onInt(PKey.MOTION, it.toInt()) }
                        Spacer(Modifier.height(8.dp))
                        PrefSwitchRow("List entrance animation", "Rows slide in (Recordings, More, Personalize)", stagger, palette) {
                            live = live.copy(stagger = it); onBool(PKey.STAGGER, it)
                        }
                        PrefSwitchRow("Swipe actions on recent calls", "A long drag right calls, left messages", swipe, palette) {
                            live = live.copy(swipe = it); onBool(PKey.SWIPE, it)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Haptic strength", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(listOf("0" to "Off", "1" to "Light", "2" to "Normal", "3" to "Strong"), hapticLv.coerceIn(0, 3).toString(), palette) {
                            live = live.copy(haptic = it.toInt()); onInt(PKey.HAPTIC, it.toInt())
                        }
                        PrefSwitchRow("Vibrate on button press", "Dial pad and buttons", vibrate, palette) {
                            live = live.copy(vibrate = it); onVibrateOnButtonPress(it)
                        }
                    }
                }

                // ---------------------------------------------------------------- bottom bar
                item(key = "bar") {
                    PCard("Bottom bar", "Choose the look, the order and which buttons show. Contacts, Protect and More always stay.", palette) {
                        Text("Style", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(listOf(AshuStyle.NAV_FLOATING to "Floating", AshuStyle.NAV_DOCKED to "Docked"), navStyle, palette) {
                            live = live.copy(navStyle = it); onString(PKey.NAV_STYLE, it)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Tab names", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(
                            listOf(AshuStyle.LABELS_ALWAYS to "Always", AshuStyle.LABELS_SELECTED to "Selected", AshuStyle.LABELS_NEVER to "Hidden"),
                            navLabels, palette
                        ) { live = live.copy(navLabels = it); onString(PKey.NAV_LABELS, it) }
                        Spacer(Modifier.height(14.dp))
                        Text("Order", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text("Press a tab and slide it. The preview below moves with your finger.", fontSize = 12.sp, color = palette.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        NavOrderEditor(
                            palette = palette,
                            orderCsv = navOrder,
                            showDialer = showDialer,
                            showRecent = showRecent,
                            autoSave = navAutoSave,
                            onOrderSave = { csv -> live = live.copy(navOrder = csv); onString(PKey.NAV_ORDER, csv) }
                        )
                        Spacer(Modifier.height(8.dp))
                        PrefSwitchRow(
                            "Auto-save order",
                            if (navAutoSave) "Saved the moment you let go of a tab" else "You tap Save order yourself",
                            navAutoSave, palette
                        ) { live = live.copy(navAutoSave = it); onBool(PKey.NAV_AUTOSAVE, it) }
                        PrefSwitchRow("Show Dialer in the bar", "Turn off to open the dialer only from the floating button", showDialer, palette) {
                            live = live.copy(navShowDialer = it); onBool(PKey.NAV_SHOW_DIALER, it)
                        }
                        PrefSwitchRow("Show Recents in the bar", "Recents stay reachable from the floating dialer", showRecent, palette) {
                            live = live.copy(navShowRecent = it); onBool(PKey.NAV_SHOW_RECENT, it)
                        }
                    }
                }

                // ---------------------------------------------------------------- dialer
                item(key = "dialer") {
                    PCard("Dialer", "Floating button, floating window and layouts.", palette) {
                        PrefSwitchRow(
                            "Floating shortcut button",
                            "A round button above the bar: Dialer on Recents, Recents on the other tabs",
                            fabOn, palette
                        ) { live = live.copy(fab = it); onBool(PKey.QUICK_FAB, it) }
                        PrefSwitchRow(
                            "Open dialer as a floating window",
                            "A solid dialer card pops over the page instead of switching pages",
                            dialerFloating, palette
                        ) { live = live.copy(dialerFloating = it); onBool(PKey.DIALER_FLOATING, it) }
                        PrefSwitchRow(
                            "Spring-open animation",
                            "The floating dialer grows out of its button with a spring, key by key. Off = a quick fade",
                            floatMorph, palette
                        ) { live = live.copy(floatMorph = it); onBool(PKey.FLOAT_MORPH, it) }
                        Spacer(Modifier.height(6.dp))
                        Text("Dialer animation speed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text(
                            "How quickly the floating dialer opens, closes and builds its keys.",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Segmented(listOf("fast" to "Fast", "standard" to "Standard", "smooth" to "Smooth"), dialerSpeed, palette) {
                            live = live.copy(dialerSpeed = it); onString(PKey.DIALER_SPEED, it)
                        }
                        Spacer(Modifier.height(10.dp))
                        PrefSwitchRow(
                            "Wide contact suggestions",
                            "The matches under the number use the full width and are bigger",
                            dialerWide, palette
                        ) { live = live.copy(dialerWide = it); onBool(PKey.DIALER_WIDE, it) }
                        Spacer(Modifier.height(6.dp))
                        Text("Dial pad layout", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(listOf("classic" to "Classic", "compact" to "Compact", "glass" to "Glass card"), dialerLayout, palette) {
                            live = live.copy(dialerLayout = it); onString(PKey.DIALER_LAYOUT, it)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Key style", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(
                            listOf(AshuStyle.KEYPAD_GLASS to "Glass", AshuStyle.KEYPAD_FLAT to "Flat", AshuStyle.KEYPAD_OUTLINE to "Outline"),
                            keypad, palette
                        ) { live = live.copy(keypad = it); onString(PKey.KEYPAD, it) }
                    }
                }

                // ---------------------------------------------------------------- calls
                item(key = "calls") {
                    PCard("Calls", null, palette) {
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
                        Spacer(Modifier.height(4.dp))
                        PrefSwitchRow(
                            "Auto-merge added calls",
                            "Tap Add call, and once the new person answers they join the conference by themselves",
                            autoMerge, palette
                        ) { live = live.copy(autoMerge = it); onBool(PKey.AUTO_MERGE, it) }
                        Spacer(Modifier.height(10.dp))
                        Text("Call notification", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text(
                            "System style lets Android show its own call chip in the status bar on phones that support it. Custom card is this app's own notification.",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Segmented(listOf("auto" to "Auto", "system" to "System", "glass" to "Custom card"), callNotif, palette) {
                            live = live.copy(callNotif = it); onString(PKey.CALL_NOTIF, it)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Notification look", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text(
                            "The card you see while on a call. Classic is a flat card that follows your light or dark theme; Themed takes your theme's colours; Slim is the one-row card.",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Segmented(
                            listOf("classic" to "Classic", "dark" to "Dark", "light" to "Light", "themed" to "Themed", "minimal" to "Slim"),
                            callLook, palette
                        ) { live = live.copy(callLook = it); onString(PKey.CALL_NOTIF_LOOK, it) }
                    }
                }

                item(key = "reset") {
                    Box(Modifier.fillMaxWidth().padding(bottom = 28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Reset personalization",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.danger,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .bounceClick {
                                    onReset()
                                    live = Live()
                                    hue = 0.6f
                                    shade = 0.5f
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A slider that reports every movement (so things restyle live) and the final value on release. */
@Composable
private fun LiveSlider(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    palette: DialerPalette,
    onLive: (Float) -> Unit,
    onCommit: (Float) -> Unit
) {
    var drag by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    val shown = if (dragging) drag else value
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            Text(if (dragging) "${shown.roundToInt()}%" else valueText, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = palette.accent)
        }
        androidx.compose.material3.Slider(
            value = shown,
            onValueChange = {
                dragging = true
                drag = it
                onLive(it)
            },
            onValueChangeFinished = {
                dragging = false
                onCommit(drag)
            },
            valueRange = min..max,
            colors = sliderColors(palette)
        )
    }
}

/**
 * A slider whose TRACK is the thing being chosen (a rainbow, or a deep-to-pastel ramp of one colour),
 * so you always see which colour sits where. Tap or drag anywhere; the knob shows the current colour.
 */
@Composable
private fun SpectrumSlider(
    value: Float,
    brush: Brush,
    thumb: Color,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbPx = with(density) { 30.dp.toPx() }
    var widthPx by remember { mutableIntStateOf(1) }
    val currentChange by rememberUpdatedState(onChange)
    val currentCommit by rememberUpdatedState(onCommit)
    var last by remember { mutableFloatStateOf(value) }

    fun fraction(x: Float, w: Int): Float = ((x - thumbPx / 2f) / (w - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val f = fraction(o.x, size.width)
                    last = f
                    currentChange(f)
                    currentCommit(f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        last = fraction(o.x, size.width)
                        currentChange(last)
                    },
                    onDragEnd = { currentCommit(last) },
                    onDragCancel = { currentCommit(last) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        last = fraction(change.position.x, size.width)
                        currentChange(last)
                    }
                )
            }
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(50))
                .background(brush)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((value.coerceIn(0f, 1f) * (widthPx - thumbPx).coerceAtLeast(0f)).roundToInt(), 0) }
                .size(30.dp)
                .shadow(4.dp, CircleShape)
                .background(Color.White, CircleShape)
                .padding(5.dp)
                .background(thumb, CircleShape)
        )
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
            .glassListCard(palette, 22.dp)
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
internal fun PrefSwitchRow(title: String, subtitle: String, checked: Boolean, palette: DialerPalette, onChange: (Boolean) -> Unit) {
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
