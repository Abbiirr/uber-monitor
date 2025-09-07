package com.example.uber_monitor

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiHelper {

    fun makeAuthenticatedRequest(
        context: Context,
        urlString: String,
        method: String = "GET",
        body: JSONObject? = null,
        retryCount: Int = 0
    ): String? {
        try {
            val authPrefs = context.getSharedPreferences("uber_monitor_auth", Context.MODE_PRIVATE)
            val accessToken = authPrefs.getString("access_token", "") ?: ""

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = method
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("Accept-Language", "EN")
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray())
                }
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().readText()
            } else if (responseCode == 401 && retryCount == 0) {
                // Token expired, try refresh
                Log.d("ApiHelper", "Got 401, attempting token refresh")

                if (refreshToken(context)) {
                    // Retry the original request with new token
                    return makeAuthenticatedRequest(context, urlString, method, body, retryCount + 1)
                }
            } else if (responseCode == 401 && retryCount > 0) {
                // Refresh failed, need to re-register
                Log.e("ApiHelper", "Token refresh failed, user needs to clear data")
                return null
            }

            Log.e("ApiHelper", "Request failed with code: $responseCode")
            return null

        } catch (e: Exception) {
            Log.e("ApiHelper", "Request failed", e)
            return null
        }
    }

    private fun refreshToken(context: Context): Boolean {
        return try {
            val authPrefs = context.getSharedPreferences("uber_monitor_auth", Context.MODE_PRIVATE)
            val refreshToken = authPrefs.getString("refresh_token", "") ?: ""

            if (refreshToken.isEmpty()) {
                Log.e("ApiHelper", "No refresh token available")
                return false
            }

            val url = URL("https://giglytech-user-service-api.global.fintech23.xyz/api/v1/user/data-collector/refresh")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("Accept-Language", "EN")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true

            val json = JSONObject().apply {
                put("refreshToken", refreshToken)
            }

            connection.outputStream.use {
                it.write(json.toString().toByteArray())
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(response)

                if (responseJson.getString("responseCode") == "S100000") {
                    val data = responseJson.getJSONObject("data")
                    val newAccessToken = data.getString("accessToken")
                    val newRefreshToken = data.getString("refreshToken")

                    // Save new tokens
                    authPrefs.edit().apply {
                        putString("access_token", newAccessToken)
                        putString("refresh_token", newRefreshToken)
                        apply()
                    }

                    Log.d("ApiHelper", "Token refreshed successfully")
                    return true
                }
            }

            Log.e("ApiHelper", "Token refresh failed")
            false

        } catch (e: Exception) {
            Log.e("ApiHelper", "Token refresh error", e)
            false
        }
    }
}