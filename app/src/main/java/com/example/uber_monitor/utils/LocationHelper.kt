// File: LocationHelper.kt
package com.example.uber_monitor.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

/**
 * Singleton helper to fetch device GPS coordinates.
 * Initialize in Application or Service via init(context).
 * Call getLastLocation or startLocationUpdates as needed.
 */
object LocationHelper {
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var appContext: Context

    /**
     * Initialize the helper. Must be called before other methods.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    }

    /**
     * Fetch the last known location once. Returns null if unavailable or permission missing.
     * @param callback invoked with Location or null
     */
    @SuppressLint("MissingPermission")
    fun getLastLocation(callback: (Location?) -> Unit) {
        if (!checkPermission()) {
            callback(null)
            return
        }
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                callback(location)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    /**
     * Start continuous location updates. Caller must stop updates by calling stopLocationUpdates().
     * @param updateIntervalMs desired update interval in milliseconds
     * @param callback invoked on each location update
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        updateIntervalMs: Long,
        callback: (Location) -> Unit
    ) {
        if (!checkPermission()) return
        val request = LocationRequest.create().apply {
            interval = updateIntervalMs
            fastestInterval = updateIntervalMs / 2
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        fusedClient.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { callback(it) }
            }
        }, appContext.mainLooper)
    }

    /**
     * Stop ongoing location updates.
     */
    fun stopLocationUpdates(callback: LocationCallback) {
        fusedClient.removeLocationUpdates(callback)
    }

    /**
     * Check if either fine or coarse location permission is granted.
     */
    private fun checkPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
