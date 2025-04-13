package com.example.uber_monitor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.ComponentName
import com.example.uber_monitor.UberAccessibilityService
import android.app.AppOpsManager



class MainActivity : AppCompatActivity() {
    // Utility function to check if the service is enabled.
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
            .setMessage("To monitor UI changes for Uber, please enable the accessibility service in settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                // Launch the accessibility settings
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun promptForUsageAccess() {
        AlertDialog.Builder(this)
            .setTitle("Enable Usage Access")
            .setMessage("To monitor the foreground app, please allow usage access in settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Replace 'RideAccessibilityService::class.java' with your service class.
        if (!isAccessibilityServiceEnabled(this, UberAccessibilityService::class.java)) {
            promptForAccessibilityService()
        }
        if (!isUsageAccessGranted(this)) {
            promptForUsageAccess()
        }
    }
}
