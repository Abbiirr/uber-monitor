package com.example.uber_monitor

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import android.util.Log

class RegistrationActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var submitButton: Button
    private lateinit var progressBar: ProgressBar

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleText = TextView(this).apply {
            text = "Driver Registration"
            textSize = 24f
            setPadding(0, 0, 0, 40)
        }

        val nameLabel = TextView(this).apply {
            text = "Full Name"
            textSize = 16f
            setPadding(0, 20, 0, 10)
        }

        nameInput = EditText(this).apply {
            hint = "Enter your full name"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val phoneLabel = TextView(this).apply {
            text = "Phone Number"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        }

        phoneInput = EditText(this).apply {
            hint = "Enter your phone number (e.g. +8801XXXXXXXXX)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        progressBar = ProgressBar(this).apply {
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 30, 0, 30)
            }
        }

        submitButton = Button(this).apply {
            text = "Submit & Continue"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 40, 0, 20)
            }
            setOnClickListener { handleSubmit() }
        }

        val skipButton = Button(this).apply {
            text = "Skip for now"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                saveUserData("Guest", "")
                navigateToDashboard()
            }
        }

        layout.addView(titleText)
        layout.addView(nameLabel)
        layout.addView(nameInput)
        layout.addView(phoneLabel)
        layout.addView(phoneInput)
        layout.addView(progressBar)
        layout.addView(submitButton)
        layout.addView(skipButton)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun handleSubmit() {
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show()
            return
        }

        // Basic phone validation for international format
        if (!phone.startsWith("+")) {
            Toast.makeText(this, "Please enter phone number with country code (e.g. +880...)", Toast.LENGTH_SHORT).show()
            return
        }

        submitButton.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE

        scope.launch {
            saveUserData(name, phone)

            val success = withContext(Dispatchers.IO) {
                registerUser(name, phone)
            }

            delay(500)
            withContext(Dispatchers.Main) {
                progressBar.visibility = android.view.View.GONE
                submitButton.isEnabled = true

                if (success) {
                    Toast.makeText(this@RegistrationActivity, "Registration successful!", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                } else {
                    Toast.makeText(this@RegistrationActivity, "Registration failed, but you can continue", Toast.LENGTH_LONG).show()
                    navigateToDashboard()
                }
            }
        }
    }

    private fun registerUser(name: String, phone: String): Boolean {
        return try {
            val url = URL("https://giglytech-user-service-api.global.fintech23.xyz/api/v1/user/data-collector/auth")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("Accept-Language", "EN")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true

            val deviceId = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )

            val json = JSONObject().apply {
                put("phoneNumber", phone)
                put("name", name)
                put("deviceId", deviceId)
            }

            connection.outputStream.use {
                it.write(json.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            Log.d("RegistrationActivity", "API Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(response)

                Log.d("RegistrationActivity", "API Response: $response")

                if (responseJson.getString("responseCode") == "S100000") {
                    val data = responseJson.getJSONObject("data")
                    val accessToken = data.getString("accessToken")
                    val refreshToken = data.getString("refreshToken")

                    // Save tokens
                    val authPrefs = getSharedPreferences("uber_monitor_auth", MODE_PRIVATE)
                    authPrefs.edit().apply {
                        putString("access_token", accessToken)
                        putString("refresh_token", refreshToken)
                        putString("device_id", deviceId)
                        apply()
                    }

                    Log.d("RegistrationActivity", "Tokens saved successfully")
                    true
                } else {
                    Log.e("RegistrationActivity", "API returned error: ${responseJson.getString("responseMessage")}")
                    false
                }
            } else {
                Log.e("RegistrationActivity", "HTTP error: $responseCode")
                false
            }
        } catch (e: Exception) {
            Log.e("RegistrationActivity", "Registration failed", e)
            false
        }
    }

    private fun saveUserData(name: String, phone: String) {
        val prefs = getSharedPreferences("uber_monitor_user", MODE_PRIVATE)
        prefs.edit().apply {
            putString("user_name", name)
            putString("user_phone", phone)
            putBoolean("registered", true)
            apply()
        }
        Log.d("RegistrationActivity", "User data saved: $name, $phone")
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this@RegistrationActivity, DashboardActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}