package com.rohittp.dependables.sample

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rohittp.dependables.remotelogger.RemoteLogger
import timber.log.Timber

/**
 * Canonical FCM-integration pattern. The library never owns the service — the consumer does,
 * and routes recognised payloads to [RemoteLogger.handleMessage] first.
 */
class SampleFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (RemoteLogger.handleMessage(applicationContext, message.data)) return

        // Fall-through for any other payload your app cares about.
        Timber.i("unhandled message: data=%s notification=%s", message.data, message.notification)
    }

    override fun onNewToken(token: String) {
        Timber.i("new FCM token: %s", token)
    }
}
