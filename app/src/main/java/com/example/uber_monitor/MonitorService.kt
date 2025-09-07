package com.example.uber_monitor

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.app.NotificationCompat

class MonitorService : Service() {
    companion object {
        private const val CHANNEL_ID = "monitor_channel"
        private const val NOTIF_ID   = 1337
        private const val PREFS      = "setup_prefs"
        private const val MP_INTENT  = "mp_intent"
        private const val MP_RESULT  = "mp_result"
    }

    @SuppressLint("ServiceCast")
    override fun onCreate() {
        super.onCreate()
        // 1) Create notification channel (Oreo+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ride Monitor", NotificationManager.IMPORTANCE_LOW)
            )
        }

        // 2) Build & start foreground notification
        val note = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ride Monitoring Active")
            .setContentText("Capturing Pathao/Uber trips in background")
            .setSmallIcon(R.drawable.ic_notification)  // your icon
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, note)

        // 3) Initialize MediaProjection from saved prefs
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val uri = prefs.getString(MP_INTENT, null)
        val res = prefs.getInt(MP_RESULT, -1)
        if (uri != null && res == Activity.RESULT_OK) {
            val mpIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            MediaProjectionSingleton.projectionData = mpIntent
            MediaProjectionSingleton.resultCode = res
        }

        // 4) Kick off your AccessibilityServices or any other monitoring
        //    (they will pick up MediaProjectionSingleton and never re-prompt)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If the system kills us, recreate with null intent:
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        // clean up if needed
        super.onDestroy()
    }
}
