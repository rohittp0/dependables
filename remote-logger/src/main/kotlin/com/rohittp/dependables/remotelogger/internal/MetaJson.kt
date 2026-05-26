package com.rohittp.dependables.remotelogger.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import com.rohittp.dependables.remotelogger.DeviceId
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the `meta.json` envelope embedded in every export zip. Matches the field set
 * travel-animator-android emits (see `ui/manage_data/utils.kt:buildMetaJson`) so existing
 * tooling that consumes these zips keeps working.
 *
 * `buildType` from travel-animator's BuildConfig isn't reachable from a library, so we drop it.
 * The boolean `debug` is derived from `ApplicationInfo.FLAG_DEBUGGABLE`, which is equivalent
 * for every use we care about.
 */
internal object MetaJson {

    fun build(context: Context): String {
        val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val pkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val deviceId = runCatching { DeviceId.get() }.getOrNull()
        val deviceName = runCatching { DeviceId.name() }.getOrNull()

        return JSONObject().apply {
            put("exportedAt", exportedAt)
            put("applicationId", context.packageName)
            put("versionName", pkg?.versionName ?: JSONObject.NULL)
            put("versionCode", pkg?.let { longVersionCodeCompat(it) } ?: JSONObject.NULL)
            put("debug", debug)
            put("deviceId", deviceId ?: JSONObject.NULL)
            put("deviceName", deviceName ?: JSONObject.NULL)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("brand", Build.BRAND)
            put("product", Build.PRODUCT)
            put("fingerprint", Build.FINGERPRINT)
            put("androidRelease", Build.VERSION.RELEASE)
            put("androidSdk", Build.VERSION.SDK_INT)
            put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(","))
            put("locale", Locale.getDefault().toLanguageTag())
            put("timezone", TimeZone.getDefault().id)
        }.toString(2)
    }

    @Suppress("DEPRECATION")
    private fun longVersionCodeCompat(pkg: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode
        else pkg.versionCode.toLong()
}
