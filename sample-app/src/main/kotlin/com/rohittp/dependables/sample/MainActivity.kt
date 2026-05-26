package com.rohittp.dependables.sample

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.rohittp.dependables.remotelogger.internal.LogDumpWorker
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.log_button).setOnClickListener {
            Timber.i("hello %d", ++counter)
        }
        findViewById<Button>(R.id.dump_button).setOnClickListener {
            // Same call the FCM payload would make — for manual smoke-testing without a push.
            LogDumpWorker.enqueue(this, hours = 1)
        }
    }
}
