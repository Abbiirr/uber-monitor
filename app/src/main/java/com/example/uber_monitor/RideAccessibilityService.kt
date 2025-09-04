package com.example.uber_monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class UberAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UberAccService"
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val UBER_PACKAGE = "com.ubercab"
        private const val PATHAO_PACKAGE = "com.pathao"
        private const val PATHAO_DRIVER_PACKAGE = "com.pathao.driver"

        private const val NOTIFICATION_CHANNEL_ID = "uber_monitor_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var screenshotTaken = false
    private var lastPackageName = ""
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == null) return

        val packageName = event.packageName.toString()

        if (packageName != lastPackageName) {
            lastPackageName = packageName
            Log.i(TAG, "Current app: $packageName")
            updateServiceLog("Current app: $packageName")
        }

        when (packageName) {
            UBER_DRIVER_PACKAGE -> {
                Log.i(TAG, "UBER DRIVER DETECTED! Package: $packageName")
                updateServiceLog("UBER DRIVER DETECTED!")
                handleUberDriverEvent(event)
            }
            UBER_PACKAGE -> {
                Log.i(TAG, "Uber passenger app detected: $packageName")
                updateServiceLog("Uber passenger app active")
                handleUberEvent(event)
            }
            PATHAO_DRIVER_PACKAGE -> {
                Log.i(TAG, "Pathao Driver detected: $packageName")
                updateServiceLog("Pathao Driver detected")
            }
            PATHAO_PACKAGE -> {
                Log.i(TAG, "Pathao app detected: $packageName")
                updateServiceLog("Pathao app active")
            }
            else -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    Log.d(TAG, "Other app: $packageName")
                }
            }
        }
    }

    private fun handleUberDriverEvent(event: AccessibilityEvent) {
        Log.i(TAG, "Processing Uber Driver event")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.d(TAG, "rootInActiveWindow is null")
            return
        }

        try {
            val allText = extractAllText(rootNode)
            if (allText.isNotEmpty()) {
                Log.i(TAG, "Uber Driver screen content: ${allText.take(200)}")
            }

            val targetNode = findUberDriverElements(rootNode)
            if (targetNode != null && !screenshotTaken) {
                screenshotTaken = true
                Log.i(TAG, "Uber Driver target element found! Taking screenshot...")
                updateServiceLog("Capturing Uber Driver screen")
                triggerScreenshotWithForeground()
                targetNode.recycle()
            } else if (targetNode == null && screenshotTaken) {
                Log.i(TAG, "Target element no longer found")
                screenshotTaken = false
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun handleUberEvent(event: AccessibilityEvent) {
        Log.i(TAG, "Processing regular Uber event")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.d(TAG, "rootInActiveWindow is null")
            return
        }

        try {
            val targetNode = findTargetNode(rootNode)
            if (targetNode != null && !screenshotTaken) {
                screenshotTaken = true
                Log.i(TAG, "Target element found. Taking screenshot...")
                triggerScreenshotWithForeground()
                targetNode.recycle()
            } else if (targetNode == null && screenshotTaken) {
                Log.i(TAG, "Target element no longer found")
                screenshotTaken = false
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun triggerScreenshotWithForeground() {
        // Start foreground service before media projection
        startForegroundService()

        Handler(Looper.getMainLooper()).postDelayed({
            triggerScreenshot()
        }, 100)
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Uber Monitor")
            .setContentText("Capturing screen...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Uber Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen capture notifications"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun triggerScreenshot() {
        val projectionData = MediaProjectionSingleton.projectionData
        if (projectionData == null) {
            Log.e(TAG, "MediaProjection data unavailable!")
            stopForegroundService()
            return
        }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(Activity.RESULT_OK, projectionData)

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to start MediaProjection")
                stopForegroundService()
                return
            }

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val virtualDisplay = mediaProjection?.createVirtualDisplay(
                "UberRideScreenshot",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val planes = image.planes
                if (planes.isNotEmpty()) {
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    saveBitmap(bitmap)
                    image.close()
                }
                virtualDisplay?.release()
                mediaProjection?.stop()
                mediaProjection = null

                // Stop foreground after capture
                Handler(Looper.getMainLooper()).postDelayed({
                    stopForegroundService()
                }, 500)
            }, Handler(Looper.getMainLooper()))

        } catch (e: Exception) {
            Log.e(TAG, "Error in triggerScreenshot", e)
            stopForegroundService()
        }
    }

    private fun findUberDriverElements(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        val searchTerms = listOf(
            "GO", "Accept", "Decline", "Online", "Offline",
            "earnings", "trips", "Navigate", "Pickup", "Dropoff"
        )

        for (term in searchTerms) {
            val nodes = root.findAccessibilityNodeInfosByText(term)
            if (nodes.isNotEmpty()) {
                Log.i(TAG, "Found Uber Driver element with text: $term")
                return nodes[0]
            }
        }

        return findNodeRecursive(root) { node ->
            (node.isClickable || node.className == "android.widget.Button") &&
                    node.text != null
        }
    }

    private fun findTargetNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        val resourceIds = listOf(
            "com.ubercab:id/order_selection_order_cell",
            "com.ubercab.driver:id/accept_button",
            "order_selection_order_cell"
        )

        for (id in resourceIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                Log.i(TAG, "Found node with ID: $id")
                return nodes[0]
            }
        }

        val textPatterns = listOf("Request", "Accept", "Decline", "Navigate")
        for (text in textPatterns) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                Log.i(TAG, "Found node with text: $text")
                return nodes[0]
            }
        }

        return findNodeRecursive(root) { node ->
            node.className == "android.widget.Button" && node.isClickable
        }
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null

        if (predicate(node)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, predicate)
            if (result != null) {
                return result
            }
            child.recycle()
        }

        return null
    }

    private fun extractAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val builder = StringBuilder()
        val queue = mutableListOf(node)

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)

            current.text?.let {
                builder.append(it).append(" ")
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return builder.toString().trim()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        stopForegroundService()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        stopForegroundService()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED

            packageNames = null
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        Log.i(TAG, "Accessibility service connected")
        updateServiceLog("Service started - Ready to detect Uber Driver")
    }

    private fun saveBitmap(bitmap: Bitmap) {
        try {
            val screenshotsDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots")
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "uber_${timestamp}.png"
            val file = File(screenshotsDir, fileName)

            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            Log.i(TAG, "Screenshot saved: ${file.absolutePath}")
            updateServiceLog("Screenshot saved: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving screenshot: ${e.message}")
        }
    }

    private fun updateServiceLog(message: String) {
        try {
            val prefs = getSharedPreferences("uber_monitor_logs", Context.MODE_PRIVATE)
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            prefs.edit().apply {
                putString("last_log", "$timestamp: $message")
                putInt("log_count", prefs.getInt("log_count", 0) + 1)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating log", e)
        }
    }
}