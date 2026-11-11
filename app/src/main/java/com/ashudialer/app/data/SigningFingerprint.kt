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
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * The fingerprints (SHA-1 / SHA-256) of the key THIS installed copy of the app is signed with.
 *
 * Google sign-in "error 10" (DEVELOPER_ERROR) means exactly one thing: the fingerprint of the signing key is not registered
 * for this app in Firebase / Google Cloud. Reading it from the installed app itself (Account screen) gives the right value
 * for whichever build is running - debug, CI release or a local build - with no keytool and no guessing. A fingerprint is
 * public information (it identifies a key, it cannot sign anything).
 */
object SigningFingerprint {
    data class Fingerprints(val sha1: String, val sha256: String)

    @Suppress("DEPRECATION")
    fun read(context: Context): Fingerprints? {
        return try {
            val pm = context.packageManager
            val signer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            }
            val certificate = signer ?: return null
            val bytes = certificate.toByteArray()
            Fingerprints(sha1 = hex("SHA-1", bytes), sha256 = hex("SHA-256", bytes))
        } catch (e: Exception) {
            null
        }
    }

    private fun hex(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString(":") { "%02X".format(it) }
}
