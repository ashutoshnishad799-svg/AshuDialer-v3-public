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

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * Copies and moves contacts between the places a phone can keep them - the phone itself, a Google account, a SIM card -
 * and to / from .vcf files (v2.3). Needs no root: the phone's own Contacts provider and SIM provider are used, with the
 * Contacts permission the dialer already holds.
 *
 * How each pair works
 *  - phone <-> Google: the contact is copied row by row (every field, photo thumbnail too), so nothing is lost.
 *  - SIM -> phone / Google: name, number and e-mail are written as a normal contact.
 *  - phone / Google -> SIM: a SIM entry is only a name and one number, so a contact with two numbers becomes two entries and
 *    long names are shortened to what the SIM can hold. (Move is therefore not offered into a SIM.)
 *  - SIM -> SIM (one SIM to the other): works like any other pair.
 *  - .vcf file -> anywhere above, and any place -> .vcf file.
 * A contact that is already in the destination (same name and number) is skipped, not duplicated.
 */
class ContactTransferRepository(private val context: Context) {

    enum class Kind { DEVICE, GOOGLE, SIM, FILE }

    /** One place contacts can live. */
    data class Place(
        val id: String,
        val kind: Kind,
        val title: String,
        val subtitle: String,
        val accountType: String? = null,
        val accountName: String? = null,
        val simSubscriptionId: Int = -1,
        val simSlot: Int = 0
    )

    /** A contact in a form every place understands. */
    data class TransferContact(
        val key: String,
        val name: String,
        val numbers: List<String>,
        val emails: List<String> = emptyList(),
        val organization: String = "",
        /** Raw contact id when it was read from the phone / a Google account (needed to copy every field or to move it). */
        val sourceRawId: Long = -1L,
        /** The exact name stored on the SIM (needed to delete it from there on a move). */
        val simTag: String? = null
    )

    class ReadResult(val contacts: List<TransferContact>, val error: String? = null)

    class Summary(
        val copied: Int,
        val alreadyThere: Int,
        val failed: Int,
        val removedFromSource: Int,
        val notes: List<String>
    )

