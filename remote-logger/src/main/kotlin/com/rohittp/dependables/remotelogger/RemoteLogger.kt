package com.rohittp.dependables.remotelogger

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.rohittp.dependables.remotelogger.internal.LogDumpWorker
import timber.log.Timber

/**
 * Facade for the remote-logger library.
 *
 * Typical wiring in an `Application.onCreate`:
 *
 * ```kotlin
 * RemoteLogger.init(this)
 * if (isMainProcess) RemoteLogger.subscribeForDevice(this, AndroidId.get(this))
 * ```
 *
 * Inside a `FirebaseMessagingService`:
 *
 * ```kotlin
 * override fun onMessageReceived(msg: RemoteMessage) {
 *     if (RemoteLogger.handleMessage(applicationContext, msg.data)) return
 *     // ... your own routing
 * }
 * ```
 */
object RemoteLogger {

    private const val DUMP_TYPE = "dump_logs"
    private const val DUMP_HOURS_KEY = "hours"
    private const val TOPIC_PREFIX = "logdump_"

    @Volatile
    private var config: RemoteLoggerConfig = RemoteLoggerConfig()

    /** Override defaults. Call before [init] if you need a non-default storage path. */
    @JvmStatic
    fun configure(config: RemoteLoggerConfig) {
        this.config = config
    }

    /** Currently effective config. */
    @JvmStatic
    fun config(): RemoteLoggerConfig = config

    /**
     * Plant a [FileLoggingTree] for the current process and purge old day-buckets.
     *
     * Safe to call from every process; only the main process performs the purge (others would
     * race with the main process's own purge). Pass [processTag] explicitly if you want to
     * override the auto-inferred tag.
     */
    @JvmStatic
    @JvmOverloads
    fun init(context: Context, processTag: String = ProcessTag.infer(context)) {
        Timber.plant(FileLoggingTree(context, processTag))
        if (processTag == "main") {
            FileLoggingTree.purgeOld(context, config.onDeviceRetentionDays)
        }
    }

    /**
     * Subscribe the device to topic `logdump_<deviceId>` so the backend can target a single
     * device with a `dump_logs` data message.
     */
    @JvmStatic
    fun subscribeForDevice(context: Context, deviceId: String) {
        val topic = TOPIC_PREFIX + sanitiseTopic(deviceId)
        FirebaseMessaging.getInstance()
            .subscribeToTopic(topic)
            .addOnFailureListener { e ->
                Timber.w(e, "RemoteLogger: failed to subscribe to %s", topic)
            }
    }

    /**
     * Inspect an incoming FCM data payload. Returns true iff this was a `dump_logs` request
     * (and the upload has been enqueued); otherwise false so the consumer's service can keep
     * routing.
     *
     * Recognised keys:
     *   - `type` = `"dump_logs"` — required
     *   - `hours` = integer string — optional, defaults to [RemoteLoggerConfig.defaultDumpHours]
     *   - `deviceId` = string — optional override; if absent, the worker resolves
     *     `Settings.Secure.ANDROID_ID`
     */
    @JvmStatic
    fun handleMessage(context: Context, data: Map<String, String>): Boolean {
        if (data["type"] != DUMP_TYPE) return false
        val hours = data[DUMP_HOURS_KEY]?.toIntOrNull() ?: config.defaultDumpHours
        val deviceId = data["deviceId"]
        LogDumpWorker.enqueue(context, hours, deviceId)
        return true
    }

    /** Topic names accept `[a-zA-Z0-9-_.~%]+` only. */
    private fun sanitiseTopic(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9\\-_.~%]"), "_")
}
