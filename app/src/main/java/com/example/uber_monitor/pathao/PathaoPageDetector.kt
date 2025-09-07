// File: PathaoPageDetector.kt
package com.example.uber_monitor.pathao

import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig

/**
 * Detects if the current Pathao Driver screen is the Trip Details page.
 */
class PathaoPageDetector {
    /**
     * Returns true when the Trip Time view is present on screen.
     */
    fun isTripDetailsPage(root: AccessibilityNodeInfo): Boolean =
        root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tripTimeTv")
            ?.isNotEmpty() == true

    fun isRideRequestPage(root: AccessibilityNodeInfo): Boolean {
        return listOf(
            "${BuildConfig.PATHAO_PKG}:id/circularProgressBar",
            "${BuildConfig.PATHAO_PKG}:id/statueTv",
            "${BuildConfig.PATHAO_PKG}:id/seekbar",
            "${BuildConfig.PATHAO_PKG}:id/layoutOngoingRideInfoSheet"
        ).all { id ->
            root.findAccessibilityNodeInfosByViewId(id)?.isNotEmpty() == true
        }
    }

    fun isRideStartedPage(root: AccessibilityNodeInfo): Boolean {
        val endRideNodes = root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/statueTv") ?: emptyList()
        val hasEndRide = endRideNodes.any { it.text?.toString() == "END RIDE" }

        val toolbarNodes = root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/toolbarTitleText") ?: emptyList()
        val hasToolbar = toolbarNodes.any { it.text?.toString() == "Go to Destination" }

        return hasEndRide && hasToolbar
    }



    fun isRideFinishedPage(root: AccessibilityNodeInfo): Boolean {
        // 1) check for "Ride Receipt" text
        if (root.findAccessibilityNodeInfosByText("Ride Receipt")
                ?.isNotEmpty() == true
        ) return true

        // 2) check for total fare cost view
        if (root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/tvTotalFareCost")
                ?.isNotEmpty() == true
        ) return true

        // 3) check for submit button
        if (root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/submitTv")
                ?.isNotEmpty() == true
        ) return true

        return false
    }
    fun isTripReceiptPage(root: AccessibilityNodeInfo): Boolean {
        val outside = root.findAccessibilityNodeInfosByViewId("${BuildConfig.PATHAO_PKG}:id/touch_outside")
            ?.isNotEmpty() == true
        val title = root.findAccessibilityNodeInfosByText("Receipt Details")
            ?.isNotEmpty() == true
        return outside && title
    }

}
