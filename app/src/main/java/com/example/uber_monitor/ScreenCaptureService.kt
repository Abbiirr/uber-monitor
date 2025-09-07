// ScreenCaptureService.kt
package com.example.uber_monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File

class ScreenCaptureService : Service() {
    companion object {
        private const val NOTIF_CHANNEL = "screen_capture_vid"
        private const val NOTIF_ID      = 1001

        const val EXTRA_RESULT_CODE   = "result_code"
        const val EXTRA_RESULT_INTENT = "result_intent"
        const val ACTION_START        = "com.example.uber_monitor.action.START"
        const val ACTION_STOP         = "com.example.uber_monitor.action.STOP"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var projectionIntent: Intent?   = null
    private var resultCode: Int             = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    NOTIF_CHANNEL,
                    "Screen Recording",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                // cache the projection info
                resultCode       = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                projectionIntent = intent.getParcelableExtra(EXTRA_RESULT_INTENT)

                // 1) immediately go foreground *as mediaProjection type*
                startForegroundServiceWithNotif()

                // 2) wait a small delay, *then* start the MediaProjection
                mainHandler.postDelayed({
                    startRecording()
                }, 200) // 200ms is usually enough
            }

            ACTION_STOP -> {
                stopRecording()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotif() {
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Uber Monitor: Recording")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // must pass the mediaProjection type here
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startRecording() {
        projectionIntent?.let { data ->
            // configure MediaRecorder
            mediaRecorder = MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                val outFile = File(getExternalFilesDir(null),
                    "uber_record_${System.currentTimeMillis()}.mp4")
                setOutputFile(outFile.absolutePath)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(5_000_000)
                setVideoFrameRate(30)

                // get screen size
                val dm = resources.displayMetrics
                setVideoSize(dm.widthPixels, dm.heightPixels)

                prepare()
            }

            // now it's safe to getMediaProjection *after* we’re in a proper FGS
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager
            val mp = mgr.getMediaProjection(resultCode, data)

            // create the virtual display
            mp.createVirtualDisplay(
                "rec_disp",
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels,
                resources.displayMetrics.densityDpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface,
                null,
                null
            )

            // start actual recording
            mediaRecorder!!.start()
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try { stop() } catch (_: Exception) {}
            reset()
            release()
        }
        mediaRecorder = null
    }

    override fun onBind(intent: Intent?) = null
}
