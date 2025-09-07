// File: PathaoTripReceiptPageHandler.kt
package com.example.uber_monitor.pathao

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig
import com.example.uber_monitor.network.LogSender
import org.json.JSONObject

/**
 * Handles capture logic for the Pathao Driver Trip Receipt page,
 * capturing the receipt data once and sending a single JSON payload.
 */
class PathaoTripReceiptPageHandler (
    private val context: Context
) {
    private val detector = PathaoPageDetector()
    private var hasCaptured = false

    private companion object {
        const val RECEIPT_URL = "https://giglytech-data-collection-api.global.fintech23.xyz/api/v1/pathao/raw-trip-receipt"
    }

    fun handle(root: AccessibilityNodeInfo, log: (String) -> Unit) {
        if (detector.isTripReceiptPage(root)) {
            if (!hasCaptured) {
                hasCaptured = true
                captureAndSend(root, log)
            }
        } else {
            hasCaptured = false
        }
    }

    private fun captureAndSend(root: AccessibilityNodeInfo, log: (String) -> Unit) {
        // 1) Static resource-ID fields
        val base = JSONObject().apply {
//            put("trip_time",   extractById(root, "tripTimeTv"))
//            put("trip_earnings", extractById(root, "tripFareTv"))
//            put("trip_id",     extractById(root, "tvTripId"))
//            put("fare_change_message", extractById(root, "tvFareChangeText"))
//            put("pathao_pays", extractById(root, "commissionValueTv"))
//            put("distance",    extractById(root, "distanceTv"))
//            put("duration",    extractById(root, "durationTv"))
//            put("ride_type",   extractById(root, "rideTypeTv"))
        }
        base.put("discount", "")
        // 2) Title→Value rows
        mapOf(
            "Fare" to "base_fare",
            "Safety Coverage Fee" to "safety_coverage_fee",
            "Customer Pays" to "fare",
            "Discount" to "discount",
            "Trip Fare" to "trip_fare"
        ).forEach { (title, key) ->
            extractRowValue(root, title)?.let { base.put(key, it) }
        }

        // 3) Regex rows (surge, commission %)
        base.put("surge_amount", extractRowValue(root, Regex("""\d+(\.\d+)?\s*x\s*Surge"""))?: "")
        base.put("surge_multiplier", extractSurgeMultiplier(root) ?: "")
        extractRowValue(root, Regex("""Pathao Net Commission\(\d+%?\)"""))?.let {
            base.put("pathao_net_commission", it)
        }

        extractRowValue(root, "Trip Earnings")?.let { earnings ->
            base.put("trip_earnings", earnings)
        }

        // 4) Addresses
        listOf("PICKUP" to "pickup_address", "DESTINATION" to "destination_address")
            .forEach { (label, key) ->
                extractAddress(root, label)?.let { base.put(key, it) }
            }

        // 5) Send it
        LogSender.sendLog(base, RECEIPT_URL)
        log("Pathao -> Trip Receipt payload sent")
    }

    private fun extractSurgeMultiplier(root: AccessibilityNodeInfo): String? {
        // 1) find the title node
        val titleNode = root.findAccessibilityNodeInfosByViewId(
            "${BuildConfig.PATHAO_PKG}:id/tvReceiptTitle"
        )?.firstOrNull { it.text?.toString()?.endsWith("Surge") == true }

        // 2) pull its text, split on "x", and trim
        return titleNode
            ?.text
            ?.toString()
            ?.substringBefore("x")
            ?.trim()
    }

    private fun extractById(root: AccessibilityNodeInfo, suffix: String): String =
        root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/$suffix")
            ?.firstOrNull()?.text?.toString().orEmpty()

    private fun extractRowValue(root: AccessibilityNodeInfo, title: String): String? =
        root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvReceiptTitle")
            ?.firstOrNull { it.text?.toString() == title }
            ?.parent
            ?.let { parent ->
                (0 until parent.childCount)
                    .mapNotNull { parent.getChild(it) }
                    .firstOrNull { it.viewIdResourceName == "${BuildConfig.PATHAO_PKG}:id/tvReceiptValue" }
                    ?.text?.toString()
            }

    private fun extractRowValue(root: AccessibilityNodeInfo, regex: Regex): String? =
        root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvReceiptTitle")
            ?.firstOrNull { it.text?.toString()?.matches(regex) == true }
            ?.let { titleNode ->
                titleNode.parent
                    ?.let { parent ->
                        (0 until parent.childCount)
                            .mapNotNull { parent.getChild(it) }
                            .firstOrNull { it.viewIdResourceName == "${BuildConfig.PATHAO_PKG}:id/tvReceiptValue" }
                            ?.text?.toString()
                    }
            }

    private fun extractAddress(root: AccessibilityNodeInfo, typeLabel: String): String? =
        root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvAddressTypeLabel")
            ?.firstOrNull { it.text?.toString() == typeLabel }
            ?.parent
            ?.let { parent ->
                (0 until parent.childCount)
                    .mapNotNull { parent.getChild(it) }
                    .firstOrNull { it.viewIdResourceName == "${BuildConfig.PATHAO_PKG}:id/tvAddressLine" }
                    ?.text?.toString()
            }
}
