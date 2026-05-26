package com.rohittp.dependables.remotelogger

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FileLoggingTreeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val planted = mutableListOf<Timber.Tree>()

    @After
    fun tearDown() {
        planted.forEach { Timber.uproot(it) }
    }

    @Test
    fun infoLinesWriteNdjsonFileWithExpectedShape() {
        val logsRoot = tmp.newFolder("logs")
        val tree = FileLoggingTree(logsRoot, "main")
        Timber.plant(tree).also { planted += tree }

        Timber.i("alpha")
        Timber.w("beta")
        Timber.d("dropped")
        Timber.e("gamma")

        // Tree flushes every 500 ms; wait a bit longer to be safe on slow CI.
        Thread.sleep(1200)

        val dayDir = newestSubdir(logsRoot)
        assertNotNull("expected a day directory under logs/", dayDir)
        val files = dayDir!!.listFiles { f -> f.name.endsWith("-main.ndjson") }.orEmpty()
        assertEquals(1, files.size)

        val lines = files[0].readLines().filter { it.isNotBlank() }
        assertEquals("DEBUG should have been dropped", 3, lines.size)

        // Plain-text assertions to avoid the org.json stub in Android unit tests.
        val first = lines[0]
        assertTrue("first line carries msg=alpha: $first", first.contains("\"msg\":\"alpha\""))
        assertTrue("first line carries lvl=I: $first", first.contains("\"lvl\":\"I\""))
        assertTrue("first line carries ISO-8601 ts: $first",
            first.contains(Regex("\"ts\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\"")))
    }

    @Test
    fun purgeOldDropsDayDirectoriesOlderThanRetention() {
        val logsRoot = tmp.newFolder("logs")
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = System.currentTimeMillis()
        val tenDaysAgo = dayFmt.format(Date(now - 10L * 24 * 3600 * 1000))
        val yesterday = dayFmt.format(Date(now - 24L * 3600 * 1000))

        File(logsRoot, tenDaysAgo).mkdirs()
        File(logsRoot, yesterday).mkdirs()
        File(logsRoot, "not-a-date").mkdirs()

        FileLoggingTree.purgeOldIn(logsRoot, retentionDays = 7)

        assertTrue(File(logsRoot, yesterday).exists())
        assertTrue("non-date dirs are pruned", !File(logsRoot, "not-a-date").exists())
        assertTrue("old day dir is pruned", !File(logsRoot, tenDaysAgo).exists())
    }

    private fun newestSubdir(root: File): File? =
        root.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.lastModified() }
}
