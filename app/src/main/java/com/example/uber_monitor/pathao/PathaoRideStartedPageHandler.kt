// File: PathaoRideStartedPageHandler.kt
package com.example.uber_monitor.pathao

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig
import com.example.uber_monitor.network.LogSender
import com.example.uber_monitor.utils.LogCacheValidator
import org.json.JSONObject

/**
 * Handles capture logic for the Pathao Driver Ride Started page,
 * sending a single enriched log once per appearance, including destination.
 */
class PathaoRideStartedPageHandler(
    private val context: Context
) {
    private val detector = PathaoPageDetector()
    private var hasCaptured = false
    private val validator = LogCacheValidator(context)

    private companion object {
        const val STARTED_URL = "https://giglytech-data-collection-api.global.fintech23.xyz/api/v1/pathao/raw-ride-started"
        private const val DATA_TYPE = "ride_start"
    }

    /**
     * When the Ride Started page first appears, send one JSON payload.
     */
    fun handle(root: AccessibilityNodeInfo, logMessage: (String) -> Unit) {
        if (detector.isRideStartedPage(root)) {
            if (!hasCaptured) {
                hasCaptured = true

                // 1) Local debug log
                logMessage("Pathao -> Ride started page detected")

                // 2) Extract destination location
                val destination = root
                    .findAccessibilityNodeInfosByViewId(
                        "${BuildConfig.PATHAO_PKG}:id/tvAddressLine"
                    )
                    ?.firstOrNull()
                    ?.text
                    ?.toString()
                    .orEmpty()
                logMessage("Pathao -> Destination: \"$destination\"")

                // 3) Build base JSON and include destination
                val baseJson = JSONObject().apply {
                    put("event", "ride_started")
                    put("destination_location", destination)
                }

                // 4) Delegate enrichment (timestamp, coords, device_id) + send
                // Use LogCacheValidator to avoid duplicates
                if (validator.shouldSend(baseJson, DATA_TYPE)) {
                    LogSender.sendLog(baseJson, STARTED_URL)
                    logMessage("Pathao -> ride_start sent")
                } else {
                    logMessage("Pathao -> Duplicate ride_start within 10min, skipping send")
                }
            }
        } else {
            // reset for next appearance
            hasCaptured = false
        }
    }
}
