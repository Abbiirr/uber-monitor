package com.example.uber_monitor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
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
    private lateinit var nameErrorText: TextView
    private lateinit var phoneErrorText: TextView

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Main container with gradient background
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF100")) // Uber yellow background
        }

        // Top section with welcome message
        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // App logo/icon placeholder
        val logoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val logoCircle = TextView(this).apply {
            text = "🚗"
            textSize = 48f
            gravity = Gravity.CENTER
            width = 120
            height = 120
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }

        logoContainer.addView(logoCircle)

        val titleText = TextView(this).apply {
            text = "Welcome Driver!"
            textSize = 28f
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        val subtitleText = TextView(this).apply {
            text = "Register to start monitoring your rides"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        topSection.addView(logoContainer)
        topSection.addView(titleText)
        topSection.addView(subtitleText)

        // Card container for form
        val cardView = CardView(this).apply {
            radius = 20f
            cardElevation = 10f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(30, 0, 30, 30)
            }
        }

        val formLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        // Name input section
        val nameLabel = TextView(this).apply {
            text = "Full Name"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        nameInput = EditText(this).apply {
            hint = "Enter your full name"
            textSize = 16f
            setPadding(20, 30, 20, 30)
            background = GradientDrawable().apply {
                setStroke(2, Color.parseColor("#E0E0E0"))
                cornerRadius = 12f
                setColor(Color.parseColor("#F5F5F5"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    validateName()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        nameErrorText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.RED)
            setPadding(10, 4, 0, 0)
            visibility = View.GONE
        }

        // Phone input section
        val phoneLabel = TextView(this).apply {
            text = "Phone Number"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 20, 0, 8)
        }

        phoneInput = EditText(this).apply {
            hint = "+8801XXXXXXXXX"
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 16f
            setPadding(20, 30, 20, 30)
            background = GradientDrawable().apply {
                setStroke(2, Color.parseColor("#E0E0E0"))
                cornerRadius = 12f
                setColor(Color.parseColor("#F5F5F5"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    validatePhone()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        phoneErrorText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.RED)
            setPadding(10, 4, 0, 0)
            visibility = View.GONE
        }

        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 30, 0, 0)
            }
        }

        // Submit button
        submitButton = Button(this).apply {
            text = "Register & Continue"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = 25f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                150
            ).apply {
                setMargins(0, 40, 0, 0)
            }
            setOnClickListener { handleSubmit() }
        }

        // Privacy note
        val privacyText = TextView(this).apply {
            text = "Your information is securely stored and used only for ride monitoring"
            textSize = 11f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        // Add all views to form
        formLayout.addView(nameLabel)
        formLayout.addView(nameInput)
        formLayout.addView(nameErrorText)
        formLayout.addView(phoneLabel)
        formLayout.addView(phoneInput)
        formLayout.addView(phoneErrorText)
        formLayout.addView(progressBar)
        formLayout.addView(submitButton)
        formLayout.addView(privacyText)

        cardView.addView(formLayout)

        // Add everything to main layout
        mainLayout.addView(topSection)
        mainLayout.addView(cardView)

        // Wrap in ScrollView
        val scrollView = ScrollView(this).apply {
            addView(mainLayout)
        }

        setContentView(scrollView)
    }

    private fun validateName(): Boolean {
        val name = nameInput.text.toString().trim()
        return when {
            name.isEmpty() -> {
                nameErrorText.text = "Name is required"
                nameErrorText.visibility = View.VISIBLE
                false
            }
            name.length < 2 -> {
                nameErrorText.text = "Name must be at least 2 characters"
                nameErrorText.visibility = View.VISIBLE
                false
            }
            else -> {
                nameErrorText.visibility = View.GONE
                true
            }
        }
    }

    private fun validatePhone(): Boolean {
        val phone = phoneInput.text.toString().trim()
        return when {
            phone.isEmpty() -> {
                phoneErrorText.text = "Phone number is required"
                phoneErrorText.visibility = View.VISIBLE
                false
            }
            !phone.startsWith("+") -> {
                phoneErrorText.text = "Include country code (e.g., +880)"
                phoneErrorText.visibility = View.VISIBLE
                false
            }
            phone.length < 10 -> {
                phoneErrorText.text = "Invalid phone number"
                phoneErrorText.visibility = View.VISIBLE
                false
            }
            else -> {
                phoneErrorText.visibility = View.GONE
                true
            }
        }
    }

    private fun handleSubmit() {
        val isNameValid = validateName()
        val isPhoneValid = validatePhone()

        if (!isNameValid || !isPhoneValid) {
            return
        }

        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()

        submitButton.isEnabled = false
        submitButton.alpha = 0.6f
        progressBar.visibility = View.VISIBLE

        scope.launch {
            saveUserData(name, phone)

            val success = withContext(Dispatchers.IO) {
                registerUser(name, phone)
            }

            delay(500)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                submitButton.isEnabled = true
                submitButton.alpha = 1.0f

                if (success) {
                    Toast.makeText(this@RegistrationActivity, "Registration successful! 🎉", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                } else {
                    // Even on failure, allow navigation but show error
                    Toast.makeText(this@RegistrationActivity, "Network error. Continuing offline mode.", Toast.LENGTH_LONG).show()
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