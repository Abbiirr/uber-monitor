
package com.example.uber_monitor.uber

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Routes to specific page handlers for Pathao Driver.
 */
class UberPageHandler(context: Context) {
    private val detector = UberPageDetector()
    private val requestHandler = UberRideRequestPageHandler(context)
    private val waitingForPassengerPageHandler = UberWaitingForPassengerPageHandler(context)
    private val startedHandler = UberRideStartedPageHandler(context)
    private val collectPaymentHandler = UberCollectPaymentHandler(context)


    fun handle(root: AccessibilityNodeInfo, logMessage: (String) -> Unit) {
        when {
            detector.isRideRequestPage(root) -> requestHandler.handle(root, logMessage)
            detector.isWaitingForPassengerPage(root) -> waitingForPassengerPageHandler.handle(root, logMessage)
            detector.isRideStartedPage(root) -> startedHandler.handle(root, logMessage)
            detector.isCollectPaymentPage(root) -> collectPaymentHandler.handle(root, logMessage)
            else -> logMessage("Uber -> Other page detected")
        }
    }
}
