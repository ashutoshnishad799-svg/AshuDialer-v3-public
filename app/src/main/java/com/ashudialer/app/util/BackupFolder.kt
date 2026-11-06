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
package com.ashudialer.app.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where Local Backup files go.
 *
 * Before, "Export Backup" opened Android's Save-as box and dropped one file wherever it happened to be (usually loose in
 * Downloads). Now the person picks a folder ONCE; every backup after that is written into a folder called
 * "Ashu Phone Backups" inside it, with the date in the file name, and no picker is needed again.
 */
object BackupFolder {

    const val DIR_NAME = "Ashu Phone Backups"
    private const val PREFS = "ashu_backup_folder"
    private const val KEY_TREE = "tree_uri"

    /** The remembered folder, or null when none was chosen (or Android took the access away). */
    fun savedTree(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TREE, null) ?: return null
        val uri = Uri.parse(raw)
        val stillGranted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return if (stillGranted) uri else null
    }

    /** A short readable name for the remembered folder ("Documents", "Backups" ...), or null. */
    fun savedTreeLabel(context: Context): String? {
        val tree = savedTree(context) ?: return null
        return try {
            DocumentsContract.getTreeDocumentId(tree).substringAfter(':', "").ifBlank { "Phone storage" }
        } catch (_: Exception) {
            null
        }
    }

    fun remember(context: Context, tree: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some providers do not allow keeping access; the backup below still works for this one save.
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TREE, tree.toString()).apply()
    }

    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TREE).apply()
    }

    /** Where the folder picker should start: straight on the phone's own storage. */
    fun startUri(): Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:")

    /**
     * Writes the backup into <tree>/Ashu Phone Backups/ashudialer_backup_<date>.adlb and returns the message to show.
     * Never throws: on a problem it returns an explanation and forgets a folder that no longer works.
     */
    fun saveAndDescribe(context: Context, tree: Uri, bytes: ByteArray): String {
        val name = "ashudialer_backup_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".adlb"
        return try {
            val resolver = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val dirUri = findChildFolder(resolver, tree, rootId, DIR_NAME)
                ?: DocumentsContract.createDocument(resolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, DIR_NAME)
                ?: throw IOException("could not create the \"$DIR_NAME\" folder")
            val fileUri = DocumentsContract.createDocument(resolver, dirUri, "application/octet-stream", name)
                ?: throw IOException("could not create the backup file")
            val stream = resolver.openOutputStream(fileUri) ?: throw IOException("could not open the backup file")
            stream.use { it.write(bytes) }
            "Backup saved in \"$DIR_NAME\" as $name. Keep the PIN somewhere safe, it can't be recovered."
        } catch (e: Exception) {
            forget(context)
            "Couldn't save the backup there (${e.message}). Tap Export again and choose a folder."
        }
    }

    private fun findChildFolder(resolver: ContentResolver, tree: Uri, parentId: String, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name && c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        return null
    }
}
