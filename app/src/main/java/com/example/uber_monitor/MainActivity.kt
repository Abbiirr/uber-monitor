package com.example.uber_monitor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.ComponentName
import android.app.AppOpsManager
import android.view.Gravity
import android.widget.ScrollView

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var accessibilityButton: Button
    private lateinit var usageAccessButton: Button
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleText = TextView(this).apply {
            text = "Uber Monitor Service"
            textSize = 24f
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "Checking permissions..."
            textSize = 16f
            setPadding(0, 20, 0, 40)
        }

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

        val instructionsText = TextView(this).apply {
            text = """
                Instructions:
                1. Enable Accessibility Service to monitor app events
                2. Enable Usage Access to detect foreground apps
                3. Open Uber Driver app (com.ubercab.driver)
                
                The service will detect and log Uber Driver activity.
            """.trimIndent()
            textSize = 14f
            setPadding(0, 40, 0, 20)
        }

        logTextView = TextView(this).apply {
            text = "Service Logs:\n"
            textSize = 12f
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFFF0F0F0.toInt())
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(accessibilityButton)
        layout.addView(usageAccessButton)
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

        val statusBuilder = StringBuilder()
        statusBuilder.append("Permissions Status:\n\n")
        statusBuilder.append("✓ Accessibility: ${if (accessibilityEnabled) "Enabled ✓" else "Disabled ✗"}\n")
        statusBuilder.append("✓ Usage Access: ${if (usageAccessEnabled) "Enabled ✓" else "Disabled ✗"}\n\n")

        if (accessibilityEnabled && usageAccessEnabled) {
            statusBuilder.append("✓ All permissions granted! Service is ready.")
        } else {
            statusBuilder.append("⚠ Please grant all permissions to start monitoring.")
        }

        statusText.text = statusBuilder.toString()

        accessibilityButton.isEnabled = !accessibilityEnabled
        accessibilityButton.text = if (accessibilityEnabled) "Accessibility Service Enabled ✓" else "Enable Accessibility Service"

        usageAccessButton.isEnabled = !usageAccessEnabled
        usageAccessButton.text = if (usageAccessEnabled) "Usage Access Enabled ✓" else "Enable Usage Access"
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