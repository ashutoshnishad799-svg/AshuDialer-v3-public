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

<<<<<<< HEAD
=======
import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
<<<<<<< HEAD
=======
import androidx.compose.runtime.getValue
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
<<<<<<< HEAD
import com.ashudialer.app.ui.theme.DialerPalette

/**
 * The soft light that sits behind every glass surface.
 *
 * v2.1: it is STATIC now. The earlier version slowly moved two full-screen layers around forever; every frame
 * the GPU re-drew big radial gradients and then every translucent pane on top of them, which is exactly what made
 * the Frost / Liquid Glass themes feel laggy (and cost battery). The same light is now painted ONCE into a cached
 * draw pass and never touched again, so scrolling, typing and animations have the whole frame budget.
 *
 * The colours are quiet on purpose (pale blue-greys on Frost, steel blue on Liquid Glass): enough variation behind
 * a pane for the glass to read as glass, never a coloured glow.
=======
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The light that lives behind every Liquid Glass surface.
 *
 * PERFORMANCE (rewritten for 2.0.1): the first version redrew four full-screen radial gradients on
 * every frame, which is a lot of work for a mid-range GPU once dozens of translucent glass panes sit
 * on top of it. Now the coloured blobs are drawn ONCE into a cached layer, and the "movement" is only
 * the GPU sliding / slowly rotating that finished layer (graphicsLayer properties change, nothing is
 * re-drawn or re-composed). Two layers move in opposite directions so it still looks like living light.
 *
 * It also never blurs anything, freezes when animation speed is "Off" or Battery Saver is on, and
 * does not even start its animation clock in those cases.
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
 */
@Composable
fun AuroraBackground(
    palette: DialerPalette,
    modifier: Modifier = Modifier
) {
<<<<<<< HEAD
    val src = palette.auroraColors.ifEmpty { listOf(palette.accent, palette.accentSoft, palette.accent, palette.accentSoft) }
    val strength = if (palette.isDark) 0.34f else 0.55f
    val blobs = remember(src, strength) {
        listOf(
            Blob(0.16f, 0.10f, 0.70f, src[0 % src.size], strength),
            Blob(0.92f, 0.38f, 0.62f, src[1 % src.size], strength * 0.85f),
            Blob(0.24f, 0.86f, 0.70f, src[2 % src.size], strength * 0.80f),
            Blob(0.86f, 0.96f, 0.52f, src[3 % src.size], strength * 0.65f)
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.opaqueBackground)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val longest = maxOf(w, h)
                // Brushes are built once per size, not per frame.
                val painted = blobs.map { b ->
                    val center = Offset(b.cx * w, b.cy * h)
                    val radius = b.radius * longest
                    Triple(center, radius, Brush.radialGradient(
                        colors = listOf(b.color.copy(alpha = b.alpha), Color.Transparent),
                        center = center,
                        radius = radius
                    ))
                }
                onDrawBehind {
                    painted.forEach { (center, radius, brush) ->
                        drawCircle(brush = brush, radius = radius, center = center)
=======
    val style = LocalAshuStyle.current
    val context = LocalContext.current
    val saver = remember(context) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
    val animate = style.animationsOn && !saver
    val src = palette.auroraColors.ifEmpty { listOf(palette.accent, palette.accentSoft, palette.accent, palette.accentSoft) }
    val strength = if (palette.isDark) 0.50f else 0.62f
    val layerA = remember(src, strength) {
        listOf(
            Blob(0.20f, 0.14f, 0.62f, src[0 % src.size], strength),
            Blob(0.88f, 0.40f, 0.58f, src[1 % src.size], strength * 0.9f)
        )
    }
    val layerB = remember(src, strength) {
        listOf(
            Blob(0.30f, 0.84f, 0.66f, src[2 % src.size], strength * 0.85f),
            Blob(0.80f, 0.94f, 0.50f, src[3 % src.size], strength * 0.7f)
        )
    }

    Box(modifier.fillMaxSize().background(palette.opaqueBackground)) {
        if (animate) {
            val transition = rememberInfiniteTransition(label = "aurora")
            val clockA by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(38_000, easing = LinearEasing)),
                label = "aurora-a"
            )
            val clockB by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(53_000, easing = LinearEasing)),
                label = "aurora-b"
            )
            AuroraLayer(layerA, phase = { clockA }, reverse = false)
            AuroraLayer(layerB, phase = { clockB }, reverse = true)
        } else {
            AuroraLayer(layerA, phase = { 0.2f }, reverse = false)
            AuroraLayer(layerB, phase = { 0.6f }, reverse = true)
        }
    }
}

private data class Blob(val cx: Float, val cy: Float, val radius: Float, val color: Color, val alpha: Float)

@Composable
private fun AuroraLayer(blobs: List<Blob>, phase: () -> Float, reverse: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read inside the layer block: only the layer is updated each frame, never recomposed.
                val a = phase() * 2f * PI.toFloat() * (if (reverse) -1f else 1f)
                translationX = size.width * 0.09f * cos(a)
                translationY = size.height * 0.05f * sin(a)
                rotationZ = 4f * sin(a)
                scaleX = 1.22f
                scaleY = 1.22f
            }
            .drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val h = size.height
                    blobs.forEach { b ->
                        val r = b.radius * maxOf(w, h)
                        val center = Offset(b.cx * w, b.cy * h)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(b.color.copy(alpha = b.alpha), Color.Transparent),
                                center = center,
                                radius = r
                            ),
                            radius = r,
                            center = center
                        )
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
                    }
                }
            }
    )
}
<<<<<<< HEAD

private data class Blob(val cx: Float, val cy: Float, val radius: Float, val color: Color, val alpha: Float)
=======
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
