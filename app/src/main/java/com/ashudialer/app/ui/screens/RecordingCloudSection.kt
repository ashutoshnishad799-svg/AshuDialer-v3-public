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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.appcalls.cloud.CloudPrefs
import com.ashudialer.app.appcalls.cloud.CloudSync
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.appcalls.recording.RecordingMarks
import com.ashudialer.app.appcalls.recording.RecordingMetaStore
import com.ashudialer.app.appcalls.recording.RecordingPrefs
import com.ashudialer.app.telecom.CallRecorder
import com.ashudialer.app.ui.components.bounceClick
import com.ashudialer.app.ui.components.liquidGlass
import com.ashudialer.app.ui.theme.DialerPalette
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * v4 cloud backup card for the recording settings - fully optional, OFF by default.
 *
 * How it works, in plain words (the same words the card shows): the person picks a folder in
 * Android's own file picker - normally a folder inside the Google Drive app. Ashu Phone copies each
 * finished recording into that folder and the Drive app uploads it. There is no Google login in
 * Ashu Phone and nothing to set up in any developer console.
 *
 * "Keep recordings only in the cloud" is the optional mode where nothing stays on the phone: a new
 * recording waits in the app's private storage (never the public Music folder) just until its upload
 * is complete, then it is removed from the phone. If an upload can't finish, the recording is kept.
 */
