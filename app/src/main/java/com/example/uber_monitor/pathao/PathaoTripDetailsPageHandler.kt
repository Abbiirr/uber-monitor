// File: PathaoTripDetailsPageHandler.kt
package com.example.uber_monitor.pathao

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig
import com.example.uber_monitor.utils.LocationHelper

/**
 * Handles capture logic for the Pathao Driver Trip Details page.
 */
class PathaoTripDetailsPageHandler(context: Context) {
    private val detector = PathaoPageDetector()
    init {
        // Initialize the LocationHelper once with the application context
        LocationHelper.init(context.applicationContext)
    }

    /**
     * If on Trip Details page, logs all trip details.
     */
    fun handle(root: AccessibilityNodeInfo, logMessage: (String) -> Unit) {
        if (!detector.isTripDetailsPage(root)) return
        logMessage("Pathao -> Trip details page detected")
        val now = System.currentTimeMillis()
        logMessage("Pathao -> Trip details opened at $now")

        // Fetch GPS via LocationHelper
        LocationHelper.getLastLocation { location ->
            if (location != null) {
                logMessage("Pathao -> GPS: ${location.latitude},${location.longitude}")
            } else {
                logMessage("Pathao -> GPS unavailable or permission missing")
            }
        }
        // Static resource-ID captures
        listOf(
            "${BuildConfig.PATHAO_PKG}:id/tripTimeTv" to "Trip Time",
            "${BuildConfig.PATHAO_PKG}:id/tripFareTv" to "Trip Earnings",
            "${BuildConfig.PATHAO_PKG}:id/tvTripId" to "Trip ID",
            "${BuildConfig.PATHAO_PKG}:id/tvFareChangeText" to "Fare Change Message",
            "${BuildConfig.PATHAO_PKG}:id/commissionValueTv" to "Pathao Pays",
            "${BuildConfig.PATHAO_PKG}:id/distanceTv" to "Distance",
            "${BuildConfig.PATHAO_PKG}:id/durationTv" to "Duration",
            "${BuildConfig.PATHAO_PKG}:id/rideTypeTv" to "Ride Type"
        ).forEach { (resId, label) ->
            val text = root.findAccessibilityNodeInfosByViewId(resId)
                ?.firstOrNull()?.text?.toString().orEmpty()
            logMessage("Pathao -> $label: \"$text\"")
        }

        // Title→Value rows
        listOf(
            "Fare" to "Base Fare",
            "Safety Coverage Fee" to "Safety Fee",
            "Customer Pays" to "Customer Pays",
            "Discount" to "Discount",
            "Trip Fare" to "Trip Fare (Fare + Surge)"
        ).forEach { (title, label) ->
            root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvReceiptTitle")
                ?.firstOrNull { it.text?.toString() == title }
                ?.parent
                ?.let { parent ->
                    (0 until parent.childCount)
                        .mapNotNull { parent.getChild(it) }
                        .firstOrNull { it.viewIdResourceName == "${BuildConfig.PATHAO_PKG}:id/tvReceiptValue" }
                        ?.text
                        ?.toString()
                        ?.let { logMessage("Pathao -> $label: \"$it\"") }
                }
        }

        // Dynamic regex captures
        captureByRegex(root, Regex("""\\d+(\\.\\d+)?\\s*x\\s*Surge"""), logMessage)
        captureByRegex(root, Regex("""Pathao Net Commission\\(\\d+%?\\)"""), logMessage)

        // Addresses on trip details screen
        root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvAddressTypeLabel")
            ?.forEach { labelNode ->
                val type = labelNode.text?.toString().orEmpty()
                if (type == "PICKUP" || type == "DESTINATION") {
                    labelNode.parent?.let { parent ->
                        (0 until parent.childCount)
                            .mapNotNull { parent.getChild(it) }
                            .firstOrNull { it.viewIdResourceName == "${BuildConfig.PATHAO_PKG}:id/tvAddressLine" }
                            ?.text
                            ?.toString()
                            ?.let { logMessage("Pathao -> $type Address: \"$it\"") }
                    }
                }
            }
    }

    private fun captureByRegex(
        root: AccessibilityNodeInfo,
        regex: Regex,
        logMessage: (String) -> Unit
    ) {
        root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvReceiptTitle")
            ?.filter { it.text?.toString()?.matches(regex) == true }
            ?.forEach { titleNode ->
                val parent = titleNode.parent ?: return@forEach
                (0 until parent.childCount)
                    .mapNotNull { parent.getChild(it) }
                    .firstOrNull { it.viewIdResourceName == "${BuildConfig.PATHAO_PKG}:id/tvReceiptValue" }
                    ?.text
                    ?.toString()
                    ?.let { logMessage("Pathao -> ${titleNode.text} → \"$it\"") }
            }
    }
}