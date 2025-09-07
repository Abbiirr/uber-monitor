// File: PathaoRideRequestPageHandler.kt
package com.example.uber_monitor.pathao

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig
import com.example.uber_monitor.network.LogSender
import com.example.uber_monitor.utils.LogCacheValidator
import org.json.JSONObject

/**
 * Handles capture logic for the Pathao Driver Ride Request page,
 * capturing the details once and sending a single JSON payload.
 */
class PathaoRideRequestPageHandler(
    private val context: Context
) {
    private val detector = PathaoPageDetector()
    private var hasCaptured = false
    private val validator = LogCacheValidator(context)

    private companion object {
        const val REQUEST_URL = "https://giglytech-data-collection-api.global.fintech23.xyz/api/v1/pathao/raw-ride-request"
        private const val DATA_TYPE = "ride_request"
    }

    /**
     * Delegates capture when the page first appears and resets state when leaving.
     */
    fun handle(root: AccessibilityNodeInfo, log: (String) -> Unit) {
        if (detector.isRideRequestPage(root)) {
            if (!hasCaptured) {
                hasCaptured = true
                captureAndSend(root, log)
            }
        } else {
            hasCaptured = false
        }
    }

    /**
     * Captures pickup, destination, fare, distance, bonus, surge, then sends as a JSON.
     */
    private fun captureAndSend(root: AccessibilityNodeInfo, log: (String) -> Unit) {
        // Pickup & Destination
        val pickup = extractAddress(root, "PICKUP", log)
        val destination = extractAddress(root, "DESTINATION", log)
        log("Pathao -> Pickup: \"$pickup\"")
        log("Pathao -> Destination: \"$destination\"")

        // Fare estimate
        val fare = extractText(root, "tvRideFare", "Fare Estimate", log)

        // Distance
        val distance = extractText(root, "tvRideDistance", "Distance", log)

        // Bonus
        val bonus = extractText(root, "tvRideBonus", "Bonus", log)

        // Surge icon presence
        val surge = root.findAccessibilityNodeInfosByViewId(
            "${BuildConfig.PATHAO_PKG}:id/ivSurge"
        ).orEmpty().isNotEmpty()
        log("Pathao -> Surge icon present: $surge")

        // Build and send JSON payload
        val json = JSONObject().apply {
            put("pickup_location", pickup)
            put("destination_location", destination)
            put("fare", fare)
            put("distance", distance)
            put("bonus", if (bonus.isNotEmpty()) bonus else JSONObject.NULL)
            put("is_surge", surge)
        }
        if (validator.shouldSend(json, DATA_TYPE)) {
            LogSender.sendLog(json, REQUEST_URL)
            log("Pathao -> ride_request sent")
        } else {
            log("Pathao -> Duplicate ride_request within 10min, skipping send")
        }
    }

    private fun extractAddress(
        root: AccessibilityNodeInfo,
        typeLabel: String,
        log: (String) -> Unit
    ): String {
        val nodes = root.findAccessibilityNodeInfosByViewId(
            "${BuildConfig.PATHAO_PKG}:id/tvAddressTypeLabel"
        ).orEmpty()

        // 1) Find the correct label node by text
        val labelNode = nodes.firstOrNull { it.text?.toString() == typeLabel }
        if (labelNode == null) {
            log("Pathao → No label found for $typeLabel")
            return ""
        }

        // 2) Get its parent and snapshot all its children
        val parent = labelNode.parent ?: return ""
        val children = (0 until parent.childCount)
            .mapNotNull { parent.getChild(it) }

        // 3) Find the label’s position, then look *after* it
        val labelIndex = children.indexOfFirst { it == labelNode }
        val addrNode = children
            .drop(labelIndex + 1)                  // only siblings after the label
            .firstOrNull {
                it.viewIdResourceName ==
                        "${BuildConfig.PATHAO_PKG}:id/tvAddressLine"
            }

        val addr = addrNode?.text?.toString().orEmpty()
        log("Pathao -> $typeLabel Address resolved to: \"$addr\"")
        return addr
    }

    private fun extractText(
        root: AccessibilityNodeInfo,
        viewIdSuffix: String,
        label: String, log: (String) -> Unit
    ): String {
        val text = root
            .findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/$viewIdSuffix")
            ?.firstOrNull()
            ?.text
            ?.toString()
            .orEmpty()
        log("Pathao -> $label: \"${if (text.isNotEmpty()) text else "not found"}\"")
        return text
    }
}
