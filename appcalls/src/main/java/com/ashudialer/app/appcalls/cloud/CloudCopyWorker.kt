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
package com.ashudialer.app.appcalls.cloud

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.appcalls.recording.RecordingLibrary
import com.ashudialer.app.appcalls.recording.RecordingMetaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Copies ONE finished recording into the folder the person picked (normally in Google Drive).
 *
 * Lessons built in (the same ones CallVault's own copy worker learned the hard way):
 *  - the copy is idempotent: a recording that is already in the folder is never uploaded again, and a
 *    half-written twin left by an interrupted attempt is removed first;
 *  - retries are limited - after [MAX_ATTEMPTS] the worker gives up, says so, and keeps the local file;
 *  - the local copy is deleted ONLY after the cloud copy is complete (and only in "Cloud only" mode),
 *    so a recording can never end up in neither place.
 */
class CloudCopyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = CloudPrefs(ctx)
        val meta = RecordingMetaStore(ctx)

        val path = inputData.getString(CloudSync.KEY_PATH) ?: return@withContext Result.failure()
        val uriText = inputData.getString(CloudSync.KEY_URI)
        val name = inputData.getString(CloudSync.KEY_NAME) ?: File(path).name
        val deleteLocal = inputData.getBoolean(CloudSync.KEY_DELETE_LOCAL, false)

        if (!prefs.enabled) {
            // Switched off while this was waiting: nothing to do, nothing was uploaded.
            meta.update(name) { if (it.cloud == RecMeta.CLOUD_QUEUED) it.copy(cloud = RecMeta.CLOUD_NONE) else it }
            return@withContext Result.success()
        }
        if (meta.get(name).cloud == RecMeta.CLOUD_DONE) return@withContext Result.success()

        val folder = prefs.folderUri?.let { runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(it)) }.getOrNull() }
        if (folder == null || !folder.exists() || !folder.canWrite()) {
            return@withContext retryOrGiveUp(name, "the backup folder is not reachable any more")
        }

        val size = sourceSize(ctx, path, uriText)
        if (size <= 0L) {
            // Empty or gone: uploading it would put an unplayable file in the cloud. This is final.
            AppCallsLogger.w(TAG, "Not backing up $name: the source is empty or missing")
            meta.update(name) { it.copy(cloud = RecMeta.CLOUD_NONE) }
            return@withContext Result.failure()
        }

        val existing = runCatching { folder.findFile(name) }.getOrNull()
        if (existing != null) {
            if (existing.length() == size) {
                return@withContext finish(ctx, prefs, meta, name, existing.uri, path, deleteLocal)
            }
            runCatching { existing.delete() } // an incomplete leftover from an interrupted attempt
        }

        val target = runCatching { folder.createFile(CloudSync.mimeFor(name), name) }.getOrNull()
            ?: return@withContext retryOrGiveUp(name, "could not create the file in the backup folder")

        var copied = 0L
        try {
            val input = openSource(ctx, path, uriText) ?: throw IOException("cannot read the recording")
            input.use { src ->
                val out = ctx.contentResolver.openOutputStream(target.uri, "w")
                    ?: throw IOException("cannot write to the backup folder")
                out.use { dst ->
                    copied = src.copyTo(dst, 64 * 1024)
                    dst.flush()
                }
            }
        } catch (e: Exception) {
            runCatching { target.delete() }
            return@withContext retryOrGiveUp(name, e.message ?: "the copy was interrupted")
        }

        if (copied != size) {
            runCatching { target.delete() }
            return@withContext retryOrGiveUp(name, "the copy came out incomplete")
        }
        finish(ctx, prefs, meta, name, target.uri, path, deleteLocal)
    }

    private fun finish(
        ctx: Context,
        prefs: CloudPrefs,
        meta: RecordingMetaStore,
        name: String,
        cloudUri: Uri,
        path: String,
        deleteLocal: Boolean
    ): Result {
        val now = System.currentTimeMillis()
        meta.update(name) { it.copy(cloud = RecMeta.CLOUD_DONE, cloudAt = now, cloudUri = cloudUri.toString()) }
        prefs.lastSuccessAt = now
        prefs.lastError = ""
        AppCallsLogger.i(TAG, "Backed up $name (deleteLocal=$deleteLocal)")
        if (deleteLocal) {
            // keepMeta: the star/notes/cloud record must outlive the local file.
            RecordingLibrary.delete(ctx, File(path), keepMeta = true)
        }
        return Result.success()
    }

    private fun retryOrGiveUp(name: String, reason: String): Result {
        val ctx = applicationContext
        CloudPrefs(ctx).lastError = reason
        if (runAttemptCount < MAX_ATTEMPTS) {
            AppCallsLogger.w(TAG, "Backup of $name failed ($reason) - will retry, attempt ${runAttemptCount + 1}")
            return Result.retry()
        }
        RecordingMetaStore(ctx).update(name) { it.copy(cloud = RecMeta.CLOUD_FAILED) }
        CloudSync.notifyProblem(ctx, "Couldn't back up \"$name\": $reason. The recording is still safe on your phone.")
        return Result.failure()
    }

    private fun openSource(ctx: Context, path: String, uriText: String?): InputStream? {
        if (!uriText.isNullOrBlank()) {
            runCatching { ctx.contentResolver.openInputStream(Uri.parse(uriText)) }.getOrNull()?.let { return it }
        }
        val f = File(path)
        return if (f.exists() && f.length() > 0L) FileInputStream(f) else null
    }

    private fun sourceSize(ctx: Context, path: String, uriText: String?): Long {
        val f = File(path)
        if (f.exists() && f.length() > 0L) return f.length()
        if (!uriText.isNullOrBlank()) {
            val size = runCatching {
                ctx.contentResolver.openAssetFileDescriptor(Uri.parse(uriText), "r")?.use { it.length }
            }.getOrNull()
            if (size != null && size > 0L) return size
        }
        return 0L
    }

    companion object {
        private const val TAG = "AppCalls:CloudCopy"
        private const val MAX_ATTEMPTS = 5
    }
}
