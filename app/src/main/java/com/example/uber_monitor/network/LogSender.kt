package com.example.uber_monitor.network

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.uber_monitor.utils.LocationHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONObject
import java.io.IOException

object LogSender {
    private val client = OkHttpClient()
    private const val DEFAULT_API_URL = "https://giglytech-data-collection-api.global.fintech23.xyz/raw-trip-finished"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun sendLog(baseJson: JSONObject, apiUrl: String = DEFAULT_API_URL) {
        // Get access token
        val authPrefs = appContext.getSharedPreferences("uber_monitor_auth", Context.MODE_PRIVATE)
        val accessToken = authPrefs.getString("access_token", "") ?: ""

        if (accessToken.isEmpty()) {
            Log.e("LogSender", "No access token available, skipping log send")
            return
        }

        LocationHelper.getLastLocation { location ->
            baseJson.put("timestamp", System.currentTimeMillis())
            if (location != null) {
                baseJson.put("coordinates", listOf(location.latitude, location.longitude))
            } else {
                baseJson.put("coordinates", JSONObject.NULL)
            }

            val deviceId = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            baseJson.put("device_id", deviceId)

            Log.d("LogSender", "Sending log body to $apiUrl: $baseJson")
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = baseJson.toString().toRequestBody(mediaType)

            // Add Bearer token to request
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "EN")
                .post(body)
                .build()

            val buffer = Buffer().also { request.body?.writeTo(it) }
            Log.d("LogSender", "Request body (raw): ${buffer.readUtf8()}")

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("LogSender", "Failed to send log to $apiUrl", e)
                }
                override fun onResponse(call: Call, response: Response) {
                    Log.d("LogSender", "Log sent to $apiUrl, response code: ${response.code}")
                    if (response.code == 401) {
                        Log.w("LogSender", "Unauthorized - token may be expired")
                    }
                    response.close()
                }
            })
        }
    }
}