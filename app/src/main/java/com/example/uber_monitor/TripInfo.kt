// File: TripInfo.kt
package com.example.uber_monitor

/**
 * Holds the key trip details extracted from the Pathao Driver UI.
 */
data class TripInfo(
    val tripTime: String?,
    val tripFare: String?,
    val tripId: String?
)
