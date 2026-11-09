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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.trash.TrashItemEntity
import com.ashudialer.app.data.trash.TrashRepository
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.components.staggerIn
import com.ashudialer.app.ui.theme.LocalDialerPalette

private enum class TrashFilter(val label: String) {
    ALL("All"),
    CONTACTS("Contacts"),
    CALLS("Recent calls"),
    UNSAVED("Unsaved numbers")
}

private const val TRASH_DAY_MS = 24L * 60L * 60L * 1000L

/**
 * Trash (v2.3): deleted contacts and deleted calls (from saved AND unsaved numbers) wait here for 30 days and can be put
 * back. The switch at the top turns Trash off or on; while it is off a delete is permanent again and nothing new is kept.
 */
@Composable
fun TrashScreen(
    items: List<TrashItemEntity>,
    trashEnabled: Boolean,
    onBack: () -> Unit,
    onToggleTrash: (Boolean) -> Unit,
    onRestore: (TrashItemEntity) -> Unit,
    onDeleteForever: (TrashItemEntity) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var filter by remember { mutableStateOf(TrashFilter.ALL) }
    var confirmDelete by remember { mutableStateOf<TrashItemEntity?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    val shown = remember(items, filter) {
        items.filter { item ->
            when (filter) {
                TrashFilter.ALL -> true
                TrashFilter.CONTACTS -> item.kind == TrashRepository.KIND_CONTACT
                TrashFilter.CALLS -> item.kind == TrashRepository.KIND_CALLS
                TrashFilter.UNSAVED -> item.kind == TrashRepository.KIND_CALLS && item.title.none { it.isLetter() }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glassCircle(palette)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Trash", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                Text(
                    "Empty Trash",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.danger,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { confirmEmpty = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "trash-switch") {
                SettingsCard(palette) {
                    ToggleRow(
                        icon = Icons.Filled.Delete,
                        title = "Use Trash",
                        subtitle = if (trashEnabled) {
                            "Deleted contacts and calls stay here for ${TrashRepository.RETENTION_DAYS} days"
                        } else {
                            "Off: deleting is permanent and nothing new is kept"
                        },
                        checked = trashEnabled,
                        palette = palette,
                        onToggle = onToggleTrash
                    )
                }
            }

            item(key = "trash-filters") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TrashFilter.values().toList(), key = { it.name }) { option ->
                        val selected = option == filter
                        Text(
                            option.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) palette.accent else palette.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) palette.accent.copy(alpha = 0.16f) else palette.cardBackground)
                                .clickable { filter = option }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                item(key = "trash-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Delete, contentDescription = null,
                            tint = palette.textSecondary.copy(alpha = 0.45f), modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (items.isEmpty()) "Trash is empty" else "Nothing here",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (trashEnabled) "Deleted contacts and calls will show up here" else "Trash is switched off",
                            fontSize = 13.sp, color = palette.textSecondary
                        )
                    }
                }
            } else {
                items(shown, key = { it.id }) { item ->
                    TrashRow(
                        item = item,
                        onRestore = { onRestore(item) },
                        onDeleteForever = { confirmDelete = item },
                        modifier = Modifier.staggerIn(shown.indexOf(item).coerceAtLeast(0))
                    )
                }
            }
        }
    }

    val pending = confirmDelete
    if (pending != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${pending.title} forever?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = null
                    onDeleteForever(pending)
                }) { Text("Delete forever", color = palette.danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
    if (confirmEmpty) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Trash?") },
            text = { Text("Everything in Trash will be deleted for good.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmEmpty = false
                    onEmptyTrash()
                }) { Text("Empty Trash", color = palette.danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrashRow(
    item: TrashItemEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val isContact = item.kind == TrashRepository.KIND_CONTACT
    val unsaved = !isContact && item.title.none { it.isLetter() }
    val now = System.currentTimeMillis()
    val daysLeft = ((item.expiresAtMillis - now + TRASH_DAY_MS - 1) / TRASH_DAY_MS).coerceAtLeast(0)
    val deletedAgo = trashAgo(now - item.deletedAtMillis)
    val kindLabel = when {
        isContact -> "Contact"
        unsaved -> "Unsaved number"
        else -> "Recent calls"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isContact) Icons.Filled.Person else Icons.Filled.Phone,
                    contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.subtitle, fontSize = 13.sp, color = palette.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$kindLabel \u00b7 deleted $deletedAgo \u00b7 " + if (daysLeft <= 0L) "removed today" else "$daysLeft ${if (daysLeft == 1L) "day" else "days"} left",
                fontSize = 11.5.sp, color = palette.textSecondary.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f)
            )
            Text(
                "Restore",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRestore)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
            IconButton(onClick = onDeleteForever, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.DeleteForever, contentDescription = "Delete ${item.title} forever",
                    tint = palette.danger, modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun trashAgo(diffMillis: Long): String {
    val minutes = diffMillis / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 24L * 60L -> "${minutes / 60L}h ago"
        else -> "${minutes / (24L * 60L)}d ago"
    }
}
