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
package com.ashudialer.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shares a contact.
 *
 * THE FIX for "WhatsApp does not accept the shared contact": contacts used to be shared only as a .vcf file attached
 * with the type text/vcard. WhatsApp (and several other chat apps) refuse that file, so nothing could be sent. Sharing is
 * now PLAIN TEXT by default - the saved name on the first line and each number on its own line - which every app takes
 * (WhatsApp, SMS, Telegram, mail, notes). A real contact card is still one tap away ([shareAsCard]) for apps that want it.
 */
object ContactShareHelper {

    /** Name + number as text. */
    fun share(context: Context, contact: Contact) {
        shareText(context, contact.displayName, listOf(contact.phoneNumber))
    }

    /**
     * [name] on the first line (left out for a bare number), then every number on its own line.
     * A person with two numbers is shared with both.
     */
    fun shareText(context: Context, name: String?, numbers: List<String>) {
        val cleanNumbers = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val title = name?.trim().orEmpty()
        val text = buildString {
            val nameIsJustTheNumber = cleanNumbers.size == 1 && cleanNumbers[0] == title
            if (title.isNotEmpty() && !nameIsJustTheNumber) appendLine(title)
            cleanNumbers.forEach { appendLine(it) }
        }.trim()
        if (text.isEmpty()) return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (title.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share contact"))
    }

    /**
     * Shares a number that has no saved contact behind it (Recents, an unknown caller). Plain text, so it can be pasted
     * into any chat without a vCard-aware receiver on the other end.
     */
    fun shareNumber(context: Context, phoneNumber: String, displayName: String? = null) {
        shareText(context, displayName, listOf(phoneNumber))
    }

    /** A real contact card (.vcf) for apps that want one (Gmail, Bluetooth, Contacts import). */
    fun shareAsCard(context: Context, name: String, numbers: List<String>) {
        val safeName = name.ifBlank { "Contact" }
        val cleanNumbers = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val vcard = buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:${escape(safeName)}")
            appendLine("N:${escape(safeName)};;;;")
            cleanNumbers.forEach { appendLine("TEL;TYPE=CELL:${escape(it)}") }
            appendLine("END:VCARD")
        }

        val dir = File(context.cacheDir, "shared_contacts").apply { mkdirs() }
        val file = File(dir, safeFileName(safeName))
        file.writeText(vcard, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("Contact", uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share contact card"))
    }

    fun shareAsCard(context: Context, contact: Contact) {
        shareAsCard(context, contact.displayName, listOf(contact.phoneNumber))
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
        .replace("\r", "")

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(48)
        .ifBlank { "contact" } + ".vcf"
}
