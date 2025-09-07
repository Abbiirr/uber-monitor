package com.example.uber_monitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.Html
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.uber_monitor.pathao.PathaoPageHandler
import com.example.uber_monitor.uber.UberPageHandler
import com.example.uber_monitor.BuildConfig
import java.io.File
import java.io.FileOutputStream


class UberAccessibilityService : BaseAccessibilityService() {

    override val logTag = "UberServiceLogger"
    override val logFileName = "uber_driver_logs.txt"
    override val elementsToCapture: List<Pair<String, String>> = emptyList()
    private var isRecording = false
    private lateinit var pageHandler: UberPageHandler

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            packageNames = arrayOf("com.ubercab.driver")
        }
        pageHandler = UberPageHandler(this)
    }
    fun getForegroundApp(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 10000  // last 10 seconds
        val stats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            beginTime,
            endTime
        )
        if (stats.isNullOrEmpty()) return null

        val recentApp = stats.maxByOrNull { it.lastTimeUsed }
        return recentApp?.packageName
    }


    @SuppressLint("NewApi")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only react to window changes
        if (event?.eventType !in listOf(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            )
        ) return
        // Detect foreground package
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName ?: "null"

        // Start/stop recording when Uber Driver enters/exits foreground
//        when {
//            pkg == "com.ubercab.driver" && !isRecording -> startUberRecording()
//            pkg != "com.ubercab.driver" && isRecording -> stopUberRecording()
//        }

        val containers = root.findAccessibilityNodeInfosByViewId(
            "com.ubercab.driver:id/trip_details_container"
        ) ?: return
        if (containers.isEmpty()) return

        // Gather all raw text under the container(s)
        val raw = mutableListOf<String>()
        containers.forEach { gatherText(it, raw) }

        // Decode HTML entities and filter unwanted entries
        val lines = raw
            .map { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
            .filter { it.isNotEmpty() && !it.contains("staticmap?") && !it.endsWith("Chevron down small") }

        // Output everything as one big block
        val output = lines.joinToString("\n")
        logMessage(output)
        MediaProjectionSingleton.projectionData?.let { data ->
            val svcIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, Activity.RESULT_OK)
                putExtra(ScreenCaptureService.EXTRA_RESULT_INTENT, data)
            }
            // For API ≥ 26, must call startForegroundService
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }
        }
    }

    private fun startUberRecording() {
        MediaProjectionSingleton.projectionData?.let { data ->
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, Activity.RESULT_OK)
                putExtra(ScreenCaptureService.EXTRA_RESULT_INTENT, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            isRecording = true
            logMessage("▶︎ START RECORDING Uber")
        }
    }

    private fun stopUberRecording() {
        val intent = Intent(this, ScreenCaptureService::class.java)
            .setAction(ScreenCaptureService.ACTION_STOP)
        startService(intent)
        isRecording = false
        logMessage("⏹ STOP RECORDING Uber")
    }


    private fun gatherText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            n.text?.toString()?.let { out += it }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue += it }
        }
    }

    override fun captureAllElements(root: AccessibilityNodeInfo) {
        val pkg = root.packageName?.toString()
        if (pkg != "${BuildConfig.UBER_PKG}") return
        pageHandler.handle(root, ::logMessage)
    }
}
