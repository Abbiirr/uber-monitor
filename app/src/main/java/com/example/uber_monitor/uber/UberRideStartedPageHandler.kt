package com.example.uber_monitor.uber

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig
import com.example.uber_monitor.network.LogSender
import com.example.uber_monitor.utils.LogCacheValidator
import org.json.JSONObject

/**
 * Handles capture logic for the Uber Driver Ride Started page,
 * sending a single enriched log once per appearance, including
 * current location, destination, and (optionally) maneuver text.
 */
class UberRideStartedPageHandler(
    private val context: Context
) {
    private val detector = UberPageDetector()
    private var hasCaptured = false
    private val validator = LogCacheValidator(context)

    private companion object {
        const val STARTED_URL = "https://giglytech-data-collection-api.global.fintech23.xyz/api/v1/uber/raw-ride-started"
        private const val DATA_TYPE = "ride_start"
    }

    /**
     * When the Ride Started page first appears, send one JSON payload.
     */
    fun handle(root: AccessibilityNodeInfo, logMessage: (String) -> Unit) {
        if (detector.isRideStartedPage(root)) {
            if (!hasCaptured) {
                hasCaptured = true
                logMessage("Uber -> Ride started page detected")

                val pkg = BuildConfig.UBER_PKG

                // 1) Extract the “current location” (POI text)
                val currLocNode = root.findAccessibilityNodeInfosByViewId(
                    "$pkg:id/ub__nav_address_view_poi_text"
                ).orEmpty().firstOrNull()
                val currentLocation = currLocNode?.text?.toString().orEmpty()
                logMessage("Uber -> Current location: \"$currentLocation\"")

                // 2) Extract the “destination” address
                val destAddrNode = root.findAccessibilityNodeInfosByViewId(
                    "$pkg:id/ub__nav_address_view_address_text"
                ).orEmpty().firstOrNull()
                val destinationAddress = destAddrNode?.text?.toString().orEmpty()
                logMessage("Uber -> Destination address: \"$destinationAddress\"")

                // 3) Optionally keep maneuver text too
                val maneuverNode = root.findAccessibilityNodeInfosByViewId(
                    "$pkg:id/ub__nav_maneuver_text"
                ).orEmpty().firstOrNull()
                val maneuver = maneuverNode?.text?.toString().orEmpty()
                if (maneuver.isNotEmpty()) {
                    logMessage("Uber -> Maneuver: \"$maneuver\"")
                }

                // 4) Build the JSON
                val payload = JSONObject().apply {
                    put("event", "ride_started")
                    put("current_location", currentLocation)
                    put("destination_location", destinationAddress)
                    if (maneuver.isNotEmpty()) {
                        put("maneuver_text", maneuver)
                    }
                }

                // 5) Dedupe + send
                if (validator.shouldSend(payload, DATA_TYPE)) {
                    LogSender.sendLog(payload, STARTED_URL)
                    logMessage("Uber -> ride_start sent")
                } else {
                    logMessage("Uber -> Duplicate ride_start within 10min, skipping send")
                }
            }
        } else {
            // reset so we fire next time the page re‐appears
            hasCaptured = false
        }
    }
}
