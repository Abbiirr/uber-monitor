package com.example.uber_monitor

import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.uber_monitor.databinding.ActivityDashboardBinding
import com.example.uber_monitor.databinding.NavHeaderBinding
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import android.widget.Toast

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var drawerLayout: DrawerLayout
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()
        setupDrawer()
        updateStats()
    }

    private fun initializeViews() {
        // Initialize all stat views with default values
        binding.apply {
            tvPathaoRequests.text = "0"
            tvPathaoTripsFinished.text = "0"
            tvUberRequests.text = "0"
            tvUberTripsFinished.text = "0"
            tvPathaoGrowth.text = ""
            tvUberGrowth.text = ""
        }
    }

    private fun setupDrawer() {
        drawerLayout = binding.drawerLayout

        // Setup navigation header
        val navView = binding.navView
        val headerView = navView.getHeaderView(0)
        val navBinding = NavHeaderBinding.bind(headerView)

        // Load user data
        val prefs = getSharedPreferences("uber_monitor_user", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "") ?: ""
        val userPhone = prefs.getString("user_phone", "") ?: ""

        // Update navigation header
        navBinding.tvUserName.text = if (userName.isNotEmpty()) userName else "User"
        navBinding.tvUserPhone.text = userPhone

        val initials = getInitialsFromName(userName)
        navBinding.tvUserInitials.text = initials

        // Update avatar initials in toolbar
        binding.tvUserAvatar.text = initials

        // Handle menu button
        binding.btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Handle navigation items
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Setup user avatar popup
        binding.tvUserAvatar.setOnClickListener {
            val popupView = layoutInflater.inflate(R.layout.popup_user_profile, null)
            val popupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )

            popupView.findViewById<TextView>(R.id.popup_user_name).text =
                if (userName.isNotEmpty()) userName else "User"
            popupView.findViewById<TextView>(R.id.popup_user_phone).text = userPhone

            popupWindow.elevation = 10f
            popupWindow.isOutsideTouchable = true
            popupWindow.isFocusable = true
            popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            popupWindow.showAsDropDown(binding.tvUserAvatar, -100, 10)
        }
    }

    private fun getInitialsFromName(name: String): String {
        return if (name.isNotEmpty()) {
            name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
        } else {
            "U"
        }
    }

    private fun updateStats() {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiHelper.makeAuthenticatedRequest(
                        this@DashboardActivity,
                        "https://giglytech-user-service-api.global.fintech23.xyz/api/v1/user/data-collector/summary",
                        "POST",
                        JSONObject() // Empty body for POST
                    )
                }

                response?.let {
                    val json = JSONObject(it)
                    if (json.getString("responseCode") == "S100000") {
                        val data = json.getJSONArray("data")

                        for (i in 0 until data.length()) {
                            val platform = data.getJSONObject(i)
                            val platformName = platform.getString("platform")
                            val totalRequests = platform.getInt("totalRequests")
                            val totalTripsFinished = platform.getInt("totalTripsFinished")

                            // Calculate growth percentage if available
                            val growth = platform.optDouble("growth", 0.0)

                            when (platformName) {
                                "Pathao" -> {
                                    binding.tvPathaoRequests.text = totalRequests.toString()
                                    binding.tvPathaoTripsFinished.text = totalTripsFinished.toString()
                                    if (growth != 0.0) {
                                        binding.tvPathaoGrowth.text = String.format("%+.0f%%", growth)
                                        binding.tvPathaoGrowth.setTextColor(
                                            if (growth > 0) getColor(R.color.green) else getColor(R.color.red)
                                        )
                                    }
                                }
                                "Uber" -> {
                                    binding.tvUberRequests.text = totalRequests.toString()
                                    binding.tvUberTripsFinished.text = totalTripsFinished.toString()
                                    if (growth != 0.0) {
                                        binding.tvUberGrowth.text = String.format("%+.0f%%", growth)
                                        binding.tvUberGrowth.setTextColor(
                                            if (growth > 0) getColor(R.color.green) else getColor(R.color.red)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        setDefaultValues()
                    }
                } ?: run {
                    // ApiHelper already handles navigation to registration on auth failure
                    // Just set default values without showing toast
                    setDefaultValues()
                }

            } catch (e: Exception) {
                Log.e("DashboardActivity", "Failed to fetch stats", e)
                setDefaultValues()
            }
        }
    }

    private fun setDefaultValues() {
        binding.apply {
            tvPathaoRequests.text = "0"
            tvPathaoTripsFinished.text = "0"
            tvUberRequests.text = "0"
            tvUberTripsFinished.text = "0"
            tvPathaoGrowth.text = ""
            tvUberGrowth.text = ""
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}