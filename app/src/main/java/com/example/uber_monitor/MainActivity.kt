package com.example.uber_monitor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.ComponentName
import android.app.AppOpsManager
import android.view.Gravity
import android.widget.ScrollView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }

    private lateinit var statusText: TextView
    private lateinit var accessibilityButton: Button
    private lateinit var usageAccessButton: Button
    private lateinit var screenCaptureButton: Button
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a proper UI layout
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Title
        val titleText = TextView(this).apply {
            text = "Uber Monitor Service"
            textSize = 24f
            setPadding(0, 0, 0, 20)
        }

        // Status text
        statusText = TextView(this).apply {
            text = "Checking permissions..."
            textSize = 16f
            setPadding(0, 20, 0, 40)
        }

        // Accessibility button
        accessibilityButton = Button(this).apply {
            text = "Enable Accessibility Service"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 10)
            }
            setOnClickListener { promptForAccessibilityService() }
        }

        // Usage access button
        usageAccessButton = Button(this).apply {
            text = "Enable Usage Access"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 10)
            }
            setOnClickListener { promptForUsageAccess() }
        }

        // Screen capture button
        screenCaptureButton = Button(this).apply {
            text = "Grant Screen Capture Permission"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 10)
            }
            setOnClickListener { requestMediaProjection() }
        }

        // Instructions text
        val instructionsText = TextView(this).apply {
            text = """
                Instructions:
                1. Enable Accessibility Service to monitor app events
                2. Enable Usage Access to detect foreground apps
                3. Grant Screen Capture permission for screenshots
                4. Open Uber Driver app (com.ubercab.driver)
                
                The service will detect Uber Driver and capture screenshots.
            """.trimIndent()
            textSize = 14f
            setPadding(0, 40, 0, 20)
        }

        // Log display
        logTextView = TextView(this).apply {
            text = "Service Logs:\n"
            textSize = 12f
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFF0F0F0.toInt())
        }

        // Add views to layout
        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(accessibilityButton)
        layout.addView(usageAccessButton)
        layout.addView(screenCaptureButton)
        layout.addView(instructionsText)
        layout.addView(logTextView)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        checkServiceStatus()
    }

    private fun updatePermissionStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled(this, UberAccessibilityService::class.java)
        val usageAccessEnabled = isUsageAccessGranted(this)
        val screenCaptureEnabled = MediaProjectionSingleton.projectionData != null

        val statusBuilder = StringBuilder()
        statusBuilder.append("Permissions Status:\n\n")
        statusBuilder.append("✓ Accessibility: ${if (accessibilityEnabled) "Enabled ✓" else "Disabled ✗"}\n")
        statusBuilder.append("✓ Usage Access: ${if (usageAccessEnabled) "Enabled ✓" else "Disabled ✗"}\n")
        statusBuilder.append("✓ Screen Capture: ${if (screenCaptureEnabled) "Granted ✓" else "Not Granted ✗"}\n\n")

        if (accessibilityEnabled && usageAccessEnabled && screenCaptureEnabled) {
            statusBuilder.append("✓ All permissions granted! Service is ready.")
        } else {
            statusBuilder.append("⚠ Please grant all permissions to start monitoring.")
        }

        statusText.text = statusBuilder.toString()

        // Update button states
        accessibilityButton.isEnabled = !accessibilityEnabled
        accessibilityButton.text = if (accessibilityEnabled) "Accessibility Service Enabled ✓" else "Enable Accessibility Service"

        usageAccessButton.isEnabled = !usageAccessEnabled
        usageAccessButton.text = if (usageAccessEnabled) "Usage Access Enabled ✓" else "Enable Usage Access"

        screenCaptureButton.isEnabled = !screenCaptureEnabled
        screenCaptureButton.text = if (screenCaptureEnabled) "Screen Capture Granted ✓" else "Grant Screen Capture Permission"
    }

    private fun checkServiceStatus() {
        val prefs = getSharedPreferences("uber_monitor_logs", MODE_PRIVATE)
        val lastLog = prefs.getString("last_log", "No recent activity")
        val logCount = prefs.getInt("log_count", 0)

        logTextView.text = """
            Service Logs:
            Total events logged: $logCount
            Last activity: $lastLog
            
            Monitoring for:
            - com.ubercab.driver (Uber Driver)
            - com.ubercab (Uber)
            - com.pathao (Pathao)
            - com.pathao.driver (Pathao Driver)
        """.trimIndent()
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                MediaProjectionSingleton.projectionData = data
                Toast.makeText(this, "Screen capture permission granted!", Toast.LENGTH_SHORT).show()
                updatePermissionStatus()
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServicesSetting.split(":").any {
            ComponentName.unflattenFromString(it)?.flattenToString() == expectedComponentName.flattenToString()
        }
    }

    private fun isUsageAccessGranted(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun promptForAccessibilityService() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage("To monitor UI changes for Uber and Pathao, please enable the accessibility service in settings.\n\nYou'll need to find 'Uber Monitor' in the list and enable it.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptForUsageAccess() {
        AlertDialog.Builder(this)
            .setTitle("Enable Usage Access")
            .setMessage("To detect when apps are in the foreground, please allow usage access in settings.\n\nFind 'Uber Monitor' and enable the permission.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}