package com.rohittp.dependables.remotelogger

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class FileLoggingTreeTest {

    @Test
    fun infoLinesWriteNdjsonFileWithExpectedShape() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        File(ctx.filesDir, "logs").deleteRecursively()

        val tree = FileLoggingTree(ctx, "main")
        Timber.plant(tree)
        try {
            Timber.i("alpha")
            Timber.w("beta")
            Timber.d("dropped")    // below INFO → must not appear
            Timber.e("gamma")

            // Force the flush executor to settle. The tree schedules a 500 ms flush, but a
            // hard sleep is acceptable here since this is the only thing on the test thread.
            Thread.sleep(800)
        } finally {
            Timber.uproot(tree)
        }

        val dayDir = newestSubdir(File(ctx.filesDir, "logs"))
        assertNotNull("expected a day directory under logs/", dayDir)
        val files = dayDir!!.listFiles { f -> f.name.endsWith("-main.ndjson") }.orEmpty()
        assertEquals(1, files.size)

        val lines = files[0].readLines().filter { it.isNotBlank() }
        assertEquals("DEBUG should have been dropped", 3, lines.size)

        val first = JSONObject(lines[0])
        assertEquals("alpha", first.getString("msg"))
        assertEquals("I", first.getString("lvl"))
        assertTrue("ts must be ISO-8601 zulu",
            first.getString("ts").matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")))
    }

    @Test
    fun purgeOldDropsDayDirectoriesOlderThanRetention() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val logs = File(ctx.filesDir, "logs").apply { deleteRecursively(); mkdirs() }
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = System.currentTimeMillis()
        val tenDaysAgo = dayFmt.format(Date(now - 10L * 24 * 3600 * 1000))
        val yesterday = dayFmt.format(Date(now - 24L * 3600 * 1000))

        File(logs, tenDaysAgo).mkdirs()
        File(logs, yesterday).mkdirs()
        File(logs, "not-a-date").mkdirs()

        FileLoggingTree.purgeOld(ctx, retentionDays = 7)

        assertTrue(File(logs, yesterday).exists())
        assertTrue("non-date dirs are pruned", !File(logs, "not-a-date").exists())
        assertTrue("old day dir is pruned", !File(logs, tenDaysAgo).exists())
    }

    private fun newestSubdir(root: File): File? =
        root.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.lastModified() }
}
