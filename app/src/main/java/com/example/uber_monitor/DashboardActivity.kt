package com.example.uber_monitor

import android.os.Bundle
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
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var drawerLayout: DrawerLayout
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer()
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

        // Update avatar initials
        binding.tvUserAvatar.text = userName.split(" ")
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

            popupView.findViewById<TextView>(R.id.popup_user_name).text = userName
            popupView.findViewById<TextView>(R.id.popup_user_phone).text = userPhone

            popupWindow.elevation = 10f
            popupWindow.isOutsideTouchable = true
            popupWindow.isFocusable = true
            popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            popupWindow.showAsDropDown(binding.tvUserAvatar, -100, 10)
        }
    }

    private fun updateStats() {
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
                    val data = json.getJSONArray("data")

                    for (i in 0 until data.length()) {
                        val platform = data.getJSONObject(i)
                        val platformName = platform.getString("platform")
                        val totalRequests = platform.getInt("totalRequests")
                        val totalTripsFinished = platform.getInt("totalTripsFinished")

                        when (platformName) {
                            "Pathao" -> {
                                binding.tvPathaoRequests.text = totalRequests.toString()
                                binding.tvPathaoTripsFinished.text = totalTripsFinished.toString()
                            }
                            "Uber" -> {
                                binding.tvUberRequests.text = totalRequests.toString()
                                binding.tvUberTripsFinished.text = totalTripsFinished.toString()
                            }
                        }
                    }
                } ?: loadHardcodedData()

            } catch (e: Exception) {
                loadHardcodedData()
            }
        }
    }

    private fun loadHardcodedData() {
        binding.apply {
            tvPathaoRequests.text = "11465"
            tvPathaoTripsFinished.text = "11465"
            tvUberRequests.text = "11476"
            tvUberTripsFinished.text = "11476"
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