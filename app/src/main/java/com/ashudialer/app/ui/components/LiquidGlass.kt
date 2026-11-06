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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
<<<<<<< HEAD
import androidx.compose.ui.geometry.Offset
=======
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
<<<<<<< HEAD
import androidx.compose.ui.graphics.SolidColor
=======
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.ui.theme.LocalButtonDepth

/**
 * The one glass modifier for the whole app. Every card, sheet, chip and key goes through here, so a
 * change in this file (or a slider in Personalize) restyles every screen at once.
 *
 * PERFORMANCE (rewritten for 2.0.1): the glass used to be four stacked modifiers (clip, background,
 * background, border), each with its own outline work, repeated for every card on screen. It is now ONE
 * cached draw pass: the outline is built once per size, the brushes are built once per look (remembered),
 * and drawing a card is a handful of draw calls. Same look, much less work per frame.
 *
 * Driven by [com.ashudialer.app.ui.theme.AshuStyle]: glass frosting, corner scale and the shiny edge.
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
    val isRaised = LocalButtonDepth.current == "raised"

    // Pure Black skips the whole glass look - see DialerPalette.flatSurfaces' doc comment.
    if (palette.flatSurfaces) {
        val flatBorder = palette.cardBorder.copy(alpha = (if (isRaised) 1f else 0.85f) * borderAlpha)
        return this
            .let { m ->
                if (isRaised) {
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

    val look = remember(palette, style.glassIntensity, style.specular, tintAlpha, borderAlpha, isRaised) {
        GlassLook.build(palette, style.glassIntensity, style.specular, tintAlpha, borderAlpha, isRaised)
    }

    return this
        .let { m ->
            if (isRaised && !palette.glass) {
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
        .drawWithCache {
            val outline = shp.createOutline(size, layoutDirection, this)
            val outlinePath = Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }
            // A stroke is centred on the edge and the clip removes the outer half, so the visible
            // width is half of what is passed here.
            val rimWidth = 2f * look.rimDp * density
            val hairWidth = 1f * density
<<<<<<< HEAD
            // The thin light line just inside the top edge (what makes a pane read as glass). It is a straight
            // line pulled in from the rounded corners, so it never needs a second path.
            val cornerPx = if (outline is Outline.Rounded) outline.roundRect.topLeftCornerRadius.x else 0f
            val topInset = (cornerPx * 0.6f + 2f * density).coerceAtMost(size.width / 2f)
            val topY = 1.5f * density
=======
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
            onDrawBehind {
                look.fillColor?.let { drawPath(outlinePath, color = it) }
                look.fillBrush?.let { drawPath(outlinePath, brush = it) }
                look.sheen?.let { drawPath(outlinePath, brush = it) }
                drawPath(outlinePath, brush = look.rim, style = Stroke(width = rimWidth))
                look.hairline?.let { drawPath(outlinePath, color = it, style = Stroke(width = hairWidth)) }
<<<<<<< HEAD
                look.topLight?.let { c ->
                    drawLine(c, Offset(topInset, topY), Offset(size.width - topInset, topY), strokeWidth = density)
                }
=======
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
            }
        }
}

/** Everything needed to paint one glass look, built once and remembered. */
private class GlassLook(
    val fillColor: Color?,
    val fillBrush: Brush?,
    val sheen: Brush?,
    val rim: Brush,
    val rimDp: Float,
<<<<<<< HEAD
    val hairline: Color?,
    val topLight: Color? = null
=======
    val hairline: Color?
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
) {
    companion object {
        fun build(
            palette: DialerPalette,
            glassIntensity: Float,
            specular: Boolean,
            tintAlpha: Float,
            borderAlpha: Float,
            isRaised: Boolean
        ): GlassLook {
            val k = glassIntensity.coerceIn(0f, 1f)
            val dark = palette.isDark

            if (palette.glass) {
<<<<<<< HEAD
                // v2.1 "classic glass". The earlier look stacked a tinted gradient sheen and a three-stop rainbow-ish
                // rim on every pane, which read as cartoony / AI-made and cost three gradient draws per card.
                // Now a pane is: ONE translucent white fill (lighter at the top, a little clearer at the bottom),
                // ONE clean 1dp edge and ONE thin light line under the top edge. No colour tint, no gradient rim.
                // Callers that ask for a heavier pane (BottomNav) get a proportionally milkier one.
                val boost = (tintAlpha / 0.55f).coerceIn(0.7f, 1.7f)
                val topA = (if (dark) 0.10f + 0.12f * k else 0.60f + 0.26f * k) * boost
                val bottomA = (if (dark) 0.05f + 0.07f * k else 0.42f + 0.24f * k) * boost
                val edge = if (dark) Color.White.copy(alpha = if (specular) 0.20f else 0.12f)
                else Color(0xFF0F1B3A).copy(alpha = if (specular) 0.12f else 0.08f)
=======
                // Callers that ask for a heavier pane (BottomNav passes 0.9) get a proportionally milkier one.
                val boost = (tintAlpha / 0.55f).coerceIn(0.7f, 1.7f)
                val topA = (if (dark) 0.075f + 0.14f * k else 0.46f + 0.36f * k) * boost
                val bottomA = (if (dark) 0.035f + 0.075f * k else 0.32f + 0.30f * k) * boost
                val rimBright = if (specular) (if (dark) 0.62f else 0.95f) else (if (dark) 0.22f else 0.60f)
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
                return GlassLook(
                    fillColor = null,
                    fillBrush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = topA.coerceIn(0f, 0.96f)),
                            Color.White.copy(alpha = bottomA.coerceIn(0f, 0.92f))
                        )
                    ),
<<<<<<< HEAD
                    sheen = null,
                    rim = SolidColor(edge.copy(alpha = (edge.alpha * borderAlpha).coerceIn(0f, 1f))),
                    rimDp = 1f,
                    hairline = null,
                    topLight = if (specular) Color.White.copy(alpha = if (dark) 0.14f else 0.95f) else null
=======
                    sheen = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.16f else 0.35f),
                            Color.Transparent,
                            palette.accent.copy(alpha = if (dark) 0.07f else 0.05f)
                        )
                    ),
                    rim = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = (rimBright * borderAlpha).coerceIn(0f, 1f)),
                            Color.White.copy(alpha = (rimBright * 0.16f * borderAlpha).coerceIn(0f, 1f)),
                            Color.White.copy(alpha = (rimBright * 0.42f * borderAlpha).coerceIn(0f, 1f))
                        )
                    ),
                    rimDp = 1f,
                    hairline = if (dark) Color.Black.copy(alpha = 0.22f) else Color(0xFF0F1B3A).copy(alpha = 0.10f)
