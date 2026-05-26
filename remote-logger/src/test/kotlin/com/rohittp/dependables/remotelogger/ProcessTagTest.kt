package com.rohittp.dependables.remotelogger

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProcessTagTest {

    @Test
    fun mainProcessYieldsMainTag() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        // Robolectric reports the package name as the process name; ProcessTag treats anything
        // without a `:` suffix as the main process.
        assertEquals("main", ProcessTag.infer(ctx))
    }
}
