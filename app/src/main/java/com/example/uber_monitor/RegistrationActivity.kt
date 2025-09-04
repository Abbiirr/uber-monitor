package com.example.uber_monitor

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlin.math.log
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
            hint = "Enter your phone number"
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
                saveUserData("", "")
                finish()
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

        submitButton.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE

        scope.launch {
            // Save locally first
            saveUserData(name, phone)

            // Try API call in background
            withContext(Dispatchers.IO) {
                try {
                    // TODO: Replace with actual API endpoint
                    val url = URL("https://your-api-endpoint.com/register")
                    val connection = url.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true

                    val json = JSONObject().apply {
                        put("name", name)
                        put("phone", phone)
                        put("device_id", android.provider.Settings.Secure.getString(
                            contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ))
                    }

                    connection.outputStream.use {
                        it.write(json.toString().toByteArray())
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Success - but we don't show this to user
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    // Silently fail - user continues regardless
                    e.printStackTrace()
                }
            }

            // Always proceed after a short delay
            delay(1000)
            withContext(Dispatchers.Main) {
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@RegistrationActivity, "Registration complete!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun saveUserData(name: String, phone: String) {
        val prefs = getSharedPreferences("uber_monitor_user", MODE_PRIVATE)
        prefs.edit().apply {
            putString("user_name", name)
            putString("user_phone", phone)
            putBoolean("registered", true)
            Log.d("RegistrationActivity", "user data saved: $name, $phone")
            apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}