>>>>>>> e4c51b79b3e8b70a4d59613d7f5ef21885ea7436
                )
            }

            // Classic glass (every non-"glass" palette)
            val tintFactor = 0.45f + 0.9f * k
            val shine = if (specular) 0.5f + 0.8f * k else 0f
            val cardIsNearWhite = !dark &&
                palette.cardBackground.red > 0.92f &&
                palette.cardBackground.green > 0.92f &&
                palette.cardBackground.blue > 0.92f
            val tint = palette.cardBackground.copy(alpha = (tintAlpha * tintFactor).coerceIn(0.08f, 0.98f))
            val highlight = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = ((if (dark) 0.22f else 0.45f) * shine).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = ((if (dark) 0.07f else 0.08f) * shine).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = 0f)
                )
            )
            val baseStroke = if (dark) 0.26f else 0.65f
            val edge = if (specular) 1f else 0.55f
            val rim: Brush = if (cardIsNearWhite) {
                val c = Color.Black.copy(alpha = ((if (isRaised) 0.16f else 0.10f) * borderAlpha).coerceIn(0f, 1f))
                Brush.linearGradient(listOf(c, c))
            } else {
                val a = ((baseStroke + if (isRaised) 0.12f else 0f) * borderAlpha * edge).coerceIn(0f, 1f)
                if (specular) {
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
            return GlassLook(
                fillColor = tint,
                fillBrush = null,
                sheen = highlight,
                rim = rim,
                rimDp = 1f,
                hairline = null
            )
        }
    }
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
