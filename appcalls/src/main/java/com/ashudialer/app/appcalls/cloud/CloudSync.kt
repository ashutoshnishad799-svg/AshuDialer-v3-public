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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.R
import com.ashudialer.app.appcalls.recording.RecMeta
import com.ashudialer.app.appcalls.recording.RecordingMetaStore
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Entry point of the optional cloud backup. Nothing here runs unless the person turned it on AND
 * picked a folder (see [CloudPrefs]); with the feature off every function is a quick no-op.
 *
 * Uploads are done by WorkManager ([CloudCopyWorker]) so they survive the app being closed, wait for
 * the right network (Wi-Fi only by default) and retry on their own with growing delays.
 */
object CloudSync {

    private const val TAG = "AppCalls:Cloud"
    private const val WORK_TAG = "ashu_cloud_copy"
    const val CHANNEL_ID = "ashu_cloud_sync"
    private const val NOTIFICATION_ID = 7420

    internal const val KEY_PATH = "path"
    internal const val KEY_URI = "uri"
    internal const val KEY_NAME = "name"
    internal const val KEY_DELETE_LOCAL = "delete_local"

    /** Called by the recorder the moment a recording has been saved. */
    fun onRecordingSaved(context: Context, file: File, uri: Uri?) {
        val prefs = CloudPrefs(context)
        if (!prefs.isActive || !prefs.autoBackup) return
        enqueue(context, file, uri)
    }

    /** Queues one recording for upload (skipped when it is already in the cloud folder). */
    fun enqueue(context: Context, file: File, uri: Uri?) {
        val app = context.applicationContext
        val prefs = CloudPrefs(app)
        if (!prefs.isActive) return
        val meta = RecordingMetaStore(app)
        if (meta.get(file.name).cloud == RecMeta.CLOUD_DONE) return
        meta.update(file.name) { it.copy(cloud = RecMeta.CLOUD_QUEUED) }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(prefs.chargingOnly)
            .build()
        val data = Data.Builder()
            .putString(KEY_PATH, file.absolutePath)
            .putString(KEY_URI, uri?.toString())
            .putString(KEY_NAME, file.name)
            .putBoolean(KEY_DELETE_LOCAL, prefs.cloudOnly)
            .build()
        val request = OneTimeWorkRequestBuilder<CloudCopyWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        // KEEP: tapping "Back up now" twice (or a retry sweep) never uploads the same file twice.
        WorkManager.getInstance(app).enqueueUniqueWork("cloud_${file.name}", ExistingWorkPolicy.KEEP, request)
        AppCallsLogger.i(TAG, "Queued for cloud backup: ${file.name}")
    }

    /** Queues every recording that is not in the cloud folder yet. Returns how many were queued. */
    fun backUpAll(context: Context, files: List<File>, uriFor: (File) -> Uri? = { null }): Int {
        val app = context.applicationContext
        if (!CloudPrefs(app).isActive) return 0
        val meta = RecordingMetaStore(app)
        var queued = 0
        for (f in files) {
            if (meta.get(f.name).cloud != RecMeta.CLOUD_DONE) {
                enqueue(app, f, uriFor(f))
                queued++
            }
        }
        return queued
    }

    /** Stops everything still waiting to be uploaded (files stay safe on the phone). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(WORK_TAG)
    }

    /** Remembers the folder the person picked in the system file picker. */
    fun savePickedFolder(context: Context, tree: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val prefs = CloudPrefs(context)
            prefs.folderUri = tree.toString()
            prefs.folderName = DocumentFile.fromTreeUri(context, tree)?.name.orEmpty()
            prefs.lastError = ""
            true
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Could not keep access to the picked folder: ${e.message}")
            false
        }
    }

    /** True when the picked folder still exists and can be written to. */
    fun isFolderUsable(context: Context): Boolean {
        val text = CloudPrefs(context).folderUri ?: return false
        return runCatching {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(text))
            doc != null && doc.exists() && doc.canWrite()
        }.getOrDefault(false)
    }

    /** Opens the backup folder in the app that owns it (Google Drive), for "Open in Drive". */
    fun folderViewIntent(context: Context): Intent? {
        val text = CloudPrefs(context).folderUri ?: return null
        val doc = DocumentFile.fromTreeUri(context, Uri.parse(text)) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(doc.uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    internal fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "amr" -> "audio/amr"
        else -> "application/octet-stream"
    }

    /** One quiet notification when a backup gave up. Never fires for the normal waiting-for-Wi-Fi case. */
    internal fun notifyProblem(context: Context, text: String) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording backup", NotificationManager.IMPORTANCE_LOW)
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_rec_mic)
                .setContentTitle("Recording backup needs attention")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        }
    }
}
