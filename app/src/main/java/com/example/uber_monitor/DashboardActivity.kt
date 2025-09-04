package com.example.uber_monitor

import android.os.Bundle
import android.view.View
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.uber_monitor.databinding.ActivityDashboardBinding
import com.example.uber_monitor.databinding.NavHeaderBinding
import com.google.android.material.navigation.NavigationView
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var drawerLayout: DrawerLayout

    // Sample data - replace with actual data source
    private val recentActivities = listOf(
        RideActivity("Completed ride to Downtown", "2:45 PM", "$24.50"),
        RideActivity("Started ride from Airport", "1:30 PM", "$18.75"),
        RideActivity("Accepted new ride request", "12:15 PM", null),
        RideActivity("Completed ride to Mall", "11:45 AM", "$16.25")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer()
        setupDashboard()
        updateStats()
    }

    private fun setupDrawer() {
        drawerLayout = binding.drawerLayout

        // Setup navigation header
        val navView = binding.navView
        val headerView = navView.getHeaderView(0)
        val navBinding = NavHeaderBinding.bind(headerView)

        // Load user data
        val prefs = getSharedPreferences("uber_monitor_user", MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Driver") ?: "Driver"
        val userPhone = prefs.getString("user_phone", "+1 (555) 123-4567") ?: ""




        navBinding.tvUserName.text = userName
        navBinding.tvUserPhone.text = userPhone
        navBinding.tvUserInitials.text = userName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()

        // Handle menu button
        binding.btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Handle navigation items
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    // Handle logout
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
        binding.tvUserAvatar.setOnClickListener {
            val popupView = layoutInflater.inflate(R.layout.popup_user_profile, null)
            val popupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )

            popupView.findViewById<TextView>(R.id.popup_user_name).text = userName
            popupView.findViewById<TextView>(R.id.popup_user_phone).text = userPhone

            popupWindow.elevation = 10f
            popupWindow.showAsDropDown(binding.tvUserAvatar, 0, 0)
        }
        binding.tvUserAvatar.setOnClickListener {
            val popupView = layoutInflater.inflate(R.layout.popup_user_profile, null)
            val popupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            )

            popupView.findViewById<TextView>(R.id.popup_user_name).text = userName
            popupView.findViewById<TextView>(R.id.popup_user_phone).text = userPhone

            popupWindow.elevation = 10f
            popupWindow.isOutsideTouchable = true
            popupWindow.isFocusable = true
            popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            popupWindow.showAsDropDown(binding.tvUserAvatar, -100, 10)
        }
    }

    private fun setupDashboard() {
        // Setup recent activities
        binding.rvRecentActivity.apply {
            val adapter = RecentActivityAdapter(recentActivities)
            this.adapter = adapter
        }
    }

    private fun updateStats() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val url = URL("https://your-api-endpoint.com/dashboard/stats")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().readText()
                    } else null
                }

                response?.let {
                    val json = JSONObject(it)
                    binding.apply {
                        tvRequestsReceived.text = json.optString("requests_received", "24")
                        tvRidesAccepted.text = json.optString("rides_accepted", "18")
                        tvRidesFinished.text = json.optString("rides_finished", "15")
                        tvActiveRides.text = json.optString("active_rides", "3")
                        tvTimeDriven.text = json.optString("time_driven", "6h 24m")
                        tvTodayEarnings.text = "$${json.optDouble("today_earnings", 85.30)}"
                        tvTotalEarned.text = "$${json.optDouble("total_earned", 142.50)}"
                        tvRating.text = json.optString("rating", "4.8")
                    }
                } ?: loadHardcodedData()

            } catch (e: Exception) {
                loadHardcodedData()
            }
        }
    }

    private fun loadHardcodedData() {
        binding.apply {
            tvRequestsReceived.text = "24"
            tvRequestsSubtext.text = "New requests today"
            tvRequestsChange.text = "+12%"
            tvRidesAccepted.text = "18"
            tvAcceptedSubtext.text = "Out of 24 requests"
            tvAcceptedChange.text = "+8%"
            tvRidesFinished.text = "15"
            tvFinishedSubtext.text = "Successfully completed"
            tvActiveRides.text = "3"
            tvActiveSubtext.text = "Currently in progress"
            tvTimeDriven.text = "6h 24m"
            tvTimeSubtext.text = "Today's driving time"
            tvTodayEarnings.text = "$85.30"
            tvEarningsSubtext.text = "From completed rides"
            tvEarningsChange.text = "+15%"
            tvTotalEarned.text = "$142.50"
            tvTotalSubtext.text = "All time earnings"
            tvRating.text = "4.8"
            tvRatingSubtext.text = "Driver rating"
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

    data class RideActivity(
        val description: String,
        val time: String,
        val amount: String?
    )

    override fun onDestroy() {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        super.onDestroy()
        scope.cancel()
    }
}