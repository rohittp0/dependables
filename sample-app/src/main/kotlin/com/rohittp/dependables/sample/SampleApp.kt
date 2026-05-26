package com.rohittp.dependables.sample

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.rohittp.dependables.remotelogger.RemoteLogger
import timber.log.Timber

class SampleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        RemoteLogger.init(this)

        // The sample ships without a google-services.json so this build is publishable as-is.
        // Drop in a real one (and apply `id("com.google.gms.google-services")`) to exercise
        // the upload path.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            RemoteLogger.subscribeForDevice(this, deviceId())
        } else {
            Timber.i("Firebase not initialised — skipping subscribeForDevice")
        }
    }

    @SuppressLint("HardwareIds")
    private fun deviceId(): String =
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
            .ifBlank { "unknown" }
}
