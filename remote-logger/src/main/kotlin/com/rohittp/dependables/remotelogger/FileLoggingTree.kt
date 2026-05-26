package com.rohittp.dependables.remotelogger

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Timber tree that writes structured (NDJSON) log lines to `filesDir/logs/<yyyy-MM-dd>/<HH>-<tag>.ndjson`.
 *
 * Lines below [Log.INFO] are dropped — DEBUG and VERBOSE are noisy and not worth uploading.
 *
 * The tree owns a single background executor that flushes the in-memory buffer every
 * [FLUSH_INTERVAL_MS] ms, or sooner if the buffer exceeds [BATCH_MAX_BYTES].
 *
 * Multi-process safety: each process plants its own instance with a distinct [processTag], so
 * file paths never overlap. Use [ProcessTag.infer] to derive the tag from the current process name.
 */
class FileLoggingTree(context: Context, processTag: String) : Timber.Tree() {

    private val logsRoot: File = File(context.filesDir, LOGS_DIR).apply { mkdirs() }
    private val tag: String = sanitiseTag(processTag)

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "FileLoggingTree").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }

    private val buffer = StringBuilder(BATCH_MAX_BYTES)
    private val bufferedBytes = AtomicLong(0L)

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val hourFmt = SimpleDateFormat("HH", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        executor.scheduleWithFixedDelay(
            ::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS
        )
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.INFO) return

        val now = Date()
        val threadName = Thread.currentThread().name
        val safeTag = inferCallerTag()
        val line = buildLine(now, priority, safeTag, threadName, message, t)

        executor.execute { append(now, line) }
    }

    private fun inferCallerTag(): String {
        val stack = Throwable().stackTrace
        val caller = stack.firstOrNull { el ->
            val c = el.className
            !c.startsWith("timber.log.") && c != this::class.java.name
        }
        return if (caller != null) {
            val simple = caller.className.substringAfterLast('.')
            "${caller.methodName}:$simple(${caller.fileName}:${caller.lineNumber})"
        } else {
            "App"
        }
    }

    private fun append(now: Date, line: String) {
        synchronized(buffer) {
            buffer.append(line).append('\n')
            val size = bufferedBytes.addAndGet(line.length.toLong() + 1L)
            if (size >= BATCH_MAX_BYTES) flushLocked(now)
        }
    }

    private fun flush() {
        synchronized(buffer) { flushLocked(Date()) }
    }

    private fun flushLocked(now: Date) {
        if (buffer.isEmpty()) return
        val dayDir = File(logsRoot, dayFmt.format(now)).apply { mkdirs() }
        val target = File(dayDir, "${hourFmt.format(now)}-$tag.ndjson")
        runCatching {
            FileWriter(target, true).use { it.write(buffer.toString()) }
        }
        buffer.setLength(0)
        bufferedBytes.set(0L)
    }

    private fun sanitiseTag(raw: String): String {
        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        return cleaned.ifEmpty { "proc" }
    }

    private fun buildLine(
        now: Date,
        priority: Int,
        tag: String?,
        thread: String,
        message: String,
        t: Throwable?
    ): String {
        val sb = StringBuilder(message.length + 128)
        sb.append('{')
        appendField(sb, "ts", tsFmt.format(now)); sb.append(',')
        appendField(sb, "lvl", levelLabel(priority)); sb.append(',')
        appendField(sb, "tag", tag ?: ""); sb.append(',')
        appendField(sb, "thread", thread); sb.append(',')
        appendField(sb, "msg", message)
        if (t != null) {
            sb.append(',')
            appendField(sb, "t", Log.getStackTraceString(t))
        }
        sb.append('}')
        return sb.toString()
    }

    private fun appendField(sb: StringBuilder, key: String, value: String) {
        sb.append('"').append(key).append("\":")
        escapeJson(sb, value)
    }

    private fun escapeJson(sb: StringBuilder, value: String) {
        sb.append('"')
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append("%04x".format(c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }

    private fun levelLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    companion object {
        private const val LOGS_DIR = "logs"
        private const val FLUSH_INTERVAL_MS = 500L
        private const val BATCH_MAX_BYTES = 64 * 1024

        /** Default retention used by [purgeOld] when no value is passed. */
        const val DEFAULT_RETENTION_DAYS: Long = 7

        /**
         * Delete day-directories older than [retentionDays]. Safe to call from any process,
         * but typically only the main process needs to.
         */
        @JvmStatic
        @JvmOverloads
        fun purgeOld(context: Context, retentionDays: Long = DEFAULT_RETENTION_DAYS) {
            val root = File(context.filesDir, LOGS_DIR)
            if (!root.isDirectory) return
            val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)
            root.listFiles()?.forEach { child ->
                val dirDate = runCatching { dayFmt.parse(child.name) }.getOrNull()
                if (child.isDirectory && (dirDate == null || dirDate.time < cutoff)) {
                    child.deleteRecursively()
                }
            }
        }

        /** Root directory under which day-buckets are written. */
        @JvmStatic
        fun logsRoot(context: Context): File = File(context.filesDir, LOGS_DIR)
    }
}
