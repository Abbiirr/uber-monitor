// Update File: PathaoPageHandler.kt
package com.example.uber_monitor.pathao

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Routes to specific page handlers for Pathao Driver.
 */
class PathaoPageHandler(context: Context) {
    private val detector = PathaoPageDetector()
    private val tripHandler = PathaoTripDetailsPageHandler(context)
    private val requestHandler = PathaoRideRequestPageHandler(context)
    private val startedHandler = PathaoRideStartedPageHandler(context)
    private val finishedHandler = PathaoRideFinishedPageHandler(context)
    private val tripReceiptPageHandler = PathaoTripReceiptPageHandler(context)

    fun handle(root: AccessibilityNodeInfo, logMessage: (String) -> Unit) {
        when {
            detector.isRideRequestPage(root) -> requestHandler.handle(root, logMessage)
            detector.isRideStartedPage(root) -> startedHandler.handle(root, logMessage)
            detector.isTripDetailsPage(root) -> tripHandler.handle(root, logMessage)
            detector.isRideFinishedPage(root) -> finishedHandler.handle(root, logMessage)
            detector.isTripReceiptPage(root) -> tripReceiptPageHandler.handle(root, logMessage)
            else -> logMessage("Pathao -> Other page detected")
        }
    }
}
