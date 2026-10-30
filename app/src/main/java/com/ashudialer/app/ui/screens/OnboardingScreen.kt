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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.theme.AUTO_THEME_ID
import com.ashudialer.app.ui.theme.DefaultPalettes
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * v4 onboarding - completely redesigned.
 *
 * Five full-screen pages in a swipeable pager, each with its own live, drawn animation (no images or
 * video, so it stays crisp on every screen, costs nothing in APK size, and follows the active theme):
 *   1. Welcome            - rings of light spreading from a glass call button
 *   2. Calling            - a dial pad that types a number by itself
 *   3. Recording          - a live waveform with a bookmark and a cloud backup
 *   4. Privacy            - a shield being scanned, with the protections that fill in
 *   5. Make it yours      - the two default looks (White / Black / follow the phone), live
 *
 * Illustrations and text move at different speeds while you swipe (parallax), the page indicator
 * stretches, and the glow behind everything drifts with your finger. All animation honours the
 * "Animation speed" setting - when it is Off nothing moves and nothing is ever left half-drawn.
 *
 * Same public signature as before, so MainActivity needs no change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    currentThemeId: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val scope = rememberCoroutineScope()
    val pageCount = 5
    val pagerState = rememberPagerState(pageCount = { pageCount })

    val pageColors = remember(palette) {
        listOf(
            palette.accent,
            Color(0xFF4F8BFF),
            Color(0xFFFF5A5F),
            Color(0xFF22B573),
            palette.accent
        )
    }
    val titles = remember {
        listOf(
            "Welcome to Ashu Phone",
            "Calling, made beautiful",
            "Record calls your way",
            "Private by design",
            "Make it yours"
        )
    }
    val bodies = remember {
        listOf(
            "A fast, private dialer with the tools power users actually want.",
            "Smart search, swipe actions and a dial pad that feels alive.",
            "Optional. Auto-record, mark key moments, add notes and back up to Google Drive when you choose.",
            "Block spam, hide numbers in Private Space and silence calls with Quiet Hours.",
            "White and Black are the defaults. Dozens of optional looks, including Liquid Glass, live in Personalize."
        )
    }

    Box(modifier = modifier.fillMaxSize().background(palette.opaqueBackground)) {
        // ambient glow that drifts with the swipe
        Canvas(Modifier.fillMaxSize()) {
            val progress = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val w = size.width
            val h = size.height
            fun glow(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r,
                    center = Offset(cx, cy)
                )
            }
            val idx = progress.toInt().coerceIn(0, pageCount - 1)
            val next = (idx + 1).coerceAtMost(pageCount - 1)
            val frac = (progress - idx).coerceIn(0f, 1f)
            val c = androidx.compose.ui.graphics.lerp(pageColors[idx], pageColors[next], frac)
            val dark = palette.isDark
            glow(w * (0.82f - 0.12f * (progress / (pageCount - 1))), h * 0.16f, w * 0.95f, c, if (dark) 0.30f else 0.22f)
            glow(w * (0.10f + 0.16f * (progress / (pageCount - 1))), h * 0.78f, w * 0.85f, c, if (dark) 0.16f else 0.13f)
        }

        Column(Modifier.fillMaxSize()) {
            // top row: skip
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val last = pagerState.currentPage == pageCount - 1
                Text(
                    "Skip",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (last) Color.Transparent else palette.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(if (last) Modifier else Modifier.bounceClick { onFinished() })
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val dist = abs(offset).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = -offset * size.width * 0.28f
                                alpha = 1f - dist
                                scaleX = 1f - 0.18f * dist
                                scaleY = 1f - 0.18f * dist
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (page) {
                            0 -> WelcomeArt(pageColors[0], palette)
                            1 -> KeypadArt(pageColors[1], palette)
                            2 -> RecordArt(pageColors[2], palette)
                            3 -> ShieldArt(pageColors[3], palette)
                            else -> ThemeArt(currentThemeId, onThemeSelected, palette)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = -offset * size.width * 0.6f
                                alpha = (1f - dist * 1.3f).coerceIn(0f, 1f)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            titles[page],
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 34.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            bodies[page],
                            fontSize = 15.5.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // indicator + primary button
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(pageCount) { i ->
                        val selected = pagerState.currentPage == i
                        val w by animateDpAsState(if (selected) 28.dp else 8.dp, spring(dampingRatio = 0.65f), label = "dot-w")
                        val c by animateColorAsState(if (selected) pageColors[i] else palette.textSecondary.copy(alpha = 0.35f), label = "dot-c")
                        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                    }
                }
                Spacer(Modifier.height(18.dp))
                val isLast = pagerState.currentPage == pageCount - 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.accent)
                        .bounceClick(pressedScale = 0.97f) {
                            if (isLast) onFinished()
                            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isLast) "Get started" else "Next",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.solidBackground
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (isLast) Icons.Filled.Check else Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = palette.solidBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 1 - Welcome
// ------------------------------------------------------------------------------------------------

@Composable
private fun WelcomeArt(color: Color, palette: DialerPalette) {
    val style = LocalAshuStyle.current
    val transition = rememberInfiniteTransition(label = "welcome")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(3600).coerceAtLeast(600), easing = LinearEasing)),
        label = "welcome-phase"
    )
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2f
            for (i in 0 until 3) {
                val p = if (style.animationsOn) (phase + i / 3f) % 1f else 0.25f + i * 0.28f
                val r = maxR * (0.36f + 0.64f * p)
                drawCircle(
                    color = color.copy(alpha = (1f - p) * 0.38f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent), center = center, radius = maxR * 0.55f),
                radius = maxR * 0.55f,
                center = center
            )
        }
        Box(
            modifier = Modifier.size(116.dp).liquidGlass(palette, CircleShape, tintAlpha = 0.85f),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, tint = color, modifier = Modifier.size(54.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 2 - a dial pad that types by itself
// ------------------------------------------------------------------------------------------------

@Composable
private fun KeypadArt(color: Color, palette: DialerPalette) {
    val style = LocalAshuStyle.current
    val sequence = remember { listOf("9", "8", "7", "6", "5", "4", "3", "2", "1", "0") }
    var step by remember { mutableIntStateOf(if (style.animationsOn) 0 else 6) }
    LaunchedEffect(style.animationsOn) {
        if (style.animationsOn) {
            while (true) {
                delay(style.ms(520).toLong().coerceAtLeast(120L))
                step = (step + 1) % (sequence.size + 3)
            }
        }
    }
    val typed = sequence.take(step.coerceAtMost(sequence.size)).joinToString("")
    val active = if (step in 1..sequence.size) sequence[step - 1] else null
    val shown = if (typed.length > 5) typed.substring(0, 5) + " " + typed.substring(5) else typed

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(58.dp)
                .liquidGlass(palette, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (shown.isEmpty()) "\u2009" else shown,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = palette.textPrimary
            )
        }
        Spacer(Modifier.height(16.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        val on = key == active
                        val bg by animateColorAsState(if (on) color else Color.Transparent, tween(160), label = "key-bg")
                        val scale by androidx.compose.animation.core.animateFloatAsState(
                            if (on) 1.14f else 1f,
                            spring(dampingRatio = 0.45f),
                            label = "key-scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .liquidGlass(palette, CircleShape)
                                .background(bg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                key,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (on) Color.White else palette.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 3 - recording: waveform, bookmark, cloud
// ------------------------------------------------------------------------------------------------

@Composable
private fun RecordArt(color: Color, palette: DialerPalette) {
    val style = LocalAshuStyle.current
    val transition = rememberInfiniteTransition(label = "record")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(2600).coerceAtLeast(500), easing = LinearEasing)),
        label = "record-phase"
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(1800).coerceAtLeast(500), easing = LinearEasing)),
        label = "bob"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(palette, RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pulse = if (style.animationsOn) 0.55f + 0.45f * sin(phase * 2f * PI.toFloat() * 2f) else 1f
                Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = pulse.coerceIn(0.2f, 1f))))
                Spacer(Modifier.width(8.dp))
                Text("REC", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("Call with Aarav", fontSize = 12.sp, color = palette.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Box {
                Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                    val bars = 27
                    val gap = 4.dp.toPx()
                    val w = (size.width - gap * (bars - 1)) / bars
                    val playhead = if (style.animationsOn) phase else 0.55f
                    for (i in 0 until bars) {
                        val envelope = 0.35f + 0.65f * sin((i / (bars - 1f)) * PI.toFloat())
                        val wobble = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat() + i * 0.55f)
                        val h = (size.height * (0.16f + 0.84f * wobble) * envelope).coerceAtLeast(5.dp.toPx())
                        val played = (i + 0.5f) / bars <= playhead
                        drawRoundRect(
                            color = if (played) color else palette.textSecondary.copy(alpha = 0.3f),
                            topLeft = Offset(i * (w + gap), (size.height - h) / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(w / 2f, w / 2f)
                        )
                    }
                }
                // a bookmark that pops onto the waveform
                val pop = if (style.animationsOn) ((phase - 0.35f) * 6f).coerceIn(0f, 1f) else 1f
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 108.dp)
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = pop
                            scaleY = pop
                            alpha = pop
                        }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).liquidGlass(palette, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { translationY = if (style.animationsOn) -4.dp.toPx() * sin(bob * 2f * PI.toFloat()) else 0f }
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("Backed up to your Drive folder", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 4 - a shield that gets scanned
// ------------------------------------------------------------------------------------------------

@Composable
private fun ShieldArt(color: Color, palette: DialerPalette) {
    val style = LocalAshuStyle.current
    val transition = rememberInfiniteTransition(label = "shield")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(style.ms(3200).coerceAtLeast(600), easing = LinearEasing)),
        label = "sweep"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 190.dp, height = 220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val shield = Path().apply {
                    moveTo(w * 0.5f, 0f)
                    cubicTo(w * 0.68f, h * 0.10f, w * 0.86f, h * 0.12f, w, h * 0.14f)
                    lineTo(w, h * 0.50f)
                    cubicTo(w, h * 0.76f, w * 0.74f, h * 0.92f, w * 0.5f, h)
                    cubicTo(w * 0.26f, h * 0.92f, 0f, h * 0.76f, 0f, h * 0.50f)
                    lineTo(0f, h * 0.14f)
                    cubicTo(w * 0.14f, h * 0.12f, w * 0.32f, h * 0.10f, w * 0.5f, 0f)
                    close()
                }
                drawPath(
                    path = shield,
                    brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.06f)))
                )
                clipPath(shield) {
                    val y = (if (style.animationsOn) sweep else 0.5f) * h
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, color.copy(alpha = 0.55f), Color.Transparent),
                            startY = y - 40.dp.toPx(),
                            endY = y + 40.dp.toPx()
                        )
                    )
                }
                drawPath(path = shield, color = color, style = Stroke(width = 2.5.dp.toPx()))
            }
            Icon(Icons.Filled.Lock, contentDescription = null, tint = color, modifier = Modifier.size(58.dp))
        }
        Spacer(Modifier.height(16.dp))
        val items = listOf("Spam blocked", "Private Space", "Quiet Hours")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { i, label ->
                val reveal = if (style.animationsOn) ((sweep * 3.2f) - i).coerceIn(0f, 1f) else 1f
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = 0.25f + 0.75f * reveal
                            translationY = (1f - reveal) * 8.dp.toPx()
                        }
                        .liquidGlass(palette, RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
//  Page 5 - choose a look (live)
// ------------------------------------------------------------------------------------------------

@Composable
private fun ThemeArt(currentThemeId: String, onThemeSelected: (String) -> Unit, palette: DialerPalette) {
    val options = remember {
        listOf(
            Triple(AUTO_THEME_ID, "Auto", null as DialerPalette?),
            Triple(DefaultPalettes[0].id, "White", DefaultPalettes[0]),
            Triple(DefaultPalettes[1].id, "Black", DefaultPalettes[1])
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { (id, name, p) ->
                val selected = currentThemeId == id
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    if (selected) 1.06f else 0.96f,
                    spring(dampingRatio = 0.6f),
                    label = "theme-scale"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .bounceClick { onThemeSelected(id) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 92.dp, height = 156.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(if (selected) 3.dp else 1.dp, if (selected) palette.accent else palette.cardBorder, RoundedCornerShape(22.dp))
                    ) {
                        if (p != null) MiniPhone(p) else {
                            // "Auto" = half white, half black
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxSize()) { MiniPhone(DefaultPalettes[0]) }
                                Box(Modifier.weight(1f).fillMaxSize()) { MiniPhone(DefaultPalettes[1]) }
                            }
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = palette.solidBackground, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(name, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = palette.textPrimary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (currentThemeId == AUTO_THEME_ID) "Follows your phone's light / dark setting" else "You can change this any time",
            fontSize = 12.5.sp,
            color = palette.textSecondary
        )
    }
}

/** A tiny drawn phone screen in a theme's own colours. */
@Composable
private fun MiniPhone(p: DialerPalette) {
    Box(Modifier.fillMaxSize().background(p.solidBackground)) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.textPrimary.copy(alpha = 0.85f)))
            repeat(3) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.cardBackground).padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(p.avatarBackground))
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Box(Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.textPrimary.copy(alpha = 0.7f)))
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.textSecondary.copy(alpha = 0.6f)))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(p.accent))
        }
    }
}
