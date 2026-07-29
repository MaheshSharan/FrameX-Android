package com.framex.app.gaming

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted snapshot of every system setting FrameX modifies during Gaming Mode activation.
 * Stored before the first modification and restored on deactivation. Allows crash-safe recovery.
 */
data class GamingOptimizationSnapshot(
    // Session metadata
    val activeGamePackage: String?,
    val activeGameUid: Int?,
    val timestamp: Long,
    
    // Android system settings (system namespace)
    val minRefreshRate: SettingValue?,
    val peakRefreshRate: SettingValue?,
    val touchResponseSpeed: SettingValue?,
    
    // Secure settings (secure namespace)
    val userPreferredDisplayModeId: SettingValue?,
    
    // Vivo global settings (global namespace)
    val vivoRefreshRateMode: SettingValue?,
    val vivoTouchPersist: SettingValue?,
    
    // Vivo global whitelist CSV keys
    val gameCubeApps: SettingValue?,
    val speedModeApps: SettingValue?,
    val vivoHighRefreshApps: SettingValue?,
    val vivoScreenRefreshAppsList: SettingValue?,
    
    // App state tracking
    val affectedPackages: Set<String>
) {
    
    fun toJson(): String {
        val json = JSONObject()
        json.put("activeGamePackage", activeGamePackage ?: JSONObject.NULL)
        json.put("activeGameUid", activeGameUid ?: JSONObject.NULL)
        json.put("timestamp", timestamp)
        
        json.put("minRefreshRate", minRefreshRate?.toJson() ?: JSONObject.NULL)
        json.put("peakRefreshRate", peakRefreshRate?.toJson() ?: JSONObject.NULL)
        json.put("touchResponseSpeed", touchResponseSpeed?.toJson() ?: JSONObject.NULL)
        json.put("userPreferredDisplayModeId", userPreferredDisplayModeId?.toJson() ?: JSONObject.NULL)
        json.put("vivoRefreshRateMode", vivoRefreshRateMode?.toJson() ?: JSONObject.NULL)
        json.put("vivoTouchPersist", vivoTouchPersist?.toJson() ?: JSONObject.NULL)
        
        json.put("gameCubeApps", gameCubeApps?.toJson() ?: JSONObject.NULL)
        json.put("speedModeApps", speedModeApps?.toJson() ?: JSONObject.NULL)
        json.put("vivoHighRefreshApps", vivoHighRefreshApps?.toJson() ?: JSONObject.NULL)
        json.put("vivoScreenRefreshAppsList", vivoScreenRefreshAppsList?.toJson() ?: JSONObject.NULL)
        
        val pkgsArray = JSONArray()
        affectedPackages.forEach { pkgsArray.put(it) }
        json.put("affectedPackages", pkgsArray)
        
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonStr: String): GamingOptimizationSnapshot? {
            return try {
                val json = JSONObject(jsonStr)
                
                val affectedPkgs = mutableSetOf<String>()
                if (json.has("affectedPackages")) {
                    val pkgsArray = json.getJSONArray("affectedPackages")
                    for (i in 0 until pkgsArray.length()) {
                        affectedPkgs.add(pkgsArray.getString(i))
                    }
                }
                
                GamingOptimizationSnapshot(
                    activeGamePackage = if (json.isNull("activeGamePackage")) null else json.getString("activeGamePackage"),
                    activeGameUid = if (json.isNull("activeGameUid")) null else json.getInt("activeGameUid"),
                    timestamp = json.getLong("timestamp"),
                    minRefreshRate = if (json.isNull("minRefreshRate")) null else SettingValue.fromJson(json.getJSONObject("minRefreshRate")),
                    peakRefreshRate = if (json.isNull("peakRefreshRate")) null else SettingValue.fromJson(json.getJSONObject("peakRefreshRate")),
                    touchResponseSpeed = if (json.isNull("touchResponseSpeed")) null else SettingValue.fromJson(json.getJSONObject("touchResponseSpeed")),
                    userPreferredDisplayModeId = if (json.isNull("userPreferredDisplayModeId")) null else SettingValue.fromJson(json.getJSONObject("userPreferredDisplayModeId")),
                    vivoRefreshRateMode = if (json.isNull("vivoRefreshRateMode")) null else SettingValue.fromJson(json.getJSONObject("vivoRefreshRateMode")),
                    vivoTouchPersist = if (json.isNull("vivoTouchPersist")) null else SettingValue.fromJson(json.getJSONObject("vivoTouchPersist")),
                    gameCubeApps = if (json.isNull("gameCubeApps")) null else SettingValue.fromJson(json.getJSONObject("gameCubeApps")),
                    speedModeApps = if (json.isNull("speedModeApps")) null else SettingValue.fromJson(json.getJSONObject("speedModeApps")),
                    vivoHighRefreshApps = if (json.isNull("vivoHighRefreshApps")) null else SettingValue.fromJson(json.getJSONObject("vivoHighRefreshApps")),
                    vivoScreenRefreshAppsList = if (json.isNull("vivoScreenRefreshAppsList")) null else SettingValue.fromJson(json.getJSONObject("vivoScreenRefreshAppsList")),
                    affectedPackages = affectedPkgs
                )
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.e("Failed to parse GamingOptimizationSnapshot from JSON", e)
                null
            }
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
        json.put("value", value)
        json.put("existed", existed)
        return json
    }
    
    companion object {
        fun fromJson(json: JSONObject): SettingValue {
            return SettingValue(
                value = json.getString("value"),
                existed = json.getBoolean("existed")
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