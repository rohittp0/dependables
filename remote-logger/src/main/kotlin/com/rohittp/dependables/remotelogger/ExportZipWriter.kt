package com.rohittp.dependables.remotelogger

import android.content.Context
import com.rohittp.dependables.remotelogger.internal.MetaJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the app-data zip that gets uploaded on a `dump_logs` push, and is also useful for
 * any in-app "export my data" UI.
 *
 * Layout:
 *
 *   meta.json
 *   databases/<file>            — every regular file under `applicationInfo.dataDir/databases/`
 *   shared_prefs/<file>         — every regular file under `applicationInfo.dataDir/shared_prefs/`
 *   datastore/<file>            — every regular file under `filesDir/datastore/`
 *   logs/<yyyy-MM-dd>/<file>    — every regular file under `filesDir/logs/<day>/` for day
 *                                 buckets whose last instant is at or after `now - logHours`
 *
 * Use [writeTo] to stream into any [OutputStream] — `FileOutputStream`, `MediaStore`'s
 * download URI, an in-memory `ByteArrayOutputStream`, etc. [LogDumpWorker] uses it to
 * stage a zip in the cache directory before uploading to Firebase Storage.
 */
object ExportZipWriter {

    /**
     * Stream the export zip to [outputStream] and return the number of files included
     * (not counting `meta.json`). Closes the underlying stream when done.
     */
    @JvmStatic
    @JvmOverloads
    suspend fun writeTo(
        context: Context,
        outputStream: OutputStream,
        logHours: Int = DEFAULT_LOG_HOURS,
    ): Int = withContext(Dispatchers.IO) {
        val files = collectExportFiles(context, logHours.coerceAtLeast(1))
        var exportedCount = 0
        runCatching {
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                runCatching {
                    zip.putNextEntry(ZipEntry("meta.json"))
                    zip.write(MetaJson.build(context).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                for (exportFile in files) {
                    runCatching {
                        zip.putNextEntry(ZipEntry(exportFile.zipPath))
                        exportFile.file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                        exportedCount += 1
                    }
                }
            }
        }
        exportedCount
    }

    /** Default window of log day-buckets to include. Matches travel-animator's default. */
    const val DEFAULT_LOG_HOURS: Int = 72

    private data class ExportFile(val file: File, val zipPath: String)

    private fun collectExportFiles(context: Context, logHours: Int): List<ExportFile> {
        val exportFiles = ArrayList<ExportFile>()

        val dataDir = File(context.applicationInfo.dataDir)
        addFilesFromDir(exportFiles, File(dataDir, "databases"), "databases")
        addFilesFromDir(exportFiles, File(dataDir, "shared_prefs"), "shared_prefs")
        addFilesFromDir(exportFiles, File(context.filesDir, "datastore"), "datastore")
        addRecentLogs(exportFiles, File(context.filesDir, "logs"), logHours)

        return exportFiles
    }

    private fun addFilesFromDir(
        exportFiles: MutableList<ExportFile>,
        dir: File,
        zipPrefix: String,
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.filter { it.isFile }?.forEach { file ->
            exportFiles.add(ExportFile(file, "$zipPrefix/${file.name}"))
        }
    }

    private fun addRecentLogs(
        exportFiles: MutableList<ExportFile>,
        logsDir: File,
        hours: Int,
    ) {
        if (!logsDir.exists() || !logsDir.isDirectory) return
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours.toLong())
        logsDir.listFiles()?.forEach { dayDir ->
            if (!dayDir.isDirectory) return@forEach
            val parsed = runCatching { dayFmt.parse(dayDir.name) }.getOrNull() ?: return@forEach
            // Day bucket spans a full UTC day; keep it if any part of it is after the cutoff.
            if (parsed.time + TimeUnit.DAYS.toMillis(1) < cutoff) return@forEach
            dayDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                exportFiles.add(ExportFile(file, "logs/${dayDir.name}/${file.name}"))
            }
        }
    }
}
