
package com.example.uber_monitor.uber

import android.view.accessibility.AccessibilityNodeInfo
import com.example.uber_monitor.BuildConfig

/**
 * Detects if the current Pathao Driver screen is the Trip Details page.
 */
class UberPageDetector {

    fun isRideRequestPage(root: AccessibilityNodeInfo): Boolean {
        val pkg = BuildConfig.UBER_PKG

        // 1) the overall sliding panel
        val hasContainer = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/container")
            ?.isNotEmpty() == true

        // 2) inside it, the "primary touch area" which holds the card
        val hasPrimaryTouch = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/primary_touch_area")
            ?.isNotEmpty() == true

        // 3) and finally the "Accept" button itself
        val hasAcceptButton = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/accept_button")
            ?.isNotEmpty() == true

        return hasContainer && (hasPrimaryTouch || hasAcceptButton)
    }

    fun isWaitingForPassengerPage(root: AccessibilityNodeInfo): Boolean {
        val pkg = BuildConfig.UBER_PKG

        // 1) the full-screen "on_job_view" container appears only once you're on a trip
        val onJobViewPresent = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/on_job_view")
            ?.isNotEmpty() == true

        // 2) the status label text ("Picking up …") within the status_assistant bottom sheet
        val statusLabelPresent = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/status_label_leading")
            .orEmpty()
            .any { it.text?.toString()?.startsWith("Picking up") == true }

        return onJobViewPresent && statusLabelPresent
    }

    fun isRideStartedPage(root: AccessibilityNodeInfo): Boolean {
        val pkg = BuildConfig.UBER_PKG

        // 1) Ensure we’re in the on-trip UI
        val onJobViewPresent = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/on_job_view")
            .orEmpty()
            .isNotEmpty()

        // 2a) Look for the bottom-sheet label starting “Dropping off”
        val droppingOffLabel = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/status_label_leading")
            .orEmpty()
            .any { it.text?.toString()?.startsWith("Dropping off") == true }

        // 2b) (Optional) The nav-address POI view only appears once you’re en route to drop-off
        val poiViewPresent = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/ub__nav_address_view_poi_text")
            .orEmpty()
            .isNotEmpty()

        // Return true if we’ve got the on-trip container + the dropping-off indicator
        return onJobViewPresent && (droppingOffLabel || poiViewPresent)
    }

    fun isCollectPaymentPage(root: AccessibilityNodeInfo): Boolean {
        val pkg = BuildConfig.UBER_PKG

        // 1) Grab the toolbar container (LinearLayout)
        val toolbarLayouts = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/ub__collect_cash_overview_toolbar")
            .orEmpty()

        // 2) For each container, see if any descendant has text “Collect payment”
        val hasTitle = toolbarLayouts.any { layout ->
            layout.findAccessibilityNodeInfosByText("Collect payment").orEmpty()
                .any { it.className == "android.widget.TextView" }
        }

        // 3) Look for the amount-value TextView by its resource-ID
        val hasAmount = root
            .findAccessibilityNodeInfosByViewId("$pkg:id/ub__collect_cash_overview_card_amount_value")
            .orEmpty()
            .isNotEmpty()

        return hasTitle && hasAmount
    }


}