    companion object {
        private const val GOOGLE_TYPE = "com.google"
        private const val BATCH_OPS = 300
        private val LOCAL_TYPES = listOf("com.android.localphone", "vnd.sec.contact.phone", "com.oppo.contacts.device")
        private val DATA_PROJECTION: Array<String> =
            Array(16) { index -> if (index == 0) ContactsContract.Data.MIMETYPE else "data$index" }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Places
    // ---------------------------------------------------------------------------------------------------------------

    suspend fun listPlaces(): List<Place> = withContext(Dispatchers.IO) {
        val places = ArrayList<Place>()
        places.add(Place(id = "device", kind = Kind.DEVICE, title = "Phone", subtitle = "Saved only on this phone"))
        for (name in googleAccountNames()) {
            places.add(
                Place(
                    id = "google:$name", kind = Kind.GOOGLE, title = "Google", subtitle = name,
                    accountType = GOOGLE_TYPE, accountName = name
                )
            )
        }
        places.addAll(simPlaces())
        places
    }

    private fun googleAccountNames(): List<String> {
        val names = LinkedHashSet<String>()
        try {
            AccountManager.get(context).getAccountsByType(GOOGLE_TYPE).forEach { names.add(it.name) }
        } catch (e: Exception) {
            // account visibility can be restricted - the contact tables below still show the synced ones
        }
        try {
            context.contentResolver.query(
                ContactsContract.Settings.CONTENT_URI,
                arrayOf(ContactsContract.Settings.ACCOUNT_NAME),
                "${ContactsContract.Settings.ACCOUNT_TYPE} = ?",
                arrayOf(GOOGLE_TYPE),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) names.add(n)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        try {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
                arrayOf(GOOGLE_TYPE),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) names.add(n)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return names.toList()
    }

    private fun simPlaces(): List<Place> {
        val out = ArrayList<Place>()
        try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            val subscriptions = (manager?.activeSubscriptionInfoList).orEmpty()
            for (info in subscriptions.sortedBy { it.simSlotIndex }) {
                val slot = info.simSlotIndex + 1
                val carrier = info.carrierName?.toString().orEmpty()
                out.add(
                    Place(
                        id = "sim:${info.subscriptionId}", kind = Kind.SIM,
                        title = "SIM $slot", subtitle = carrier.ifBlank { "SIM card" },
                        simSubscriptionId = info.subscriptionId, simSlot = slot
                    )
                )
            }
        } catch (e: Exception) {
            // no permission to list SIMs, or none - handled below
        }
        // A phone that hides its SIM list still has one SIM contact store; reading it reports any problem clearly.
        if (out.isEmpty()) {
            out.add(Place(id = "sim:-1", kind = Kind.SIM, title = "SIM", subtitle = "SIM card", simSubscriptionId = -1, simSlot = 1))
        }
        return out
    }

    private fun simUri(place: Place): Uri =
        if (place.simSubscriptionId >= 0) Uri.parse("content://icc/adn/subId/${place.simSubscriptionId}")
        else Uri.parse("content://icc/adn")

    // ---------------------------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------------------------

    suspend fun readContacts(place: Place): ReadResult = withContext(Dispatchers.IO) {
        try {
            when (place.kind) {
                Kind.SIM -> {
                    val list = readSim(place)
                    if (list.isEmpty()) ReadResult(list, "No contacts found on this SIM (or this phone does not let apps read the SIM).")
                    else ReadResult(list)
                }
                Kind.DEVICE, Kind.GOOGLE -> ReadResult(readProvider(place))
                Kind.FILE -> ReadResult(emptyList())
            }
        } catch (e: SecurityException) {
            ReadResult(emptyList(), "Contacts permission is needed. Allow it in Settings and try again.")
        } catch (e: Exception) {
            ReadResult(emptyList(), "Couldn't read these contacts (${e.message ?: "unknown error"}).")
        }
    }

    private fun readSim(place: Place): List<TransferContact> {
        val out = ArrayList<TransferContact>()
        val cursor = context.contentResolver.query(simUri(place), null, null, null, null) ?: return out
        cursor.use { c ->
            var nameColumn = c.getColumnIndex("name")
            if (nameColumn < 0) nameColumn = c.getColumnIndex("tag")
            val numberColumn = c.getColumnIndex("number")
            val emailColumn = c.getColumnIndex("emails")
            if (nameColumn < 0 || numberColumn < 0) return out
            var position = 0
            while (c.moveToNext()) {
                val name = c.getString(nameColumn).orEmpty().trim()
                val number = c.getString(numberColumn).orEmpty().trim()
                if (name.isEmpty() && number.isEmpty()) continue
                val emails = if (emailColumn >= 0) {
                    c.getString(emailColumn).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                out.add(
                    TransferContact(
                        key = "sim:${position++}",
                        name = name.ifBlank { number },
                        numbers = if (number.isEmpty()) emptyList() else listOf(number),
                        emails = emails,
                        simTag = name
                    )
                )
            }
        }
        return out
    }

    private fun providerWhere(place: Place): Pair<String, Array<String>?> {
        val notDeleted = "${ContactsContract.RawContacts.DELETED} = 0"
        return if (place.kind == Kind.GOOGLE) {
            Pair(
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND $notDeleted",
                arrayOf(place.accountType ?: GOOGLE_TYPE, place.accountName.orEmpty())
            )
        } else {
            val types = LOCAL_TYPES.joinToString(",") { "'$it'" }
            Pair(
                "(${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL OR ${ContactsContract.RawContacts.ACCOUNT_TYPE} IN ($types)) AND $notDeleted",
                null
            )
        }
    }

    private fun readProvider(place: Place): List<TransferContact> {
        val resolver = context.contentResolver
        val (where, args) = providerWhere(place)

        val names = LinkedHashMap<Long, String>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY),
            where, args, null
        )?.use { c ->
            while (c.moveToNext()) names[c.getLong(0)] = c.getString(1).orEmpty()
        }
        if (names.isEmpty()) return emptyList()

        val phones = HashMap<Long, MutableList<String>>()
        val emails = HashMap<Long, MutableList<String>>()
        val companies = HashMap<Long, String>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.RAW_CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
            "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)",
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
            ),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val rawId = c.getLong(0)
                if (!names.containsKey(rawId)) continue
                val value = c.getString(2)?.trim().orEmpty()
                if (value.isEmpty()) continue
                when (c.getString(1)) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phones.getOrPut(rawId) { ArrayList() }.add(value)
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails.getOrPut(rawId) { ArrayList() }.add(value)
                    else -> companies[rawId] = value
                }
            }
        }

        return names.map { (rawId, rawName) ->
            val numbers = phones[rawId].orEmpty().distinct()
            TransferContact(
                key = "raw:$rawId",
                name = rawName.ifBlank { numbers.firstOrNull() ?: "(No name)" },
                numbers = numbers,
                emails = emails[rawId].orEmpty().distinct(),
                organization = companies[rawId].orEmpty(),
                sourceRawId = rawId
            )
        }.sortedBy { it.name.lowercase() }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Copy / move
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Copies [contacts] (read from [from]) into [to]. With [move] the copies that arrived are removed from [from] again.
     * [onProgress] gets (done, total) as it goes.
     */
    suspend fun transfer(
        contacts: List<TransferContact>,
        from: Place,
        to: Place,
        move: Boolean,
        onProgress: (Int, Int) -> Unit
    ): Summary = withContext(Dispatchers.IO) {
        val notes = ArrayList<String>()
        var copied = 0
        var already = 0
        var failed = 0
        var removed = 0
        val total = contacts.size
        var done = 0

        if (to.kind == Kind.FILE || to.id == from.id) {
            return@withContext Summary(0, 0, 0, 0, listOf("Pick a different place to copy to."))
        }

        val existing = try {
            existingKeys(to)
        } catch (e: Exception) {
            HashSet<String>()
        }

        val provider = to.kind == Kind.DEVICE || to.kind == Kind.GOOGLE
        val fullCopy = provider && (from.kind == Kind.DEVICE || from.kind == Kind.GOOGLE)
        val pendingProvider = ArrayList<TransferContact>()
        val toRemove = ArrayList<TransferContact>()
        var shortenedNames = false

        fun flushProvider() {
            if (pendingProvider.isEmpty()) return
            val ok = insertIntoProvider(to, pendingProvider.toList(), fullCopy)
            copied += ok.first
            failed += ok.second
            pendingProvider.clear()
        }

        for (contact in contacts) {
            try {
                if (existing.contains(contactKey(contact))) {
                    already++
                    if (move) toRemove.add(contact)
                } else if (to.kind == Kind.SIM) {
                    if (contact.numbers.isEmpty()) {
                        failed++
                    } else {
                        val result = insertIntoSim(to, contact)
                        if (result.written > 0) {
                            copied++
                            existing.add(contactKey(contact))
                            if (result.shortened) shortenedNames = true
                            if (move) toRemove.add(contact)
                        } else {
                            failed++
                        }
                    }
                } else {
                    pendingProvider.add(contact)
                    existing.add(contactKey(contact))
                    if (move) toRemove.add(contact)
                    if (pendingProvider.size >= 40) flushProvider()
                }
            } catch (e: Exception) {
                failed++
            }
            done++
            onProgress(done, total)
        }
        try {
            flushProvider()
        } catch (e: Exception) {
            failed += pendingProvider.size
        }

        if (move && toRemove.isNotEmpty() && failed == 0) {
            removed = removeFromSource(from, toRemove)
        } else if (move && failed > 0) {
            notes.add("Some contacts could not be copied, so nothing was removed from the source.")
        }
        if (shortenedNames) notes.add("Long names were shortened to fit the SIM.")
        Summary(copied, already, failed, removed, notes)
    }

    /** Writes contacts that came from a .vcf file into [to]. */
    suspend fun importInto(
        contacts: List<TransferContact>,
        to: Place,
        onProgress: (Int, Int) -> Unit
    ): Summary = transfer(
        contacts = contacts,
        from = Place(id = "file", kind = Kind.FILE, title = "File", subtitle = ""),
        to = to,
        move = false,
        onProgress = onProgress
    )

    private fun contactKey(c: TransferContact): String {
        val name = c.name.trim().lowercase()
        val number = c.numbers.firstOrNull()?.let { numberKey(it) }.orEmpty()
        return "$name|$number"
    }

    private fun numberKey(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /** What is already in [place], as "name|number" keys, so a contact that is there is not copied a second time. */
    private fun existingKeys(place: Place): MutableSet<String> {
        val keys = HashSet<String>()
        val current = when (place.kind) {
            Kind.SIM -> readSim(place)
            Kind.DEVICE, Kind.GOOGLE -> readProvider(place)
            Kind.FILE -> emptyList()
        }
        current.forEach { keys.add(contactKey(it)) }
        return keys
    }

    // ---- writing into the phone / a Google account --------------------------------------------------------------

    /** Returns (written, failed). Falls back to one contact at a time when a whole batch is refused. */
    private fun insertIntoProvider(to: Place, contacts: List<TransferContact>, fullCopy: Boolean): Pair<Int, Int> {
        val accountType = if (to.kind == Kind.GOOGLE) to.accountType ?: GOOGLE_TYPE else null
        val accountName = if (to.kind == Kind.GOOGLE) to.accountName else null
        var written = 0
        var failed = 0

        val ops = ArrayList<ContentProviderOperation>()
        var inBatch = 0
        fun flushBatch(): Boolean = try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }

        val retry = ArrayList<TransferContact>()
        for (contact in contacts) {
            val base = ops.size
            ops.addAll(buildInsertOps(contact, accountType, accountName, base, fullCopy))
            inBatch++
            retry.add(contact)
            if (ops.size >= BATCH_OPS) {
                if (flushBatch()) {
                    written += inBatch
                } else {
                    val single = insertOneByOne(retry, accountType, accountName, fullCopy)
                    written += single.first
                    failed += single.second
                }
                ops.clear()
                inBatch = 0
                retry.clear()
            }
        }
        if (ops.isNotEmpty()) {
            if (flushBatch()) {
                written += inBatch
            } else {
                val single = insertOneByOne(retry, accountType, accountName, fullCopy)
                written += single.first
                failed += single.second
            }
        }
        return Pair(written, failed)
    }

    private fun insertOneByOne(
        contacts: List<TransferContact>,
        accountType: String?,
        accountName: String?,
        fullCopy: Boolean
    ): Pair<Int, Int> {
        var written = 0
        var failed = 0
        for (contact in contacts) {
            try {
                context.contentResolver.applyBatch(
                    ContactsContract.AUTHORITY,
                    ArrayList(buildInsertOps(contact, accountType, accountName, 0, fullCopy))
                )
                written++
            } catch (e: Exception) {
                failed++
            }
        }
        return Pair(written, failed)
    }

    private fun buildInsertOps(
        contact: TransferContact,
        accountType: String?,
        accountName: String?,
        base: Int,
        fullCopy: Boolean
    ): List<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                .build()
        )
        if (fullCopy && contact.sourceRawId >= 0) {
            val copied = copyRows(contact.sourceRawId, base)
            if (copied.isNotEmpty()) {
                ops.addAll(copied)
                return ops
            }
        }
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, base)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                .build()
        )
        contact.numbers.forEach { number ->
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, base)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
        }
        contact.emails.forEach { email ->
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, base)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                    .build()
            )
        }
        if (contact.organization.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, base)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, contact.organization)
                    .build()
            )
        }
        return ops
    }

    /** Every data row of a raw contact, ready to insert under the new raw contact at [base] (group links are skipped). */
    private fun copyRows(rawId: Long, base: Int): List<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, DATA_PROJECTION,
            "${ContactsContract.Data.RAW_CONTACT_ID} = ?", arrayOf(rawId.toString()), null
        )?.use { data ->
            while (data.moveToNext()) {
                val mime = data.getString(0) ?: continue
                if (mime == ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE) continue
                val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, base)
                    .withValue(ContactsContract.Data.MIMETYPE, mime)
                for (i in 1..15) {
                    // data14 of a photo row is an id the provider hands out itself - never copy it
                    if (i == 14 && mime == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE) continue
                    val key = "data$i"
                    when (data.getType(i)) {
                        Cursor.FIELD_TYPE_STRING -> builder.withValue(key, data.getString(i))
                        Cursor.FIELD_TYPE_INTEGER -> builder.withValue(key, data.getLong(i))
                        Cursor.FIELD_TYPE_BLOB -> data.getBlob(i)?.let { builder.withValue(key, it) }
                        else -> Unit
                    }
                }
                ops.add(builder.build())
            }
        }
        return ops
    }

    // ---- writing into a SIM -------------------------------------------------------------------------------------

    private class SimWrite(val written: Int, val shortened: Boolean)

    private fun insertIntoSim(to: Place, contact: TransferContact): SimWrite {
        // A SIM keeps a short name: about 14 letters, fewer for non-Latin scripts.
        val limit = if (contact.name.all { it.code < 128 }) 14 else 6
        val tag = contact.name.trim().take(limit)
        val shortened = tag.length < contact.name.trim().length
        var written = 0
        for (number in contact.numbers) {
            val clean = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.take(20)
            if (clean.isEmpty()) continue
            val values = ContentValues().apply {
                put("tag", tag)
                put("number", clean)
            }
            if (context.contentResolver.insert(simUri(to), values) != null) written++
        }
        return SimWrite(written, shortened)
    }

    // ---- removing from the source (move) ------------------------------------------------------------------------

    private fun removeFromSource(from: Place, contacts: List<TransferContact>): Int {
        var removed = 0
        val resolver = context.contentResolver
        for (contact in contacts) {
            try {
                if (from.kind == Kind.SIM) {
                    val tag = contact.simTag ?: continue
                    if (tag.contains('\'')) continue
                    var all = true
                    for (number in contact.numbers) {
                        val rows = resolver.delete(simUri(from), "tag='$tag' AND number='$number'", null)
                        if (rows <= 0) all = false
                    }
                    if (all) removed++
                } else if (contact.sourceRawId >= 0) {
                    val rows = resolver.delete(
                        ContactsContract.RawContacts.CONTENT_URI,
                        "${ContactsContract.RawContacts._ID} = ?",
                        arrayOf(contact.sourceRawId.toString())
                    )
                    if (rows > 0) removed++
                }
            } catch (e: Exception) {
                // this one stays where it is
            }
        }
        return removed
    }

    // ---------------------------------------------------------------------------------------------------------------
    // .vcf files
    // ---------------------------------------------------------------------------------------------------------------

    suspend fun readVcf(uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext ReadResult(emptyList(), "Couldn't open that file.")
            val list = parseVcf(bytes.toString(Charsets.UTF_8))
            if (list.isEmpty()) ReadResult(list, "No contacts were found in that file.") else ReadResult(list)
        } catch (e: Exception) {
            ReadResult(emptyList(), "Couldn't read that file (${e.message ?: "unknown error"}).")
        }
    }

    suspend fun writeVcf(contacts: List<TransferContact>, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out -> writeVcf(contacts, out) } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeVcf(contacts: List<TransferContact>, out: OutputStream) {
        val text = buildString {
            for (c in contacts) {
                appendLine("BEGIN:VCARD")
                appendLine("VERSION:3.0")
                appendLine("FN:${vcfEscape(c.name)}")
                appendLine("N:${vcfEscape(c.name)};;;;")
                c.numbers.forEach { appendLine("TEL;TYPE=CELL:${vcfEscape(it)}") }
                c.emails.forEach { appendLine("EMAIL;TYPE=HOME:${vcfEscape(it)}") }
                if (c.organization.isNotBlank()) appendLine("ORG:${vcfEscape(c.organization)}")
                appendLine("END:VCARD")
            }
        }
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun vcfEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r", "")
        .replace("\n", "\\n")

    private fun vcfUnescape(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    /** vCard 2.1 and 3.0 / 4.0: names, numbers, e-mails and company. Handles folded lines and quoted-printable text. */
    private fun parseVcf(text: String): List<TransferContact> {
        // 1) join folded lines (a line that starts with a space or tab continues the one before) and quoted-printable breaks
        val lines = ArrayList<String>()
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < raw.size) {
            var line = raw[i]
            i++
            while (i < raw.size && (raw[i].startsWith(" ") || raw[i].startsWith("\t"))) {
                line += raw[i].substring(1)
                i++
            }
            while (line.endsWith("=") && line.uppercase().contains("QUOTED-PRINTABLE") && i < raw.size) {
                line = line.dropLast(1) + raw[i]
                i++
            }
            lines.add(line)
        }

        // 2) read the cards
        val out = ArrayList<TransferContact>()
        var inCard = false
        var fn = ""
        var structuredName = ""
        var numbers = ArrayList<String>()
        var emails = ArrayList<String>()
        var org = ""
        var counter = 0

        for (line in lines) {
            val upper = line.trim().uppercase()
            if (upper == "BEGIN:VCARD") {
                inCard = true
                fn = ""
                structuredName = ""
                numbers = ArrayList()
                emails = ArrayList()
                org = ""
                continue
            }
            if (upper == "END:VCARD") {
                if (inCard) {
                    val name = fn.ifBlank { structuredName }.ifBlank { numbers.firstOrNull().orEmpty() }
                    if (name.isNotBlank() || numbers.isNotEmpty()) {
                        out.add(
                            TransferContact(
                                key = "vcf:${counter++}", name = name.trim(),
                                numbers = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                                emails = emails.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                                organization = org.trim()
                            )
                        )
                    }
                }
                inCard = false
                continue
            }
            if (!inCard) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon)
            var value = line.substring(colon + 1)
            val headParts = head.split(';')
            var property = headParts[0].uppercase()
            if (property.contains('.')) property = property.substringAfterLast('.')
            val params = headParts.drop(1).map { it.uppercase() }
            if (params.any { it.contains("QUOTED-PRINTABLE") }) {
                val charsetName = params.firstOrNull { it.startsWith("CHARSET=") }?.substringAfter('=')
                val charset = try {
                    if (charsetName != null) Charset.forName(charsetName) else Charsets.UTF_8
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
                value = decodeQuotedPrintable(value, charset)
            }
            when (property) {
                "FN" -> fn = vcfUnescape(value)
                "N" -> {
                    val p = value.split(';').map { vcfUnescape(it) }
                    val family = p.getOrNull(0).orEmpty()
                    val given = p.getOrNull(1).orEmpty()
                    val middle = p.getOrNull(2).orEmpty()
                    structuredName = listOf(given, middle, family).filter { it.isNotBlank() }.joinToString(" ")
                }
                "TEL" -> numbers.add(vcfUnescape(value))
                "EMAIL" -> emails.add(vcfUnescape(value))
                "ORG" -> org = value.split(';').map { vcfUnescape(it) }.filter { it.isNotBlank() }.joinToString(" ")
            }
        }
        return out
    }

    private fun decodeQuotedPrintable(value: String, charset: Charset): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '=' && i + 2 < value.length) {
                val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte != null) {
                    out.write(byte)
                    i += 3
                    continue
                }
            }
            out.write(ch.toString().toByteArray(charset))
            i++
        }
        return String(out.toByteArray(), charset)
    }
}
