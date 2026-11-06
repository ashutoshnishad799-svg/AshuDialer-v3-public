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

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalAshuStyle
import com.ashudialer.app.util.AppIconManager
import com.ashudialer.app.util.AppIconOption

/** Personalize > App icon: the four launcher icons as tiles. Tapping one switches the home-screen icon. */
@Composable
fun AppIconPickerRow(palette: DialerPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentId by remember { mutableStateOf(AppIconManager.current(context).id) }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIconManager.options.forEach { option ->
            AppIconTile(
                option = option,
                selected = option.id == currentId,
                palette = palette,
                modifier = Modifier.weight(1f)
            ) {
                if (option.id != currentId) {
                    if (AppIconManager.apply(context, option)) {
                        currentId = option.id
                        Toast.makeText(
                            context,
                            "Icon changed to ${option.label}. Your launcher may take a few seconds to update.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "This phone would not let the icon change.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconTile(
    option: AppIconOption,
    selected: Boolean,
    palette: DialerPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val style = LocalAshuStyle.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.93f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "icon-tile-scale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.cardBorder,
        animationSpec = tween(style.ms(200)),
        label = "icon-tile-border"
    )
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier.bounceClick(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(62.dp)
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, borderColor, shape)
        ) {
            Image(
                painter = painterResource(option.preview),
                contentDescription = option.label,
                modifier = Modifier.fillMaxSize()
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            option.label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) palette.textPrimary else palette.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
