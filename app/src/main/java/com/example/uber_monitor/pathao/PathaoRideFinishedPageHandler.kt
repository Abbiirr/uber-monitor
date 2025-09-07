// File: PathaoRideFinishedPageHandler.kt
package com.example.uber_monitor.pathao

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig
import com.example.uber_monitor.network.LogSender
import com.example.uber_monitor.utils.LogCacheValidator
import org.json.JSONObject

/**
 * Handles capture logic for the Pathao Driver Ride Finished page,
 * capturing the summary data once and sending a single JSON payload.
 */
class PathaoRideFinishedPageHandler (
    private val context: Context
) {
    private val detector = PathaoPageDetector()
    private var hasCaptured = false
    private val validator = LogCacheValidator(context)

    private companion object {
        const val FINISHED_URL = "https://giglytech-data-collection-api.global.fintech23.xyz/api/v1/pathao/raw-trip-finished"
        private const val DATA_TYPE = "ride_finish"
    }

    /**
     * Delegates capture when the page first appears and resets state when leaving.
     */
    fun handle(root: AccessibilityNodeInfo, logMessage: (String) -> Unit) {
        if (detector.isRideFinishedPage(root)) {
            if (!hasCaptured) {
                hasCaptured = true
                captureAndSend(root, logMessage)
            }
        } else {
            hasCaptured = false
        }
    }

    /**
     * Captures fare and discount, then sends as one JSON.
     */
    private fun captureAndSend(root: AccessibilityNodeInfo, log: (String) -> Unit) {
        // Fare
        val fare = root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvTotalFareCost")
            ?.firstOrNull()?.text?.toString().orEmpty()
        log("Pathao -> Fare: \"$fare\"")

        // Discount
        val discount = root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvDiscountInfoTitle")
            ?.firstOrNull()?.text?.toString().orEmpty()
        log("Pathao -> Discount: \"$discount\"")

        // Build single JSON payload
        val json = JSONObject().apply {
            put("fare", fare)
            put("discount", discount)
        }
        // Send once
        if (validator.shouldSend(json, DATA_TYPE)) {
            LogSender.sendLog(json, FINISHED_URL)
            log("Pathao -> ride_finish sent")
        } else {
            log("Pathao -> Duplicate ride_finish within 10min, skipping send")
        }

    }
}
