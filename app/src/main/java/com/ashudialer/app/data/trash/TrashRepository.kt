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
package com.ashudialer.app.data.trash

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Base64
import com.ashudialer.app.data.CallLogRepository
import com.ashudialer.app.data.SystemCallLogEntry
import com.ashudialer.app.data.SystemCallLogRepository
import com.ashudialer.app.data.db.CallDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What came back from putting something out of Trash. */
class TrashRestoreResult(val success: Boolean, val message: String)

/**
 * Trash for deleted contacts and deleted calls (v2.3).
 *
 * When Trash is on, the delete paths of the app first call [saveContacts] / [saveCalls], which keep a full copy for
 * [RETENTION_DAYS] days. A contact is kept field by field (every data row of every raw contact, photo thumbnail
 * included) so restoring gives the contact back the way it was, in its original account when that still exists.
 * Calls are kept as number / name / type / time / length and written back into the phone's call log on restore.
 *
 * Nothing here needs root: it only uses the Contacts and Call-log permissions the dialer already holds.
 */
class TrashRepository(
    private val context: Context,
    private val dao: TrashDao,
    private val systemCallLogRepository: SystemCallLogRepository,
    private val callLogRepository: CallLogRepository
) {
    companion object {
        const val KIND_CONTACT = "CONTACT"
        const val KIND_CALLS = "CALLS"
        const val RETENTION_DAYS = 30
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MAX_BLOB_BYTES = 600_000

        private val DATA_PROJECTION: Array<String> =
            Array(16) { index -> if (index == 0) ContactsContract.Data.MIMETYPE else "data$index" }
    }

    val items: Flow<List<TrashItemEntity>> = dao.observeAll()

    /** Removes everything older than the retention time. Safe to call as often as wanted. */
    suspend fun purgeExpired() {
        withContext(Dispatchers.IO) { dao.deleteExpired(System.currentTimeMillis()) }
    }

    suspend fun deleteForever(id: Long) {
        withContext(Dispatchers.IO) { dao.deleteById(id) }
    }

    suspend fun empty() {
        withContext(Dispatchers.IO) { dao.clear() }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Saving (called just BEFORE the real delete)
    // ---------------------------------------------------------------------------------------------------------------

    /** Keeps a full copy of each contact. Returns how many were kept. */
    suspend fun saveContacts(contactIds: Set<String>): Int = withContext(Dispatchers.IO) {
        var saved = 0
        val now = System.currentTimeMillis()
        for (id in contactIds) {
            try {
                val snapshot = snapshotContact(id) ?: continue
                dao.insert(
                    TrashItemEntity(
                        kind = KIND_CONTACT,
                        title = snapshot.name,
                        subtitle = snapshot.subtitle,
                        payload = snapshot.json,
                        itemCount = 1,
                        deletedAtMillis = now,
                        expiresAtMillis = now + RETENTION_DAYS * DAY_MS
                    )
                )
                saved++
            } catch (e: Exception) {
                // one contact failing must never stop the others (or the delete) - it just isn't kept
            }
        }
        saved
    }

    /** Keeps these calls as one Trash entry titled [title] (a name, or the number for an unsaved caller). */
    suspend fun saveCalls(title: String, calls: List<SystemCallLogEntry>): Boolean = withContext(Dispatchers.IO) {
        if (calls.isEmpty()) return@withContext false
        try {
            val array = JSONArray()
            for (c in calls) {
                array.put(
                    JSONObject()
                        .put("n", c.phoneNumber)
                        .put("name", c.displayName ?: "")
                        .put("d", c.direction.name)
                        .put("t", c.timestampMillis)
                        .put("s", c.durationSeconds)
                        .put("p", c.photoUri ?: "")
                )
            }
            val numbers = calls.map { it.phoneNumber }.distinct()
            val countText = if (calls.size == 1) "1 call" else "${calls.size} calls"
            val subtitle = if (numbers.size == 1 && numbers[0] != title) "$countText \u00b7 ${numbers[0]}" else countText
            val now = System.currentTimeMillis()
            dao.insert(
                TrashItemEntity(
                    kind = KIND_CALLS,
                    title = title.ifBlank { numbers.firstOrNull().orEmpty() },
                    subtitle = subtitle,
                    payload = array.toString(),
                    itemCount = calls.size,
                    deletedAtMillis = now,
                    expiresAtMillis = now + RETENTION_DAYS * DAY_MS
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Restoring
    // ---------------------------------------------------------------------------------------------------------------

    suspend fun restore(item: TrashItemEntity): TrashRestoreResult = withContext(Dispatchers.IO) {
        try {
            when (item.kind) {
                KIND_CONTACT -> {
                    val ok = restoreContact(item.payload)
                    if (ok) {
                        dao.deleteById(item.id)
                        TrashRestoreResult(true, "${item.title} restored")
                    } else {
                        TrashRestoreResult(false, "Couldn't restore ${item.title}. Check the Contacts permission and try again.")
                    }
                }
                KIND_CALLS -> {
                    val count = restoreCalls(item.payload)
                    dao.deleteById(item.id)
                    TrashRestoreResult(true, if (count == 1) "Call restored" else "$count calls restored")
                }
                else -> TrashRestoreResult(false, "Unknown item")
            }
        } catch (e: Exception) {
            TrashRestoreResult(false, "Couldn't restore: ${e.message ?: "unknown error"}")
        }
    }

    private class ContactSnapshot(val name: String, val subtitle: String, val json: String)

    private fun snapshotContact(contactId: String): ContactSnapshot? {
        val id = contactId.toLongOrNull() ?: return null
        val resolver = context.contentResolver

        var name = ""
        var starred = 0
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.STARRED),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(id.toString()),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0).orEmpty()
                starred = c.getInt(1)
            }
        }

        val rawArray = JSONArray()
        val phones = ArrayList<String>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME
            ),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(id.toString()),
            null
        )?.use { raws ->
            while (raws.moveToNext()) {
                val rawId = raws.getLong(0)
                val rawObject = JSONObject()
                raws.getString(1)?.let { rawObject.put("accountType", it) }
                raws.getString(2)?.let { rawObject.put("accountName", it) }
                val rows = JSONArray()
                resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    DATA_PROJECTION,
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
                    arrayOf(rawId.toString()),
                    null
                )?.use { data ->
                    while (data.moveToNext()) {
                        val mime = data.getString(0) ?: continue
                        if (mime == ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE) continue
                        val strings = JSONObject()
                        val longs = JSONObject()
                        val blobs = JSONObject()
                        for (i in 1..15) {
                            // data14 of a photo row is an id the provider hands out itself - never copy it
                            if (i == 14 && mime == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE) continue
                            val key = "data$i"
                            when (data.getType(i)) {
                                Cursor.FIELD_TYPE_STRING -> strings.put(key, data.getString(i))
                                Cursor.FIELD_TYPE_INTEGER -> longs.put(key, data.getLong(i))
                                Cursor.FIELD_TYPE_BLOB -> {
                                    val bytes = data.getBlob(i)
                                    if (bytes != null && bytes.size <= MAX_BLOB_BYTES) {
                                        blobs.put(key, Base64.encodeToString(bytes, Base64.NO_WRAP))
                                    }
                                }
                                else -> Unit
                            }
                        }
                        rows.put(JSONObject().put("m", mime).put("s", strings).put("i", longs).put("b", blobs))
                        if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                            val number = strings.optString("data1", "")
                            if (number.isNotBlank()) phones.add(number)
                        }
                    }
                }
                rawObject.put("rows", rows)
                rawArray.put(rawObject)
            }
        }
        if (rawArray.length() == 0) return null

        val title = name.ifBlank { phones.firstOrNull() ?: "(No name)" }
        val distinctPhones = phones.distinct()
        val subtitle = when {
            distinctPhones.isEmpty() -> "Contact"
            distinctPhones.size == 1 -> distinctPhones[0]
            else -> "${distinctPhones[0]} \u00b7 +${distinctPhones.size - 1} more"
        }
        val json = JSONObject().put("v", 1).put("name", title).put("starred", starred).put("raw", rawArray).toString()
        return ContactSnapshot(title, subtitle, json)
    }

    private fun restoreContact(payload: String): Boolean {
        val root = JSONObject(payload)
        val raws = root.getJSONArray("raw")
        val starred = root.optInt("starred", 0) != 0
        val accounts = knownAccountKeys()
        val ops = ArrayList<ContentProviderOperation>()

        for (r in 0 until raws.length()) {
            val raw = raws.getJSONObject(r)
            val type = raw.optString("accountType", "").ifBlank { null }
            val accountName = raw.optString("accountName", "").ifBlank { null }
            // The original account is used only while it still exists; otherwise the contact comes back on the phone itself.
            val useAccount = type != null && accountName != null && accounts.contains("$type|$accountName")

            val backReference = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, if (useAccount) type else null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, if (useAccount) accountName else null)
                    .withValue(ContactsContract.RawContacts.STARRED, if (starred) 1 else 0)
                    .build()
            )

            val rows = raw.getJSONArray("rows")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
                    .withValue(ContactsContract.Data.MIMETYPE, row.getString("m"))
                row.optJSONObject("s")?.let { o ->
                    o.keys().forEach { key -> builder.withValue(key, o.getString(key)) }
                }
                row.optJSONObject("i")?.let { o ->
                    o.keys().forEach { key -> builder.withValue(key, o.getLong(key)) }
                }
                row.optJSONObject("b")?.let { o ->
                    o.keys().forEach { key -> builder.withValue(key, Base64.decode(o.getString(key), Base64.NO_WRAP)) }
                }
                ops.add(builder.build())
            }
        }
        if (ops.isEmpty()) return false
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return true
    }

    private fun knownAccountKeys(): Set<String> {
        val keys = HashSet<String>()
        try {
            AccountManager.get(context).accounts.forEach { keys.add("${it.type}|${it.name}") }
        } catch (e: Exception) {
            // account visibility can be restricted; the Settings table below still lists the synced ones
        }
        try {
            context.contentResolver.query(
                ContactsContract.Settings.CONTENT_URI,
                arrayOf(ContactsContract.Settings.ACCOUNT_TYPE, ContactsContract.Settings.ACCOUNT_NAME),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(0)
                    val n = c.getString(1)
                    if (!t.isNullOrBlank() && !n.isNullOrBlank()) keys.add("$t|$n")
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return keys
    }

    private suspend fun restoreCalls(payload: String): Int {
        val array = JSONArray(payload)
        val entries = ArrayList<SystemCallLogEntry>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val direction = try {
                CallDirection.valueOf(o.getString("d"))
            } catch (e: Exception) {
                CallDirection.OUTGOING
            }
            entries.add(
                SystemCallLogEntry(
                    phoneNumber = o.getString("n"),
                    displayName = o.optString("name", "").ifBlank { null },
                    direction = direction,
                    timestampMillis = o.getLong("t"),
                    durationSeconds = o.optInt("s", 0),
                    photoUri = o.optString("p", "").ifBlank { null }
                )
            )
        }
        systemCallLogRepository.restoreEntries(entries)
        callLogRepository.restoreEntries(entries)
        return entries.size
    }
}
