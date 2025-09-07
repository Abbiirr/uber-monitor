package com.example.uber_monitor.uber

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig
import com.example.uber_monitor.network.LogSender
import com.example.uber_monitor.utils.LogCacheValidator
import org.json.JSONObject

/**
 * Sends a single “collect_payment” event when the driver lands on the Collect Payment screen.
 */
class UberCollectPaymentHandler(
    private val context: Context
) {
    private var hasCaptured = false
    private val validator = LogCacheValidator(context)

    companion object {
        private const val PAYMENT_URL = "https://giglytech-data-collection-api.global.fintech23.xyz/api/v1/uber/raw-collect-payment"
        private const val DATA_TYPE    = "collect_payment"
    }

    /**
     * Call this on every AccessibilityEvent.  As soon as the Collect Payment UI
     * is visible, grabs the fare and fires one JSON payload.
     */
    fun handle(root: AccessibilityNodeInfo, log: (String) -> Unit) {
        val pkg = BuildConfig.UBER_PKG

        // 1) Detect the “Collect payment” title + the fare amount view
        val titleMatches = root.findAccessibilityNodeInfosByText("Collect payment")
            .any { it.className == "android.widget.TextView" }

        val amountMatches = root.findAccessibilityNodeInfosByViewId(
            "$pkg:id/ub__collect_cash_overview_card_amount_value"
        ).orEmpty().any { it.text?.isNotBlank() == true }

        val onCollectScreen = titleMatches && amountMatches

        // 2) If we’ve left the screen, reset and bail
        if (!onCollectScreen) {
            hasCaptured = false
            return
        }

        // 3) Only run once per appearance
        if (hasCaptured) return
        hasCaptured = true

        log("Uber → Collect Payment page detected")

        // 4) Extract the fare
        val fare = root.findAccessibilityNodeInfosByViewId(
            "$pkg:id/ub__collect_cash_overview_card_amount_value"
        ).firstOrNull()
            ?.text
            ?.toString()
            .orEmpty()

        log("Uber → Fare: \"$fare\"")

        // 5) Build and send JSON, deduped
        val payload = JSONObject().apply {
            put("event", "collect_payment")
            put("fare", fare)
        }

        if (validator.shouldSend(payload, DATA_TYPE)) {
            LogSender.sendLog(payload, PAYMENT_URL)
            log("Uber → collect_payment sent")
        } else {
            log("Uber → Duplicate collect_payment within 10min, skipping send")
        }
    }
}
