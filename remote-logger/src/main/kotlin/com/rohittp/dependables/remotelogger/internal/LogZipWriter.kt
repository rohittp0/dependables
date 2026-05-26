package com.rohittp.dependables.remotelogger.internal

import android.content.Context
import com.rohittp.dependables.remotelogger.FileLoggingTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Walks `filesDir/logs/<yyyy-MM-dd>/<HH>-<tag>.ndjson` files written by [FileLoggingTree],
 * keeping only entries whose `(day, hour)` boundary is within the last [logHours] hours
 * relative to `now`, and zips them into [outputStream] alongside a `meta.json`.
 *
 * Returns the number of log files included (not counting `meta.json`).
 */
internal object LogZipWriter {

    suspend fun writeTo(
        context: Context,
        outputStream: OutputStream,
        logHours: Int,
        now: Date = Date(),
    ): Int = withContext(Dispatchers.IO) {
        val cutoff = now.time - TimeUnit.HOURS.toMillis(logHours.toLong())
        val root = FileLoggingTree.logsRoot(context)
        val files = collectLogFiles(root, cutoff)

        var written = 0
        runCatching {
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                runCatching {
                    zip.putNextEntry(ZipEntry("meta.json"))
                    zip.write(MetaJson.build(context).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                for ((zipPath, file) in files) {
                    runCatching {
                        zip.putNextEntry(ZipEntry(zipPath))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        written += 1
                    }
                }
            }
        }
        written
    }

    /**
     * Returns `(zipPath, file)` pairs for every `<day>/<hour>-<tag>.ndjson` whose hour-boundary
     * is at or after [cutoffMillis]. Bucket granularity is one hour — slightly more permissive
     * than a strict file-modification check, which is fine: a request for "last 1 hour" will
     * include the current and previous hour bucket.
     */
    private fun collectLogFiles(
        root: java.io.File,
        cutoffMillis: Long,
    ): List<Pair<String, java.io.File>> {
        if (!root.isDirectory) return emptyList()

        val dayFmt = SimpleDateFormat("yyyy-MM-dd-HH", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val out = ArrayList<Pair<String, java.io.File>>()
        root.listFiles()?.sortedBy { it.name }?.forEach { dayDir ->
            if (!dayDir.isDirectory) return@forEach
            dayDir.listFiles()?.sortedBy { it.name }?.forEach { hourFile ->
                if (!hourFile.isFile || !hourFile.name.endsWith(".ndjson")) return@forEach
                val hour = hourFile.name.substringBefore('-')
                val parsed = runCatching { dayFmt.parse("${dayDir.name}-$hour") }.getOrNull()
                if (parsed != null && parsed.time + TimeUnit.HOURS.toMillis(1) >= cutoffMillis) {
                    out += "logs/${dayDir.name}/${hourFile.name}" to hourFile
                }
            }
        }
        return out
    }
}
