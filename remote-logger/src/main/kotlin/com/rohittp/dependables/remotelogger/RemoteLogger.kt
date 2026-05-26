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
 * RemoteLogger.subscribeForDevice(this)
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
     * device with a `dump_logs` data message. The device id comes from [DeviceId.get].
     */
    @JvmStatic
    fun subscribeForDevice(context: Context) {
        val topic = TOPIC_PREFIX + DeviceId.get()
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
     */
    @JvmStatic
    fun handleMessage(context: Context, data: Map<String, String>): Boolean {
        if (data["type"] != DUMP_TYPE) return false
        val hours = data[DUMP_HOURS_KEY]?.toIntOrNull() ?: config.defaultDumpHours
        enqueueDump(context, hours)
        return true
    }

    /**
     * Manually enqueue a one-time export upload. Useful for in-app "send my logs" buttons and
     * smoke tests. Same WorkManager job that [handleMessage] would enqueue.
     */
    @JvmStatic
    @JvmOverloads
    fun enqueueDump(context: Context, hours: Int = config.defaultDumpHours) {
        LogDumpWorker.enqueue(context, hours)
    }
}
