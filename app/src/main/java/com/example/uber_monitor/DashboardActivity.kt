package com.example.uber_monitor

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.uber_monitor.databinding.ActivityDashboardBinding
import com.example.uber_monitor.databinding.NavHeaderBinding
import com.google.android.material.navigation.NavigationView
import java.text.SimpleDateFormat
import java.util.*

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
            popupView.findViewById<TextView>(R.id.popup_close).setOnClickListener {
                popupWindow.dismiss()
            }

            popupWindow.elevation = 10f
            popupWindow.showAsDropDown(binding.tvUserAvatar, 0, 0)
        }
        binding.tvUserAvatar.text = userName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
    }

    private fun setupDashboard() {
        // Setup recent activities
        binding.rvRecentActivity.apply {
            val adapter = RecentActivityAdapter(recentActivities)
            this.adapter = adapter
        }
    }

    private fun updateStats() {
        // Load stats from SharedPreferences
        val statsPrefs = getSharedPreferences("uber_monitor_stats", MODE_PRIVATE)

        // Update UI with stats
        binding.apply {
            // Requests received (sample data)
            tvRequestsReceived.text = "24"
            tvRequestsSubtext.text = "New requests today"
            tvRequestsChange.text = "+12%"

            // Rides accepted
            val acceptedRides = statsPrefs.getInt("accepted_rides", 18)
            tvRidesAccepted.text = acceptedRides.toString()
            tvAcceptedSubtext.text = "Out of 24 requests"
            tvAcceptedChange.text = "+8%"

            // Rides finished
            val totalRides = statsPrefs.getInt("total_rides", 15)
            tvRidesFinished.text = totalRides.toString()
            tvFinishedSubtext.text = "Successfully completed"

            // Active rides
            tvActiveRides.text = "3"
            tvActiveSubtext.text = "Currently in progress"

            // Time driven
            tvTimeDriven.text = "6h 24m"
            tvTimeSubtext.text = "Today's driving time"

            // Earnings
            val earnings = statsPrefs.getFloat("total_earnings", 85.30f)
            tvTodayEarnings.text = String.format("$%.2f", earnings)
            tvEarningsSubtext.text = "From completed rides"
            tvEarningsChange.text = "+15%"

            // Total earned
            tvTotalEarned.text = "$142.50"
            tvTotalSubtext.text = "All time earnings"

            // Rating
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
}