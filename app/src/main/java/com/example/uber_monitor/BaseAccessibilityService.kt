package com.example.uber_monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileWriter
import java.io.IOException

abstract class BaseAccessibilityService : AccessibilityService() {

    // Subclasses supply their own log tag, file name & element‑list
    protected abstract val logTag: String
    protected abstract val logFileName: String
    protected abstract val elementsToCapture: List<Pair<String, String>>

    // Common handler & polling interval
    private val handler = Handler(Looper.getMainLooper())
    private val intervalMs: Long = 500

    // Lazy log file in app storage
    private val logFile: File by lazy {
        File(applicationContext.getExternalFilesDir(null), logFileName)
    }

    // The shared polling task
    private val pollTask = object : Runnable {
        override fun run() {
            // DEBUG: print the package & class of the active window root
            val root = rootInActiveWindow
            val pkg = root?.packageName ?: "null"
//            logMessage("DEBUG: poll tick — root package = $pkg")
            rootInActiveWindow?.let { captureAllElements(it) }
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
        }
        handler.post(pollTask)
        Log.i(logTag, "Service connected – starting scan")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no‑op; using polling
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(pollTask)
        super.onDestroy()
    }

    /** Logs text to Logcat + file */
    fun logMessage(msg: String) {
        Log.d(logTag, msg)
        try {
            FileWriter(logFile, true).use { it.append("${System.currentTimeMillis()}: $msg\n") }
        } catch (e: IOException) {
            Log.e(logTag, "Log write error: ${e.message}")
        }
    }

    /** Default “static” captures by resource‑ID */
    protected open fun captureAllElements(root: AccessibilityNodeInfo) {
        elementsToCapture.forEach { (resId, label) ->
            val text = root.findAccessibilityNodeInfosByViewId(resId)
                ?.firstOrNull()?.text?.toString()
            logMessage("$label: \"${text ?: "NOT FOUND"}\"")
        }
    }
}
