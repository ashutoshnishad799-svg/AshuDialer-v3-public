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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ashudialer.app.ui.theme.AshuStyle
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalButtonDepth

/**
 * The one glass modifier for the whole app. Every card, sheet, chip and key goes through here, so a
 * change in this file (or a slider in Personalize) restyles every screen at once.
 *
 * v4 changes (all driven by [AshuStyle], defaults reproduce the v3 look):
 *  - Glass intensity: how frosted / opaque the panes are.
 *  - Corner scale: every rounded corner grows or shrinks together (circles stay circles).
 *  - Specular edge: a bright top-left rim that fades toward the bottom-right, like light catching
 *    real glass, plus a hairline outer edge so a pane never melts into the background.
 *  - Real Liquid Glass panes for palettes with `glass = true`: a milky white pane (not a tinted card
 *    colour) so the moving aurora light behind it shows through.
 */
@Composable
fun Modifier.liquidGlass(
    palette: DialerPalette,
    shape: Shape = RoundedCornerShape(20.dp),
    tintAlpha: Float = 0.55f,
    borderAlpha: Float = 1f
): Modifier {
    val style = LocalAshuStyle.current
    val shp = rememberScaledShape(shape, style.cornerScale)

    // Pure Black skips the whole glass look - see DialerPalette.flatSurfaces' doc comment. Still
    // respects buttonDepth's raised/flat choice, just without any translucent layers.
    if (palette.flatSurfaces) {
        val isRaisedFlat = LocalButtonDepth.current == "raised"
        val flatBorder = palette.cardBorder.copy(alpha = (if (isRaisedFlat) 1f else 0.85f) * borderAlpha)
        return this
            .let { m ->
                if (isRaisedFlat) {
                    m.shadow(
                        elevation = 4.dp,
                        shape = shp,
                        ambientColor = Color.Black.copy(alpha = 0.6f),
                        spotColor = Color.Black.copy(alpha = 0.6f)
                    )
                } else {
                    m
                }
            }
            .clip(shp)
            .background(palette.cardBackground, shp)
            .border(1.dp, flatBorder, shp)
    }

    if (palette.glass) {
        return this.glassPane(palette, shp, style, tintAlpha, borderAlpha)
    }

    // ---- Classic glass (every non-"glass" palette) -------------------------------------------
    val intensity = style.glassIntensity.coerceIn(0f, 1f)
    // 0.6 (the default) gives ~1.0 so the v3 look is unchanged; 0 = clear, 1 = nearly opaque.
    val tintFactor = 0.45f + 0.9f * intensity
    val shine = if (style.specular) 0.5f + 0.8f * intensity else 0f
    val glassTint = palette.cardBackground.copy(alpha = (tintAlpha * tintFactor).coerceIn(0.08f, 0.98f))
    val highlightBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = ((if (palette.isDark) 0.22f else 0.45f) * shine).coerceIn(0f, 1f)),
            Color.White.copy(alpha = ((if (palette.isDark) 0.07f else 0.08f) * shine).coerceIn(0f, 1f)),
            Color.White.copy(alpha = 0f)
        )
    )
    // On near-white cards a white highlight adds nothing; the border does all the work.
    val cardIsNearWhite = !palette.isDark &&
        palette.cardBackground.red > 0.92f &&
        palette.cardBackground.green > 0.92f &&
        palette.cardBackground.blue > 0.92f
    val isRaised = LocalButtonDepth.current == "raised"
    val baseStrokeAlpha = if (palette.isDark) 0.26f else 0.65f
    val edgeBoost = if (style.specular) 1f else 0.55f

    val strokeBrush: Brush = if (cardIsNearWhite) {
        val c = Color.Black.copy(alpha = (if (isRaised) 0.16f else 0.10f) * borderAlpha)
        Brush.linearGradient(listOf(c, c))
    } else {
        val a = ((baseStrokeAlpha + if (isRaised) 0.12f else 0f) * borderAlpha * edgeBoost).coerceIn(0f, 1f)
        if (style.specular) {
            // bright where light hits (top-left), softer toward the bottom-right
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = (a * 1.35f).coerceAtMost(1f)),
                    Color.White.copy(alpha = a * 0.45f),
                    Color.White.copy(alpha = a * 0.8f)
                )
            )
        } else {
            Brush.linearGradient(listOf(Color.White.copy(alpha = a), Color.White.copy(alpha = a)))
        }
    }

    return this
        .let { m ->
            if (isRaised) {
                m.shadow(
                    elevation = 4.dp,
                    shape = shp,
                    ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.5f else 0.22f),
                    spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.5f else 0.22f)
                )
            } else {
                m
            }
        }
        .clip(shp)
        .background(glassTint, shp)
        .background(highlightBrush, shp)
        .border(1.dp, strokeBrush, shp)
}

