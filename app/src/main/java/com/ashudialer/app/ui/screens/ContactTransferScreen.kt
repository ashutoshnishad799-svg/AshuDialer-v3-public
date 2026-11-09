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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.ContactTransferRepository
import com.ashudialer.app.data.ContactTransferRepository.Kind
import com.ashudialer.app.data.ContactTransferRepository.Place
import com.ashudialer.app.data.ContactTransferRepository.TransferContact
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.launch

/**
 * Copy & import contacts (v2.3). Pick where the contacts are now (this phone, a Google account, a SIM, or a .vcf file),
 * pick where they should go, tick the contacts, then Copy or Move. Any place can also be saved out as a .vcf file.
 */
@Composable
fun ContactTransferScreen(
    repository: ContactTransferRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onContactsChanged: () -> Unit = {}
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var from by remember { mutableStateOf<Place?>(null) }
    var to by remember { mutableStateOf<Place?>(null) }
    var fileContacts by remember { mutableStateOf<List<TransferContact>>(emptyList()) }
    var filePlace by remember { mutableStateOf<Place?>(null) }
    var contacts by remember { mutableStateOf<List<TransferContact>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(Pair(0, 0)) }
    var summary by remember { mutableStateOf<ContactTransferRepository.Summary?>(null) }
    var confirmMove by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var pendingExport by remember { mutableStateOf<List<TransferContact>>(emptyList()) }

    LaunchedEffect(Unit) {
        val list = repository.listPlaces()
        places = list
        from = list.firstOrNull()
        to = list.firstOrNull { it.kind == Kind.GOOGLE } ?: list.firstOrNull { it.kind == Kind.SIM }
    }

    LaunchedEffect(from, reloadTick) {
        val source = from
        selected = emptySet()
        problem = null
        if (source == null) {
            contacts = emptyList()
        } else if (source.kind == Kind.FILE) {
            contacts = fileContacts
        } else {
            loading = true
            val result = repository.readContacts(source)
            contacts = result.contacts
            problem = result.error
            loading = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                loading = true
                val result = repository.readVcf(uri)
                loading = false
                if (result.contacts.isEmpty()) {
                    Toast.makeText(context, result.error ?: "No contacts were found in that file.", Toast.LENGTH_LONG).show()
                } else {
                    val place = Place(id = "file", kind = Kind.FILE, title = "File", subtitle = transferFileName(context, uri))
                    fileContacts = result.contacts
                    filePlace = place
                    from = place
                    reloadTick++
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        val list = pendingExport
        pendingExport = emptyList()
        if (uri != null && list.isNotEmpty()) {
            scope.launch {
                val ok = repository.writeVcf(list, uri)
                Toast.makeText(
                    context,
                    if (ok) "Saved ${list.size} contact${if (list.size == 1) "" else "s"} to the file" else "Couldn't save the file",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun runTransfer(move: Boolean) {
        val source = from ?: return
        val target = to ?: return
        val chosen = contacts.filter { it.key in selected }
        if (chosen.isEmpty()) return
        scope.launch {
            working = true
            progress = Pair(0, chosen.size)
            val result = try {
                repository.transfer(chosen, source, target, move) { done, total -> progress = Pair(done, total) }
            } catch (e: Exception) {
                ContactTransferRepository.Summary(0, 0, chosen.size, 0, listOf("Something went wrong: ${e.message ?: "unknown error"}"))
            }
            working = false
            summary = result
            onContactsChanged()
            reloadTick++
        }
    }

    val source = from
    val target = to
    val chosenCount = contacts.count { it.key in selected }
    val canCopy = !working && source != null && target != null && target.kind != Kind.FILE && chosenCount > 0
    val moveAllowed = source != null && target != null && source.kind != Kind.FILE &&
        !(target.kind == Kind.SIM && source.kind != Kind.SIM)
    val canMove = canCopy && moveAllowed

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glassCircle(palette)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Copy contacts", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
        ) {
            item(key = "from-title") { TransferSectionTitle("Copy from") }
            item(key = "from-chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(places, key = { it.id }) { place ->
                        TransferPlaceChip(place = place, selected = from?.id == place.id, onClick = { from = place })
                    }
                    item(key = "file-chip") {
                        val file = filePlace
                        TransferPlaceChip(
                            place = file ?: Place(id = "file-pick", kind = Kind.FILE, title = "File (.vcf)", subtitle = "Choose a file"),
                            selected = from?.kind == Kind.FILE,
                            onClick = { importLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }

            item(key = "to-title") { TransferSectionTitle("Copy to") }
            item(key = "to-chips") {
                val destinations = places.filter { it.id != from?.id }
                if (destinations.isEmpty()) {
                    Text("No other place to copy to", fontSize = 13.sp, color = palette.textSecondary)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(destinations, key = { it.id }) { place ->
                            TransferPlaceChip(place = place, selected = to?.id == place.id, onClick = { to = place })
                        }
                    }
                }
            }
            if (target != null && target.kind == Kind.SIM && source != null && source.kind != Kind.SIM) {
                item(key = "sim-note") {
                    Text(
                        "A SIM keeps only a name and one number: contacts with two numbers become two entries and long names " +
                            "are shortened. Move is not offered into a SIM, so nothing is lost from the phone.",
                        fontSize = 12.sp, color = palette.textSecondary, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item(key = "list-title") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            loading -> "Reading contacts..."
                            contacts.isEmpty() -> "Contacts"
                            else -> "${contacts.size} contact${if (contacts.size == 1) "" else "s"}"
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    if (contacts.isNotEmpty()) {
                        val allSelected = chosenCount == contacts.size
                        Text(
                            if (allSelected) "Clear" else "Select all",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selected = if (allSelected) emptySet() else contacts.map { it.key }.toSet() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            val message = problem
            if (message != null) {
                item(key = "problem") {
                    Text(message, fontSize = 13.sp, color = palette.danger, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                }
            }

            items(contacts, key = { it.key }) { contact ->
                val isChecked = contact.key in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selected = if (isChecked) selected - contact.key else selected + contact.key }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isChecked, onCheckedChange = { checked ->
                        selected = if (checked) selected + contact.key else selected - contact.key
                    })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            contact.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        val sub = contact.numbers.joinToString("  \u00b7  ").ifBlank { contact.emails.firstOrNull() ?: "No number" }
                        Text(sub, fontSize = 12.5.sp, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.opaqueBackground)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                if (chosenCount == 0) "Tick the contacts to copy" else "$chosenCount selected",
                fontSize = 12.sp, color = palette.textSecondary, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { runTransfer(move = false) },
                    enabled = canCopy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Copy") }
                OutlinedButton(
                    onClick = { confirmMove = true },
                    enabled = canMove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Move") }
                OutlinedButton(
                    onClick = {
                        val chosen = contacts.filter { it.key in selected }
                        if (chosen.isNotEmpty()) {
                            pendingExport = chosen
                            exportLauncher.launch("contacts.vcf")
                        }
                    },
                    enabled = chosenCount > 0 && !working,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Save .vcf", maxLines = 1) }
            }
        }
    }

    if (working) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Copying contacts") },
            text = {
                Column {
                    Text("${progress.first} of ${progress.second}")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {}
        )
    }

    if (confirmMove && source != null && target != null) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            title = { Text("Move $chosenCount contact${if (chosenCount == 1) "" else "s"}?") },
            text = {
                Text(
                    "They are copied to ${placeLabel(target)} first and removed from ${placeLabel(source)} only after every one " +
                        "arrived safely."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmMove = false
                    runTransfer(move = true)
                }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { confirmMove = false }) { Text("Cancel") } }
        )
    }

    val done = summary
    if (done != null) {
        AlertDialog(
            onDismissRequest = { summary = null },
            title = { Text(if (done.failed == 0 && done.copied + done.alreadyThere > 0) "Done" else "Finished with problems") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Copied: ${done.copied}")
                    if (done.alreadyThere > 0) Text("Already there (skipped): ${done.alreadyThere}")
                    if (done.failed > 0) Text("Could not copy: ${done.failed}", color = palette.danger)
                    if (done.removedFromSource > 0) Text("Removed from the old place: ${done.removedFromSource}")
                    done.notes.forEach { Text(it, fontSize = 12.sp, color = palette.textSecondary) }
                }
            },
            confirmButton = { TextButton(onClick = { summary = null }) { Text("OK") } }
        )
    }
}

private fun placeLabel(place: Place): String =
    if (place.subtitle.isNotBlank() && place.kind != Kind.SIM) "${place.title} (${place.subtitle})" else place.title

@Composable
private fun TransferSectionTitle(text: String) {
    val palette = LocalDialerPalette.current
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
    )
}

@Composable
private fun TransferPlaceChip(place: Place, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalDialerPalette.current
    val icon = when (place.kind) {
        Kind.DEVICE -> Icons.Filled.PhoneAndroid
        Kind.GOOGLE -> Icons.Filled.Cloud
        Kind.SIM -> Icons.Filled.SimCard
        Kind.FILE -> Icons.Filled.FolderOpen
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.16f) else palette.cardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) palette.accent else palette.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                place.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) palette.accent else palette.textPrimary, maxLines = 1
            )
            if (place.subtitle.isNotBlank()) {
                Text(
                    place.subtitle, fontSize = 11.sp, color = palette.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)
                )
            }
        }
    }
}

private fun transferFileName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "contacts.vcf"
    } catch (e: Exception) {
        "contacts.vcf"
    }
}
