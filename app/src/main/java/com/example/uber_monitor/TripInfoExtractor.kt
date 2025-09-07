// File: TripInfoExtractor.kt
package com.example.uber_monitor

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Singleton object responsible for locating and reading the
 * trip‑details nodes in the Pathao Driver accessibility hierarchy.
 */
object TripInfoExtractor {

    /**
     * Walks the provided root node to find and return
     * the TripInfo (time, fare, and ID).
     */
    fun extract(root: AccessibilityNodeInfo): TripInfo {
        // 1. Trip time: under the trip_time_layout container
        val timeLayout = root
            .findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/trip_time_layout")
            .firstOrNull()
        val timeNode = timeLayout
            ?.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tripTimeTv")
            ?.firstOrNull()

        // 2. Trip fare: direct by resource-id
        val fareNode = root
            .findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tripFareTv")
            .firstOrNull()

        // 3. Trip ID: inside the clTripInfo container
        val infoLayout = root
            .findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/clTripInfo")
            .firstOrNull()
        val idNode = infoLayout
            ?.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvTripId")
            ?.firstOrNull()

        return TripInfo(
            tripTime = timeNode?.text?.toString(),
            tripFare = fareNode?.text?.toString(),
            tripId   = idNode  ?.text?.toString()
        )
    }
}
