package com.rohittp.dependables.remotelogger

/**
 * Customisation knobs for [RemoteLogger].
 *
 * Defaults match the original travel-animator-android wire-up:
 *   - uploads land at `user/<deviceId>/logs/<utc-yyyyMMdd_HHmmss>.zip` in Firebase Storage
 *   - on-device retention is 7 days
 *   - the default `dump_logs` payload (no `hours` key) collects the last 24 hours
 *
 * Pass an override to [RemoteLogger.configure] before calling [RemoteLogger.init].
 */
data class RemoteLoggerConfig(
    val storagePathBuilder: (deviceId: String, timestamp: String) -> String =
        { id, ts -> "user/$id/logs/$ts.zip" },
    val onDeviceRetentionDays: Long = FileLoggingTree.DEFAULT_RETENTION_DAYS,
    val defaultDumpHours: Int = 24,
)
