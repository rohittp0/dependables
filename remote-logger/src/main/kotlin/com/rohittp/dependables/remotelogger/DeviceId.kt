package com.rohittp.dependables.remotelogger

import android.media.MediaDrm
import android.os.Build
import android.util.Base64
import java.util.UUID

/**
 * Stable per-device identifier derived from the Widevine DRM
 * [MediaDrm.PROPERTY_DEVICE_UNIQUE_ID], with a deterministic fallback to a hash of `Build`
 * fields when Widevine is unavailable or broken (some emulators, AOSP builds without DRM).
 *
 * The result is URL- and FCM-topic-safe (`[A-Za-z0-9._-]`) and capped at 32 characters so it
 * can be dropped straight into a topic suffix or a Storage path segment.
 *
 * Public so the consumer can mirror the same ID elsewhere (e.g. log it on the backend,
 * include it in support emails).
 */
object DeviceId {

    private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

    @Volatile
    private var cached: String? = null

    /** Returns a stable, deterministic device id. Result is cached after the first call. */
    @JvmStatic
    fun get(): String {
        cached?.let { return it }
        val computed = compute()
        cached = computed
        return computed
    }

    private fun compute(): String {
        // MediaDrm only became AutoCloseable in API 28; do an explicit release for minSdk 24.
        val wideVineId = runCatching {
            val drm = MediaDrm(WIDEVINE_UUID)
            try {
                drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            } finally {
                @Suppress("DEPRECATION")
                drm.release()
            }
        }.getOrNull()

        val fullId = if (wideVineId != null) {
            Base64.encodeToString(
                wideVineId,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        } else {
            // Devices without Widevine (or with a broken DRM stack) throw
            // UnsupportedSchemeException. Fall back to a stable hash of Build fields so the
            // ID remains deterministic across launches.
            val buildSignature = listOf(
                Build.FINGERPRINT,
                Build.MANUFACTURER,
                Build.MODEL,
                Build.HARDWARE,
                Build.BOARD,
            ).joinToString("|")
            Integer.toHexString(buildSignature.hashCode())
        }

        // Build.FINGERPRINT can still contain '/', ':' etc. Coerce to the URL/FCM-topic-safe
        // charset so the ID is directly usable as an FCM topic suffix and Storage path segment.
        return fullId.takeLast(32).replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    /** Human-readable device label: `<manufacturer> <model>`, truncated to 32 chars. */
    @JvmStatic
    fun name(): String {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return if (deviceName.length > 32) deviceName.take(32) else deviceName
    }
}
