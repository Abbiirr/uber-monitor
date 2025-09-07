
package com.example.uber_monitor.uber

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
class UberWaitingForPassengerPageHandler(
    private val context: Context
) {
    private val detector = UberPageDetector()
    private var hasCaptured = false
    private val validator = LogCacheValidator(context)

    private companion object {
        const val REQUEST_URL = "https://giglytech-data-collection-api.global.fintech23.xyz/api/v1/uber/raw-ride-request"
        private const val DATA_TYPE = "ride_request"
    }

    /**
     * Delegates capture when the page first appears and resets state when leaving.
     */
    fun handle(root: AccessibilityNodeInfo, log: (String) -> Unit) {
        log("Uber -> Waiting For Passenger page detected")
//        if (detector.isRideRequestPage(root)) {
//            if (!hasCaptured) {
//                hasCaptured = true
//                captureAndSend(root, log)
//            }
//        } else {
//            hasCaptured = false
//        }
    }

    /**
     * Captures pickup, destination, fare, distance, bonus, surge, then sends as a JSON.
     */
    private fun captureAndSend(root: AccessibilityNodeInfo, log: (String) -> Unit) {
        val pkg = BuildConfig.UBER_PKG
        // locate the request card container
        val sheet = root.findAccessibilityNodeInfosByViewId("$pkg:id/primary_touch_area")
            ?.firstOrNull()
        if (sheet == null) {
            log("Uber -> Request card not found, skipping capture")
            return
        }

        // collect all TextView nodes under the sheet
        val textViews = mutableListOf<AccessibilityNodeInfo>()
        fun collect(node: AccessibilityNodeInfo) {
            if (node.className == "android.widget.TextView") textViews += node
            for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it) }
        }
        collect(sheet)

        // regex patterns for each field
        val tripTypePattern = Regex("^Uber\\w+")
        val farePattern = Regex("^BDT\\s*\\d+\\.?\\d*")
        val etaPattern = Regex("^<\\d+ min.*away")
        val summaryPattern = Regex("\\d+ mins.*trip")
        val addressPattern = Regex("^.+, .+, .+$") // simple address with at least two commas

        // helper to find first matching text
        fun findFirst(pattern: Regex): String =
            textViews.firstOrNull { it.text?.toString()?.matches(pattern) == true }
                ?.text?.toString().orEmpty()

        val tripType = findFirst(tripTypePattern)
        val fare = findFirst(farePattern)
        val eta = findFirst(etaPattern)
        val tripSummary = findFirst(summaryPattern)

        // extract all address-like texts
        val addresses = textViews.mapNotNull { it.text?.toString() }
            .filter { it.matches(addressPattern) }
        val pickupLocation = addresses.getOrNull(0).orEmpty()
        val destinationLocation = addresses.getOrNull(1).orEmpty()

        // validate presence of all required fields
        if (tripType.isEmpty() || fare.isEmpty() || eta.isEmpty() ||
            pickupLocation.isEmpty() || tripSummary.isEmpty() || destinationLocation.isEmpty()
        ) {
            log(
                "Uber -> Missing one or more fields, skipping capture: " +
                        "tripType='$tripType', fare='$fare', eta='$eta', pickup='$pickupLocation', " +
                        "summary='$tripSummary', destination='$destinationLocation'"
            )
            return
        }

        // log extracted values
        log("Uber -> Trip Type: \"$tripType\"")
        log("Uber -> Fare: \"$fare\"")
        log("Uber -> ETA: \"$eta\"")
        log("Uber -> Pickup Location: \"$pickupLocation\"")
        log("Uber -> Trip Summary: \"$tripSummary\"")
        log("Uber -> Destination: \"$destinationLocation\"")

        // build JSON payload
        val json = JSONObject().apply {
            put("trip_type", tripType)
            put("fare", fare)
            put("eta", eta)
            put("pickup_location", pickupLocation)
            put("trip_summary", tripSummary)
            put("destination_location", destinationLocation)
        }
        // dedupe and send
        if (validator.shouldSend(json, DATA_TYPE)) {
            LogSender.sendLog(json, REQUEST_URL)
            log("Uber -> ride_request sent")
        } else {
            log("Uber -> Duplicate ride_request within 10min, skipping send")
        }
    }

    private fun extractAddress(
        root: AccessibilityNodeInfo,
        typeLabel: String,
        log: (String) -> Unit
    ): String {
        val nodes = root.findAccessibilityNodeInfosByViewId(
            "${BuildConfig.UBER_PKG}:id/tvAddressTypeLabel"
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
                        "${BuildConfig.UBER_PKG}:id/tvAddressLine"
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
            .findAccessibilityNodeInfosByViewId("${BuildConfig.UBER_PKG}:id/$viewIdSuffix")
            ?.firstOrNull()
            ?.text
            ?.toString()
            .orEmpty()
        log("Pathao -> $label: \"${if (text.isNotEmpty()) text else "not found"}\"")
        return text
    }
}
