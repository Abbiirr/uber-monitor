package com.example.uber_monitor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.*

class UberAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UberAccService"
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val UBER_PACKAGE = "com.ubercab"
        private const val PATHAO_PACKAGE = "com.pathao"
        private const val PATHAO_DRIVER_PACKAGE = "com.pathao.driver"
    }

    private var lastPackageName = ""

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
                updateServiceLog("Uber Driver active - extracted ${allText.split(" ").size} words")
            }

            val targetNode = findUberDriverElements(rootNode)
            if (targetNode != null) {
                Log.i(TAG, "Found Uber Driver element: ${targetNode.text}")
                updateServiceLog("Found: ${targetNode.text}")
                targetNode.recycle()
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
            if (targetNode != null) {
                Log.i(TAG, "Found element: ${targetNode.text}")
                updateServiceLog("Found: ${targetNode.text}")
                targetNode.recycle()
            }
        } finally {
            rootNode.recycle()
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