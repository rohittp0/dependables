package com.rohittp.dependables.remotelogger.internal

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the `meta.json` envelope embedded in every log dump zip. Helps the recipient identify
 * which build the logs came from without having to consult Crashlytics.
 */
internal object MetaJson {

    fun build(context: Context): String {
        val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val pkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return JSONObject().apply {
            put("exportedAt", exportedAt)
            put("applicationId", context.packageName)
            put("versionName", pkg?.versionName ?: JSONObject.NULL)
            put("versionCode", pkg?.longVersionCode ?: JSONObject.NULL)
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
}
