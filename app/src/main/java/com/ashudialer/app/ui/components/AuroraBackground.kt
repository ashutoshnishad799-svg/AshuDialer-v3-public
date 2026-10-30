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

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The light that lives behind every Liquid Glass surface.
 *
 * PERFORMANCE NOTE (why this looks the way it does): an earlier onboarding drew full-screen
 * Modifier.blur(150.dp) blobs and was visibly laggy on mid-range phones. This version never blurs
 * anything. Each "blob" is just a radial gradient that already fades to transparent, drawn with
 * plain Canvas calls, and the animation clock is read INSIDE the draw block, so only the draw phase
 * runs every frame - no recomposition. It also:
 *  - freezes into a still image when animation speed is "Off" or Android Battery Saver is on,
 *  - only moves very slowly (a full loop takes about 45 seconds), so the eye reads it as light, not motion.
 */
@Composable
fun AuroraBackground(
    palette: DialerPalette,
    modifier: Modifier = Modifier
) {
    val style = LocalAshuStyle.current
    val context = LocalContext.current
    val saver = remember(context) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
    val animate = style.animationsOn && !saver
    val colors = palette.auroraColors.ifEmpty {
        listOf(palette.accent, palette.accentSoft, palette.accent)
    }

    val transition = rememberInfiniteTransition(label = "aurora")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(45_000, easing = LinearEasing)),
        label = "aurora-clock"
    )

    Canvas(modifier.fillMaxSize()) {
        drawRect(brush = palette.opaqueBackground)
        val phase = if (animate) clock else 0.18f
        val a = phase * 2f * PI.toFloat()
        val w = size.width
        val h = size.height
        val strength = if (palette.isDark) 0.50f else 0.62f

        fun blob(cx: Float, cy: Float, radiusFraction: Float, color: Color, alpha: Float) {
            val r = radiusFraction * maxOf(w, h)
            val center = Offset(cx * w, cy * h)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )
        }

        blob(0.18f + 0.14f * cos(a), 0.14f + 0.07f * sin(a), 0.62f, colors[0 % colors.size], strength)
        blob(0.86f + 0.10f * sin(a * 1.0f + 1.3f), 0.38f + 0.10f * cos(a + 0.7f), 0.58f, colors[1 % colors.size], strength * 0.9f)
        blob(0.30f + 0.12f * sin(a + 2.4f), 0.82f + 0.07f * cos(a + 1.1f), 0.66f, colors[2 % colors.size], strength * 0.85f)
        blob(0.78f + 0.08f * cos(a + 3.2f), 0.92f - 0.06f * sin(a), 0.50f, colors[3 % colors.size], strength * 0.7f)
    }
}
