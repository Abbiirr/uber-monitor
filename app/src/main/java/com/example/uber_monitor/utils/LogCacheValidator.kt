// File: LogCacheValidator.kt
package com.example.uber_monitor.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.IOException

/**
 * Utility that deduplicates JSON payloads based on configured fields per data type,
 * enforcing that identical payloads within a 10-minute window and from the same device
 * are treated as duplicates. Loads per-type field sets from assets/log_field_config.yaml.
 * Stores cache JSON, timestamp, and device ID separately in SharedPreferences.
 */
class LogCacheValidator(private val context: Context) {

    private companion object {
        private const val TAG = "LogCacheValidator"
        private const val PREFS_NAME = "log_cache_validator"
        private const val KEY_CONFIG   = "cache_%s"       // JSON string
        private const val KEY_TS       = "cache_%s_ts"    // timestamp
        private const val KEY_DEVICE   = "cache_%s_dev"   // device ID
        private const val CONFIG_ASSET = "log_field_config.yaml"
        private const val TEN_MIN_MS   = 10 * 60 * 1000L    // 10 minutes in millis
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val fieldConfig: Map<String, List<String>> by lazy { loadFieldConfig() }

    /**
     * Returns true if jsonBody for dataType should be sent:
     * - sends if no cache exists
     * - sends if any configured field differs
     * - sends if last send was >10 minutes ago
     * - sends if current device ID differs from last
     * Otherwise returns false and skips send.
     */
    fun shouldSend(jsonBody: JSONObject, dataType: String): Boolean {
        Log.d(TAG, "shouldSend called for dataType=$dataType, payload=$jsonBody")

        val keys = fieldConfig[dataType]
        if (keys == null) {
            Log.w(TAG, "No field config for '$dataType'; sending by default.")
            cache(jsonBody, dataType)
            return true
        }
        Log.d(TAG, "Fields to compare for '$dataType': $keys")

        // Retrieve cache
        val cacheKey  = KEY_CONFIG.format(dataType)
        val tsKey     = KEY_TS.format(dataType)
        val devKey    = KEY_DEVICE.format(dataType)
        val lastJson  = prefs.getString(cacheKey, null)
        val lastTs    = prefs.getLong(tsKey, -1L)
        val lastDev   = prefs.getString(devKey, null)
        val currDev   = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        if (lastJson == null || lastDev == null) {
            Log.d(TAG, "No previous cache for '$dataType', will send.")
            cache(jsonBody, dataType)
            return true
        }
        Log.d(TAG, "Last cached JSON for '$dataType': $lastJson")
        Log.d(TAG, "Last device ID: $lastDev, Current device ID: $currDev")

        // Compare fields
        val lastObj = JSONObject(lastJson)
        val fieldsMatch = keys.all { field ->
            val newVal = jsonBody.optString(field, "")
            val oldVal = lastObj.optString(field, "")
            Log.d(TAG, "Compare '$field': new='$newVal', old='$oldVal'")
            newVal == oldVal
        }
        Log.d(TAG, "All fields match: $fieldsMatch")

        // Check time window
        val now = System.currentTimeMillis()
        val timeDiff = now - lastTs
        val recent = lastTs > 0 && timeDiff < TEN_MIN_MS
        Log.d(TAG, "LastTs=$lastTs, now=$now, diff=${timeDiff}ms, within10min=$recent")

        // Check device match
        val sameDevice = currDev == lastDev
        Log.d(TAG, "Same device: $sameDevice")

        // Final decision
        return if (fieldsMatch && sameDevice) {
            Log.i(TAG, "Duplicate for '$dataType' within 10min from same device; skipping.")
            false
        } else {
            Log.i(TAG, "Sending new payload for '$dataType'.")
            cache(jsonBody, dataType)
            true
        }
    }

    /**
     * Caches JSON, timestamp, and current device ID for dataType.
     */
    private fun cache(jsonBody: JSONObject, dataType: String) {
        val cacheKey = KEY_CONFIG.format(dataType)
        val tsKey    = KEY_TS.format(dataType)
        val devKey   = KEY_DEVICE.format(dataType)
        val now = System.currentTimeMillis()
        val currDev = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        prefs.edit()
            .putString(cacheKey, jsonBody.toString())
            .putLong(tsKey, now)
            .putString(devKey, currDev)
            .apply()

        Log.d(TAG, "Cached for '$dataType': JSON and timestamp=$now, device=$currDev")
    }

    /**
     * Loads YAML config from assets to map dataType -> list of keys.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadFieldConfig(): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        try {
            context.assets.open(CONFIG_ASSET).bufferedReader().use { reader ->
                val yaml = Yaml()
                val loaded = yaml.load<Any>(reader)
                if (loaded is Map<*, *>) {
                    loaded.forEach { (k, v) ->
                        val key = k.toString()
                        val list = when (v) {
                            is List<*> -> v.map { it.toString() }
                            is String  -> listOf(v)
                            else       -> emptyList()
                        }
                        map[key] = list
                    }
                }
            }
            Log.d(TAG, "Loaded field config: $map")
        } catch (e: IOException) {
            Log.e(TAG, "Error opening config asset '$CONFIG_ASSET'", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config asset '$CONFIG_ASSET'", e)
        }
        return map
    }
}
