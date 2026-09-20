package com.framex.app.gaming

import com.framex.app.utils.FrameXLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted snapshot of every system setting FrameX modifies during Gaming Mode activation.
 * Stored before the first modification and restored on deactivation. Allows crash-safe recovery.
 */
data class GamingOptimizationSnapshot(
    val activeGamePackage: String?,
    val activeGameUid: Int?,
    val timestamp: Long,
    val minRefreshRate: SettingValue?,
    val peakRefreshRate: SettingValue?,
    val touchResponseSpeed: SettingValue?,
    val userPreferredDisplayModeId: SettingValue?,
    val affectedPackages: Set<String>
) {

    // =========================================================================
    // Serialization
    // =========================================================================

    fun toJson(): String {
        val json = JSONObject()
        json.put(KEY_ACTIVE_GAME_PACKAGE, activeGamePackage ?: JSONObject.NULL)
        json.put(KEY_ACTIVE_GAME_UID, activeGameUid ?: JSONObject.NULL)
        json.put(KEY_TIMESTAMP, timestamp)

        serializeSettingValues(json)
        serializeAffectedPackages(json)

        return json.toString()
    }

    private fun serializeSettingValues(json: JSONObject) {
        json.put(KEY_MIN_REFRESH_RATE, minRefreshRate?.toJson() ?: JSONObject.NULL)
        json.put(KEY_PEAK_REFRESH_RATE, peakRefreshRate?.toJson() ?: JSONObject.NULL)
        json.put(KEY_TOUCH_RESPONSE_SPEED, touchResponseSpeed?.toJson() ?: JSONObject.NULL)
        json.put(KEY_USER_PREFERRED_DISPLAY_MODE_ID, userPreferredDisplayModeId?.toJson() ?: JSONObject.NULL)
    }

    private fun serializeAffectedPackages(json: JSONObject) {
        val pkgsArray = JSONArray()
        affectedPackages.forEach { pkgsArray.put(it) }
        json.put(KEY_AFFECTED_PACKAGES, pkgsArray)
    }

    // =========================================================================
    // Deserialization Factory
    // =========================================================================

    companion object {
        private const val TAG = "GamingSnapshot"

        private const val KEY_ACTIVE_GAME_PACKAGE = "activeGamePackage"
        private const val KEY_ACTIVE_GAME_UID = "activeGameUid"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_MIN_REFRESH_RATE = "minRefreshRate"
        private const val KEY_PEAK_REFRESH_RATE = "peakRefreshRate"
        private const val KEY_TOUCH_RESPONSE_SPEED = "touchResponseSpeed"
        private const val KEY_USER_PREFERRED_DISPLAY_MODE_ID = "userPreferredDisplayModeId"
        private const val KEY_AFFECTED_PACKAGES = "affectedPackages"

        fun fromJson(jsonStr: String): GamingOptimizationSnapshot? {
            return runCatching {
                val json = JSONObject(jsonStr)

                GamingOptimizationSnapshot(
                    activeGamePackage = json.optNullableString(KEY_ACTIVE_GAME_PACKAGE),
                    activeGameUid = if (json.isNull(KEY_ACTIVE_GAME_UID)) null else json.optInt(KEY_ACTIVE_GAME_UID),
                    timestamp = json.optLong(KEY_TIMESTAMP, System.currentTimeMillis()),
                    minRefreshRate = parseSettingValue(json, KEY_MIN_REFRESH_RATE),
                    peakRefreshRate = parseSettingValue(json, KEY_PEAK_REFRESH_RATE),
                    touchResponseSpeed = parseSettingValue(json, KEY_TOUCH_RESPONSE_SPEED),
                    userPreferredDisplayModeId = parseSettingValue(json, KEY_USER_PREFERRED_DISPLAY_MODE_ID),
                    affectedPackages = parsePackageList(json, KEY_AFFECTED_PACKAGES)
                )
            }.onFailure { e ->
                FrameXLog.e("Failed to parse GamingOptimizationSnapshot from JSON", e, tag = TAG)
            }.getOrNull()
        }

        private fun parseSettingValue(json: JSONObject, key: String): SettingValue? {
            if (json.isNull(key)) return null
            val obj = json.optJSONObject(key) ?: return null
            return SettingValue.fromJson(obj)
        }

        private fun parsePackageList(json: JSONObject, key: String): Set<String> {
            val result = mutableSetOf<String>()
            val array = json.optJSONArray(key) ?: return result
            for (i in 0 until array.length()) {
                val item = array.optString(i)
                if (!item.isNullOrBlank()) {
                    result.add(item)
                }
            }
            return result
        }

        private fun JSONObject.optNullableString(key: String): String? {
            return if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
        }
    }
}

/**
 * Represents a system setting value with existence tracking.
 * If [existed] is false, the setting key was absent before FrameX wrote it.
 */
data class SettingValue(
    val value: String,
    val existed: Boolean
) {

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put(KEY_VALUE, value)
        json.put(KEY_EXISTED, existed)
        return json
    }

    companion object {
        private const val KEY_VALUE = "value"
        private const val KEY_EXISTED = "existed"

        fun fromJson(json: JSONObject): SettingValue {
            return SettingValue(
                value = json.optString(KEY_VALUE, ""),
                existed = json.optBoolean(KEY_EXISTED, false)
            )
        }

        /**
         * Create SettingValue from settings command output.
         * Returns SettingValue("", existed = false) if output is "null" or empty.
         */
        fun fromCommandOutput(output: String): SettingValue {
            val trimmed = output.trim()
            return when {
                trimmed.isEmpty() || trimmed == "null" -> SettingValue("", existed = false)
                else -> SettingValue(trimmed, existed = true)
            }
        }
    }
}