/**
 * The real "Liquid Glass" pane (palettes with glass = true).
 *
 * Layers, bottom to top:
 *  1. a vertical milky-white wash - lighter at the top, thinner at the bottom, like a lens catching light;
 *  2. a diagonal sheen that picks up a trace of the accent colour;
 *  3. a bright specular rim (1dp) that fades from top-left to bottom-right;
 *  4. a 0.5dp darker hairline on the very outside, so the pane keeps a crisp edge on any background.
 */
private fun Modifier.glassPane(
    palette: DialerPalette,
    shape: Shape,
    style: AshuStyle,
    tintAlpha: Float,
    borderAlpha: Float
): Modifier {
    val k = style.glassIntensity.coerceIn(0f, 1f)
    val dark = palette.isDark
    // Callers that ask for a heavier pane (BottomNav passes 0.9) get a proportionally milkier one.
    val boost = (tintAlpha / 0.55f).coerceIn(0.7f, 1.7f)

    val topA = (if (dark) 0.075f + 0.14f * k else 0.46f + 0.36f * k) * boost
    val bottomA = (if (dark) 0.035f + 0.075f * k else 0.32f + 0.30f * k) * boost
    val fill = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = topA.coerceIn(0f, 0.96f)),
            Color.White.copy(alpha = bottomA.coerceIn(0f, 0.92f))
        )
    )
    val sheen = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.16f else 0.35f),
            Color.Transparent,
            palette.accent.copy(alpha = if (dark) 0.07f else 0.05f)
        )
    )
    val rimBright = if (style.specular) (if (dark) 0.62f else 0.95f) else (if (dark) 0.22f else 0.60f)
    val rim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = (rimBright * borderAlpha).coerceIn(0f, 1f)),
            Color.White.copy(alpha = (rimBright * 0.16f * borderAlpha).coerceIn(0f, 1f)),
            Color.White.copy(alpha = (rimBright * 0.42f * borderAlpha).coerceIn(0f, 1f))
        )
    )
    val hairline = if (dark) Color.Black.copy(alpha = 0.22f) else Color(0xFF0F1B3A).copy(alpha = 0.10f)

    return this
        .clip(shape)
        .background(fill, shape)
        .background(sheen, shape)
        .border(1.dp, rim, shape)
        .border(0.5.dp, hairline, shape)
}

/** Scales every rounded corner of [base] by [k]; each corner is capped at half the shortest side. */
private class ScaledCornerShape(
    private val base: CornerBasedShape,
    private val k: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val maxRadius = minOf(size.width, size.height) / 2f
        fun radius(c: CornerSize): Float = (c.toPx(size, density) * k).coerceIn(0f, maxRadius)
        val topStart = radius(base.topStart)
        val topEnd = radius(base.topEnd)
        val bottomEnd = radius(base.bottomEnd)
        val bottomStart = radius(base.bottomStart)
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(if (ltr) topStart else topEnd),
                topRightCornerRadius = CornerRadius(if (ltr) topEnd else topStart),
                bottomRightCornerRadius = CornerRadius(if (ltr) bottomEnd else bottomStart),
                bottomLeftCornerRadius = CornerRadius(if (ltr) bottomStart else bottomEnd)
            )
        )
    }
}

@Composable
private fun rememberScaledShape(shape: Shape, k: Float): Shape =
    remember(shape, k) {
        // Circles (avatars, round buttons) and anything that is not a rounded-corner shape stay as is.
        if (k == 1f || shape !is CornerBasedShape || shape == CircleShape) shape
        else ScaledCornerShape(shape, k)
    }

/** Rounded-rect glass panel - the default for cards, rows, sheets, and settings items. */
@Composable
fun Modifier.glassCard(
    palette: DialerPalette,
    corner: Dp = 18.dp,
    tintAlpha: Float = 0.55f
): Modifier = this.liquidGlass(palette, RoundedCornerShape(corner), tintAlpha)

/** Circular glass chip - for avatar rings, icon badges, and pill buttons. */
@Composable
fun Modifier.glassCircle(
    palette: DialerPalette,
    tintAlpha: Float = 0.55f
): Modifier = this.liquidGlass(palette, CircleShape, tintAlpha)