@Composable
fun CloudBackupCard(palette: DialerPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { CloudPrefs(context) }
    val meta = remember { RecordingMetaStore(context) }
    var tick by remember { mutableIntStateOf(0) }

    // These are re-read whenever `tick` changes (after any switch, picker result or backup action).
    val enabled = remember(tick) { prefs.enabled }
    val folderPicked = remember(tick) { !prefs.folderUri.isNullOrBlank() }
    val pending = remember(tick) { meta.all().values.count { it.cloud == RecMeta.CLOUD_QUEUED } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (CloudSync.savePickedFolder(context, uri)) {
                prefs.enabled = true
                Toast.makeText(context, "Backup folder set", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Couldn't keep access to that folder. Try another one.", Toast.LENGTH_LONG).show()
            }
            tick++
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, RoundedCornerShape(22.dp))
            .animateContentSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (enabled && folderPicked) Icons.Filled.CloudDone else Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Back up to Google Drive", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(
                    "Optional. No login \u2014 you pick a folder, the Drive app uploads.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on && !folderPicked) {
                        picker.launch(null)
                    } else {
                        prefs.enabled = on
                        if (!on) CloudSync.cancelAll(context)
                        tick++
                    }
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = palette.solidBackground,
                    checkedTrackColor = palette.accent,
                    uncheckedTrackColor = palette.accentSoft,
                    uncheckedThumbColor = palette.textSecondary,
                    uncheckedBorderColor = palette.cardBorder
                )
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(Modifier.height(14.dp))
                if (!folderPicked) {
                    PrimaryPill("Choose a Drive folder", palette) { picker.launch(null) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "In the picker: open the \u2630 menu \u2192 Drive \u2192 make or open a folder \u2192 \u201CUse this folder\u201D.",
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentSoft)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                prefs.folderName.ifBlank { "Selected folder" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                if (CloudSync.isFolderUsable(context)) "Ready" else "Can't reach this folder \u2014 choose it again",
                                fontSize = 11.5.sp,
                                color = if (CloudSync.isFolderUsable(context)) palette.callGreen else palette.danger
                            )
                        }
                        Text(
                            "Change",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            modifier = Modifier.bounceClick { picker.launch(null) }.padding(6.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    SwitchRow("Back up new recordings automatically", "As soon as each call recording is saved", prefs.autoBackup, palette) {
                        prefs.autoBackup = it; tick++
                    }
                    SwitchRow("Wi\u2011Fi only", "Don't use mobile data for uploads", prefs.wifiOnly, palette) {
                        prefs.wifiOnly = it; tick++
                    }
                    SwitchRow("Only while charging", "Upload when the phone is plugged in", prefs.chargingOnly, palette) {
                        prefs.chargingOnly = it; tick++
                    }
                    SwitchRow(
                        "Keep recordings only in the cloud",
                        "After a successful upload the copy on this phone is deleted. Until then it waits in private app storage, never in Music.",
                        prefs.cloudOnly,
                        palette
                    ) { prefs.cloudOnly = it; tick++ }

                    Spacer(Modifier.height(8.dp))
                    val status = buildString {
                        if (pending > 0) append("$pending waiting to upload. ")
                        if (prefs.lastSuccessAt > 0L) {
                            append("Last backup: ")
                            append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(prefs.lastSuccessAt)))
                            append(". ")
                        }
                        if (prefs.lastError.isNotBlank()) append("Last problem: ${prefs.lastError}.")
                    }.trim()
                    if (status.isNotEmpty()) {
                        Text(status, fontSize = 12.sp, color = palette.textSecondary)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinePill("Back up all now", palette, Modifier.weight(1f)) {
                            val files = CallRecorder.listRecordings(context)
                            val queued = CloudSync.backUpAll(context, files) { f ->
                                if (f.absolutePath.startsWith(context.filesDir.absolutePath)) null
                                else resolveRecordingUri(context, f)
                            }
                            Toast.makeText(
                                context,
                                if (queued == 0) "Everything is already backed up" else "Queued $queued recordings",
                                Toast.LENGTH_SHORT
                            ).show()
                            tick++
                        }
                        OutlinePill("Open folder", palette, Modifier.weight(1f)) {
                            val intent = CloudSync.folderViewIntent(context)
                            try {
                                if (intent != null) context.startActivity(intent)
                                else Toast.makeText(context, "No folder chosen yet", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open the Google Drive app to see your folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small optional extras for the recorder itself. */
@Composable
fun RecordingExtrasCard(palette: DialerPalette, prefs: RecordingPrefs, modifier: Modifier = Modifier) {
    var showMark by remember { mutableStateOf(prefs.showMarkButton) }
    var minSec by remember { mutableIntStateOf(prefs.discardShorterThanSec) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text("Smart extras", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text("All optional", fontSize = 12.sp, color = palette.textSecondary)
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "\u201CMark moment\u201D button on the call screen",
            "Tap the flag during a call to bookmark an important moment. Jump to it later while listening.",
            showMark,
            palette
        ) { showMark = it; prefs.showMarkButton = it }
        Spacer(Modifier.height(6.dp))
        Text("Discard very short recordings", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text("Missed taps and instantly-ended calls aren't worth keeping.", fontSize = 12.sp, color = palette.textSecondary)
        Spacer(Modifier.height(8.dp))
        Segmented(
            options = listOf("0" to "Keep all", "3" to "< 3 s", "5" to "< 5 s", "10" to "< 10 s"),
            selected = minSec.toString(),
            palette = palette
        ) { minSec = it.toInt(); prefs.discardShorterThanSec = minSec }
    }
}

@Composable
private fun PrimaryPill(label: String, palette: DialerPalette, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Bold,
        color = palette.solidBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.accent)
            .bounceClick(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun OutlinePill(label: String, palette: DialerPalette, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.accent,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(palette.accentSoft)
            .bounceClick(onClick = onClick)
            .padding(vertical = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/**
 * The flag button on the call screen's recording bar. Each tap bookmarks the current moment of the
 * recording in progress (it pops, and the count goes up). Hidden when turned off in recording settings.
 */
@Composable
internal fun MarkMomentButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enabled = remember { RecordingPrefs(context).showMarkButton }
    if (!enabled) return
    var count by remember { mutableIntStateOf(RecordingMarks.count) }
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(popped) {
        if (popped) {
            delay(170)
            popped = false
        }
    }
    val scale by animateFloatAsState(if (popped) 1.3f else 1f, spring(dampingRatio = 0.35f), label = "mark-pop")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFFB300).copy(alpha = 0.28f))
            .clickable {
                if (RecordingMarks.markNow()) {
                    count = RecordingMarks.count
                    popped = true
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Bookmark,
            contentDescription = "Mark this moment",
            tint = Color(0xFF8A5A00),
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5C3B00))
        }
    }
}
