package com.rohittp.dependables.remotelogger

import android.app.Application
import android.content.Context
import android.os.Build

/**
 * Derives a stable short label for the current process.
 *
 * Main process → `"main"`. A named child process declared as e.g. `android:process=":remote"`
 * → `"remote"`. Anything blank or unresolvable → `"main"`.
 *
 * The tag becomes part of the log filename (`HH-<tag>.ndjson`) so processes don't contend on
 * the same file.
 */
object ProcessTag {

    @JvmStatic
    fun infer(context: Context): String {
        val name = currentProcessName(context).orEmpty()
        val suffix = name.substringAfter(':', missingDelimiterValue = "main")
        return suffix.ifBlank { "main" }
    }

    private fun currentProcessName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        // Fallback for API < 28: walk ActivityManager.runningAppProcesses for our PID.
        val pid = android.os.Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? android.app.ActivityManager ?: return null
        return am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
    }
}
