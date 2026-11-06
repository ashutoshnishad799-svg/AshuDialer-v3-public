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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 */
@Composable
fun AuroraBackground(
    palette: DialerPalette,
    modifier: Modifier = Modifier
) {
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
                    }
                }
            }
    )
}

private data class Blob(val cx: Float, val cy: Float, val radius: Float, val color: Color, val alpha: Float)
