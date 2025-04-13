package com.example.uber_monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity.RESULT_OK
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileOutputStream

class UberAccessibilityService : AccessibilityService() {

    // Flag to prevent capturing multiple screenshots for the same event.
    private var screenshotTaken = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == null) return

        // Log every accessibility event.
        Log.d("UberAccService", "Event from: ${event.packageName}, Type: ${event.eventType}")

        if (event.packageName == "com.ubercab") {
            Log.i("UberAccService", "Uber app is active.")

            // Get the root node of the active window.
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.d("UberAccService", "rootInActiveWindow is null")
                return
            }

            // Use a recursive search to find our target node.
            // This corresponds to the XPath:
            // (//android.view.View[@resource-id="order_selection_order_cell"])[1]/android.view.View[2]
            val targetNode = findTargetNode(rootNode)
            if (targetNode != null && !screenshotTaken) {
                screenshotTaken = true
                Log.i("UberAccService", "Target element found. Taking screenshot...")
                triggerScreenshot()
            } else if (targetNode == null && screenshotTaken) {
                Log.i("UberAccService", "Target element no longer found. Resetting screenshot flag.")
                screenshotTaken = false
            }
        } else {
            Log.d("UberAccService", "Non-Uber app event: ${event.packageName}")
        }
    }

    override fun onInterrupt() {
        // Handle interrupt if necessary.
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Configure the service to listen to all accessibility events.
        val info = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            // No package filtering to capture events from all apps.
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info
        Log.i("UberAccService", "Accessibility service connected")
    }

    /**
     * Recursively search for a node matching our criteria:
     * Find the first node with resource-id "order_selection_order_cell",
     * then return its second child (index 1), similar to:
     * (//android.view.View[@resource-id="order_selection_order_cell"])[1]/android.view.View[2]
     */
    private fun findTargetNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        // First attempt: mimic XPath expression
        val matchingNodes = root.findAccessibilityNodeInfosByViewId("order_selection_order_cell")
        if (matchingNodes.isNotEmpty()) {
            val firstMatch = matchingNodes[0]
            var viewChildIndex = 0
            for (i in 0 until firstMatch.childCount) {
                val child = firstMatch.getChild(i)
                if (child?.className == "android.view.View") {
                    if (viewChildIndex == 1) { // 2nd android.view.View child (0-based index)
                        Log.i("TargetMatch", "Found by XPath-like logic")
                        return child
                    }
                    viewChildIndex++
                }
            }
        }

        // Second attempt: mimic UiSelector().className("android.view.View").instance(15)
        var viewInstanceIndex = 0
        fun findViewByClassInstance(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.className == "android.view.View") {
                if (viewInstanceIndex == 15) {
                    Log.i("TargetMatch", "Found by UiSelector instance=15 logic")
                    triggerScreenshot()
                    return node
                }
                viewInstanceIndex++
            }
            for (i in 0 until node.childCount) {
                val result = findViewByClassInstance(node.getChild(i))
                if (result != null) return result
            }
            return null
        }

        return findViewByClassInstance(root)
    }


    private fun triggerScreenshot() {
        val projectionData = MediaProjectionSingleton.projectionData
        if (projectionData == null) {
            Log.e("UberAccService", "MediaProjection data unavailable!")
            return
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection: MediaProjection? = projectionManager.getMediaProjection(RESULT_OK, projectionData)
        if (mediaProjection == null) {
            Log.e("UberAccService", "Failed to start MediaProjection")
            return
        }
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection.createVirtualDisplay(
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
                virtualDisplay.release()
                mediaProjection.stop()
            }
        }, Handler(Looper.getMainLooper()))
    }

    // Save the captured bitmap as a PNG file.
    private fun saveBitmap(bitmap: Bitmap) {
        try {
            val screenshotsDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots")
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs()
            }
            val fileName = "uber_screenshot_${System.currentTimeMillis()}.png"
            val file = File(screenshotsDir, fileName)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            Log.i("UberAccService", "Screenshot saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("UberAccService", "Error saving screenshot: ${e.message}")
        }
    }
}
