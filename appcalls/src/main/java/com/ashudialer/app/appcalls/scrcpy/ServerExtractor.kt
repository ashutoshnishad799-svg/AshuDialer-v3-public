// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls.scrcpy

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.WorkerThread
import com.ashudialer.app.appcalls.AppCallsLogger
import com.ashudialer.app.appcalls.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Extracts the bundled scrcpy-server jar from this module's assets to shared storage, where the
 * privileged shell process can read and execute it. SHA-256 verified both after extraction and
 * on every subsequent use (see ShellService.kt's own re-verification before exec) so a corrupted
 * or tampered file on shared storage can never be run with shell privileges.
 */
object ServerExtractor {

    private const val TAG = "AppCalls:ServerExtractor"

    /**
     * Why the last [prepareServerFile] / [ensureServerFile] attempt failed, in words a person can read. The recording
     * service puts it in the "Call not recorded" notification, so a failure says WHAT went wrong instead of just "try again".
     */
    @Volatile
    var lastError: String = ""
        private set

    /**
     * v2.3: makes sure a verified copy of the server jar exists and returns the path of the copy to use (null = could not).
     * The normal place (the app's own folder on shared storage) is tried first; if the jar cannot be put there - a leftover
     * file that cannot be replaced, a folder that cannot be made - the app's cache folder on shared storage is tried next.
     * The shell process can read both.
     */
    @WorkerThread
    fun prepareServerFile(context: Context): String? {
        lastError = ""
        val candidates = ArrayList<String>()
        try {
            candidates.add(ScrcpyConfig.getServerPath(context))
        } catch (e: Exception) {
            lastError = e.message ?: "shared storage is not available"
        }
        context.externalCacheDir?.let { candidates.add(it.absolutePath + "/appcalls-scrcpy-${ScrcpyConfig.SCRCPY_VERSION}-server.jar") }
        for (path in candidates) {
            if (ensureServerFile(context, path)) {
                lastError = ""
                return path
            }
        }
        if (lastError.isBlank()) lastError = "no folder to keep the helper file in"
        return null
    }

    @WorkerThread
    fun ensureServerFile(context: Context, serverPath: String): Boolean {
        val file = File(serverPath)
        if (file.exists() && verifyServerHash(file)) {
            AppCallsLogger.d(TAG, "Server file already present and verified at $serverPath")
            return true
        }
        AppCallsLogger.d(TAG, "Server file absent or hash mismatch, extracting from assets...")
        // A damaged, half-written or read-only file already sitting at the path used to make every later attempt fail the
        // same way. Remove it first so a fresh copy can be written.
        if (file.exists() && !file.delete()) {
            lastError = "an old helper file at ${file.name} cannot be replaced"
            AppCallsLogger.w(TAG, "Could not delete the stale server file at $serverPath")
        }
        return extractFromAssets(context, file)
    }

    private fun extractFromAssets(context: Context, destFile: File): Boolean {
        return try {
            val folder = destFile.parentFile ?: throw java.io.IOException("no folder for the helper file")
            if (!folder.exists() && !folder.mkdirs()) throw java.io.IOException("cannot create the folder ${folder.path}")

            // Written beside the real name first and moved into place only when complete and verified, so a
            // failed or interrupted write can never leave a half file behind under the real name.
            val temp = File(folder, destFile.name + ".tmp")
            if (temp.exists()) temp.delete()
            context.assets.open(BuildConfig.SCRCPY_SERVER_ASSET_NAME).use { inputStream ->
                writeFile(temp, inputStream)
            }
            if (!verifyServerHash(temp)) {
                temp.delete()
                lastError = "the helper file did not pass its checksum"
                AppCallsLogger.w(TAG, "Server extraction succeeded but hash verification FAILED")
                return false
            }
            if (destFile.exists()) destFile.delete()
            if (!temp.renameTo(destFile)) {
                // Some storage cannot rename in place: copy instead.
                temp.copyTo(destFile, overwrite = true)
                temp.delete()
            }
            destFile.setReadable(true, false)
            val verified = verifyServerHash(destFile)
            if (verified) {
                AppCallsLogger.d(TAG, "Server extracted and verified: ${destFile.path}")
            } else {
                lastError = "the helper file did not pass its checksum"
                AppCallsLogger.w(TAG, "Server moved into place but hash verification FAILED")
            }
            verified
        } catch (e: java.io.FileNotFoundException) {
            // assets.open() throws this with the asset's own name when the file is not inside the app at all.
            lastError = if (e.message?.contains(BuildConfig.SCRCPY_SERVER_ASSET_NAME) == true) {
                "the helper file is missing from this build of the app - install the newest build"
            } else {
                "cannot write ${destFile.name}: ${e.message ?: "no access"}"
            }
            AppCallsLogger.w(TAG, "Asset extraction failed: ${e.message}")
            false
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            AppCallsLogger.w(TAG, "Asset extraction failed: ${e.message}")
            false
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun writeFile(destFile: File, input: InputStream) {
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { output ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead = input.read(buffer)
            while (bytesRead > 0) {
                output.write(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        // World-readable so the shell process (UID 2000) can open it - it cannot read this app's
        // private data directory, but shared-storage files marked readable here are visible to it.
        destFile.setReadable(true, false)
    }

    fun verifyServerHash(file: File): Boolean {
        if (!file.exists()) {
            AppCallsLogger.e(TAG, "Cannot verify: file not found at ${file.path}")
            return false
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            val matches = actualHash.equals(ScrcpyConfig.EXPECTED_SERVER_SHA256, ignoreCase = true)
            if (!matches) {
                AppCallsLogger.w(TAG, "SHA-256 mismatch: expected=${ScrcpyConfig.EXPECTED_SERVER_SHA256} actual=$actualHash")
            }
            matches
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "Hash verification error: ${e.message}", e)
            false
        }
    }
}
