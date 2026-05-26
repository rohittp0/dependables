package com.rohittp.dependables.remotelogger.internal

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.rohittp.dependables.remotelogger.RemoteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Zips the recent log buckets and uploads the result to Firebase Storage.
 *
 * Enqueued via [enqueue] (called from [RemoteLogger.handleMessage] on receipt of a
 * `dump_logs` push). The destination path is built from [RemoteLogger.config].
 */
class LogDumpWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val hours = inputData.getInt(KEY_HOURS, RemoteLogger.config().defaultDumpHours)
            .coerceAtLeast(1)

        val deviceId = inputData.getString(KEY_DEVICE_ID)
            ?: runCatching { resolveAndroidId(applicationContext) }.getOrElse {
                Timber.e(it, "LogDumpWorker: failed to resolve device id")
                return@withContext Result.failure()
            }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val zipFile = File(applicationContext.cacheDir, "log_dump_$timestamp.zip")

        val storage = FirebaseStorage.getInstance()
        val metadata = StorageMetadata.Builder()
            .setContentType("application/zip")
            .build()

        try {
            val exported = zipFile.outputStream().use { out ->
                LogZipWriter.writeTo(applicationContext, out, logHours = hours)
            }

            val path = RemoteLogger.config().storagePathBuilder(deviceId, timestamp)
            val ref = storage.reference.child(path)
            zipFile.inputStream().use { input ->
                val task = suspendCancellableCoroutine { cont ->
                    ref.putStream(input, metadata).addOnCompleteListener { cont.resume(it) }
                }
                if (!task.isSuccessful) {
                    throw task.exception ?: IOException("Upload failed for $path")
                }
            }
            Timber.i("LogDumpWorker: uploaded zip with %d files to %s", exported, path)
            Result.success()
        } catch (e: IOException) {
            Timber.w(e, "LogDumpWorker: IO error, will retry")
            Result.retry()
        } catch (e: StorageException) {
            Timber.w(e, "LogDumpWorker: storage error code=%d", e.errorCode)
            if (e.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED) Result.retry()
            else Result.failure()
        } catch (e: Exception) {
            Timber.e(e, "LogDumpWorker: unexpected failure")
            Result.failure()
        } finally {
            zipFile.delete()
        }
    }

    @SuppressLint("HardwareIds")
    private fun resolveAndroidId(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return id?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("ANDROID_ID is unavailable")
    }

    companion object {
        const val KEY_HOURS: String = "hours"
        const val KEY_DEVICE_ID: String = "deviceId"
        private const val UNIQUE_PREFIX = "log_dump_"

        /**
         * Enqueue a one-time upload. If [deviceId] is null, the worker will resolve
         * `Settings.Secure.ANDROID_ID` at run time. `WorkManager` is initialised by the
         * consumer (auto-init by default).
         */
        @JvmStatic
        @JvmOverloads
        fun enqueue(context: Context, hours: Int, deviceId: String? = null) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = if (deviceId != null) {
                workDataOf(KEY_HOURS to hours, KEY_DEVICE_ID to deviceId)
            } else {
                workDataOf(KEY_HOURS to hours)
            }
            val req = OneTimeWorkRequestBuilder<LogDumpWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_PREFIX$hours",
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
