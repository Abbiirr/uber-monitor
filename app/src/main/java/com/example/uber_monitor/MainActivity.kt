package com.example.uber_monitor

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_REQUEST_CODE = 1001
    }

    private lateinit var statusText: TextView
    private lateinit var permissionButtons: Map<String, Button>
    private lateinit var continueButton: Button
    private lateinit var progressBar: ProgressBar

    private var hasShownSuccessMessage = false

    // Permission configuration
    private val requiredPermissions = listOf(
        PermissionConfig("ride_accessibility", "Ride Monitoring", PermissionType.ACCESSIBILITY_RIDE),
        PermissionConfig("usage_access", "App Detection", PermissionType.USAGE_ACCESS),
        PermissionConfig("location", "Location Tracking", PermissionType.LOCATION)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkAllPermissions() && isUserAuthenticated()) {
            navigateToNextScreen()
            return
        }

        setupUI()
    }

    private fun setupUI() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleText = TextView(this).apply {
            text = "Ride Monitor Setup"
            textSize = 24f
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "Grant permissions to continue"
            textSize = 16f
            setPadding(0, 20, 0, 40)
        }

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 20, 0, 20)
            }
        }

        // Create permission buttons
        val buttons = mutableMapOf<String, Button>()
        requiredPermissions.forEach { config ->
            buttons[config.id] = Button(this).apply {
                text = "Enable ${config.displayName}"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 10, 0, 10)
                }
                setOnClickListener { requestPermission(config) }
            }
        }
        permissionButtons = buttons

        continueButton = Button(this).apply {
            text = "Continue ✓"
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 30, 0, 10)
            }
            setOnClickListener { navigateToNextScreen() }
        }

        // Add all views
        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(progressBar)
        permissionButtons.values.forEach { layout.addView(it) }
        layout.addView(continueButton)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()

        if (checkAllPermissions()) {
            if (isUserAuthenticated()) {
                handleAllPermissionsGranted()
            } else {
                navigateToNextScreen()
            }
        }
    }

    private fun checkAllPermissions(): Boolean {
        return requiredPermissions.all { checkPermission(it.type) }
    }

    private fun checkPermission(type: PermissionType): Boolean {
        return when (type) {
            PermissionType.ACCESSIBILITY_RIDE ->
                isAccessibilityServiceEnabled(RideMonitorAccessibilityService::class.java)
            PermissionType.USAGE_ACCESS ->
                isUsageAccessGranted()
            PermissionType.LOCATION ->
                hasLocationPermission()
        }
    }

    private fun requestPermission(config: PermissionConfig) {
        when (config.type) {
            PermissionType.ACCESSIBILITY_RIDE-> {
                AlertDialog.Builder(this)
                    .setTitle("Enable ${config.displayName}")
                    .setMessage("Enable accessibility service to monitor ${config.displayName.substringBefore(" ")} app.")
                    .setPositiveButton("Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .show()
            }
            PermissionType.USAGE_ACCESS -> {
                AlertDialog.Builder(this)
                    .setTitle("Enable Usage Access")
                    .setMessage("Allow app usage tracking to detect active apps.")
                    .setPositiveButton("Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    .show()
            }
            PermissionType.LOCATION -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_REQUEST_CODE
                )
            }
        }
    }

    private fun updatePermissionUI() {
        val statuses = requiredPermissions.map { config ->
            val granted = checkPermission(config.type)
            permissionButtons[config.id]?.visibility = if (granted) View.GONE else View.VISIBLE
            "${config.displayName}: ${if (granted) "✓" else "✗"}"
        }

        val allGranted = checkAllPermissions()
        statusText.text = if (allGranted) {
            "All permissions granted!"
        } else {
            statuses.joinToString("\n")
        }

        continueButton.visibility = if (allGranted) View.VISIBLE else View.GONE
    }

    private fun handleAllPermissionsGranted() {
        if (!hasShownSuccessMessage) {
            hasShownSuccessMessage = true
            progressBar.visibility = View.VISIBLE
            Toast.makeText(this, "Setup complete!", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                startMonitorService()
                navigateToNextScreen()
            }, 1000)
        }
    }

    private fun navigateToNextScreen() {
        val targetActivity = if (isUserAuthenticated()) {
            DashboardActivity::class.java
        } else {
            RegistrationActivity::class.java
        }

        startActivity(Intent(this, targetActivity))
        finish()
    }

    private fun isUserAuthenticated(): Boolean {
        val userPrefs = getSharedPreferences("uber_monitor_user", MODE_PRIVATE)
        val authPrefs = getSharedPreferences("uber_monitor_auth", MODE_PRIVATE)
        return userPrefs.getBoolean("registered", false) &&
                !authPrefs.getString("access_token", "").isNullOrEmpty()
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitorService::class.java).apply {
            val userPrefs = getSharedPreferences("uber_monitor_user", MODE_PRIVATE)
            putExtra("user_name", userPrefs.getString("user_name", ""))
            putExtra("user_phone", userPrefs.getString("user_phone", ""))
        }
        ContextCompat.startForegroundService(this, intent)
    }

    // Permission check helpers
    private fun isAccessibilityServiceEnabled(service: Class<*>): Boolean {
        val expected = ComponentName(this, service)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            ComponentName.unflattenFromString(it)?.flattenToString() == expected.flattenToString()
        }
    }

    private fun isUsageAccessGranted(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST_CODE) {
            updatePermissionUI()
        }
    }

    // Data classes
    data class PermissionConfig(
        val id: String,
        val displayName: String,
        val type: PermissionType
    )

    enum class PermissionType {
        ACCESSIBILITY_RIDE,
        USAGE_ACCESS,
        LOCATION
    }
}