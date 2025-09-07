package com.example.uber_monitor

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.network.LogSender
import com.example.uber_monitor.pathao.PathaoPageHandler

class PathaoAccessibilityService : BaseAccessibilityService() {
    override val logTag = "PathaoServiceLogger"
    override val logFileName = "pathao_driver_logs.txt"
    private lateinit var pageHandler: PathaoPageHandler
    override val elementsToCapture = listOf(
        "${BuildConfig.PATHAO_PKG}:id/tripTimeTv" to "Trip Time",
        "${BuildConfig.PATHAO_PKG}:id/tripFareTv" to "Trip Earnings",
        "${BuildConfig.PATHAO_PKG}:id/tvTripId" to "Trip ID",
        // Fare change
        "${BuildConfig.PATHAO_PKG}:id/tvFareChangeText" to "Fare Change Message",
        // Distance & duration
        "${BuildConfig.PATHAO_PKG}:id/distanceTv" to "Distance",
        "${BuildConfig.PATHAO_PKG}:id/durationTv" to "Duration",
        // Commission fields
        "${BuildConfig.PATHAO_PKG}:id/commissionValueTv" to "Pathao Will Pay",
        // Trip identifiers
        "${BuildConfig.PATHAO_PKG}:id/tripIdTv" to "Trip ID",
        // Ride type
        "${BuildConfig.PATHAO_PKG}:id/rideTypeTv" to "Ride Type",
        // Address labels and lines
        "${BuildConfig.PATHAO_PKG}:id/tvAddressTypeLabel" to "Address Type Label",
        "${BuildConfig.PATHAO_PKG}:id/tvAddressLine" to "Address Line"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        // ‘this’ is a valid Context
        LogSender.init(this)
        pageHandler = PathaoPageHandler(this)
    }

    /**
     * Captures each element by resourceId and logs its text
     */
    override fun captureAllElements(root: AccessibilityNodeInfo) {
        val pkg = root.packageName?.toString()
        if (pkg != "${BuildConfig.PATHAO_PKG}") return
        pageHandler.handle(root, ::logMessage)
    }

}
