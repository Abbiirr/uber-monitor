package com.example.uber_monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.network.LogSender
import com.example.uber_monitor.pathao.PathaoPageHandler
import com.example.uber_monitor.uber.UberPageHandler
import java.io.File
import java.io.FileWriter

class RideMonitorAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val intervalMs: Long = 500
    private lateinit var pathaoHandler: PathaoPageHandler
    private lateinit var uberHandler: UberPageHandler

    private val logFiles = mapOf(
        "com.pathao.driver" to "pathao_logs.txt",
        "com.ubercab.driver" to "uber_logs.txt"
    )

    private val pollTask = object : Runnable {
        override fun run() {
            rootInActiveWindow?.let { root ->
                val packageName = root.packageName?.toString()
                when (packageName) {
                    BuildConfig.PATHAO_PKG -> handlePathaoScreen(root)
                    BuildConfig.UBER_PKG -> handleUberScreen(root)
                }
            }
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            // Monitor both packages
            packageNames = arrayOf(BuildConfig.PATHAO_PKG, BuildConfig.UBER_PKG)
        }

        LogSender.init(this)
        pathaoHandler = PathaoPageHandler(this)
        uberHandler = UberPageHandler(this)

        handler.post(pollTask)
        Log.i("RideMonitor", "Service connected - monitoring Pathao and Uber")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Using polling instead, but can handle specific events here if needed
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(pollTask)
        super.onDestroy()
    }

    private fun handlePathaoScreen(root: AccessibilityNodeInfo) {
        pathaoHandler.handle(root) { msg ->
            logMessage("PATHAO", msg, "pathao_logs.txt")
        }
    }

    private fun handleUberScreen(root: AccessibilityNodeInfo) {
        uberHandler.handle(root) { msg ->
            logMessage("UBER", msg, "uber_logs.txt")
        }
    }

    private fun logMessage(app: String, msg: String, fileName: String) {
        Log.d("RideMonitor-$app", msg)
        try {
            val file = File(getExternalFilesDir(null), fileName)
            FileWriter(file, true).use {
                it.append("${System.currentTimeMillis()}: $msg\n")
            }
        } catch (e: Exception) {
            Log.e("RideMonitor", "Log write error", e)
        }
    }
}