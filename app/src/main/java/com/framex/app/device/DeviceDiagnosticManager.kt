package com.framex.app.device

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DeviceDiagnosticManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cachedProps = mutableMapOf<String, String>()

    suspend fun loadProperties(): Map<String, String> = withContext(Dispatchers.IO) {
        if (cachedProps.isNotEmpty()) return@withContext cachedProps

        val keys = runCatching {
            context.assets.open("getprop_keys.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }
        }.getOrDefault(emptyList())

        keys.forEach { key ->
            val value = readSystemProperty(key)
            if (value.isNotBlank()) {
                cachedProps[key] = value
            }
        }
        cachedProps
    }

    fun getProperty(key: String): String? {
        return cachedProps[key] ?: readSystemProperty(key).ifBlank { null }
    }

    open fun isVivoOnly(): Boolean {
        val brand = getProperty("ro.product.brand")?.lowercase() ?: android.os.Build.BRAND?.lowercase().orEmpty()
        val manufacturer = getProperty("ro.product.manufacturer")?.lowercase() ?: android.os.Build.MANUFACTURER?.lowercase().orEmpty()
        return (brand.contains("vivo") || manufacturer.contains("vivo")) && !isIqooOnly()
    }

    open fun isIqooOnly(): Boolean {
        val brand = getProperty("ro.product.brand")?.lowercase() ?: android.os.Build.BRAND?.lowercase().orEmpty()
        val manufacturer = getProperty("ro.product.manufacturer")?.lowercase() ?: android.os.Build.MANUFACTURER?.lowercase().orEmpty()
        val model = getProperty("ro.product.model")?.lowercase() ?: android.os.Build.MODEL?.lowercase().orEmpty()
        return brand.contains("iqoo") || manufacturer.contains("iqoo") || model.contains("iqoo")
    }

    open fun isVivoOrIqoo(): Boolean {
        return isVivoOnly() || isIqooOnly()
    }

    fun getDeviceModelInfo(): String {
        val model = getProperty("ro.product.model") ?: android.os.Build.MODEL
        val brand = getProperty("ro.product.brand") ?: android.os.Build.BRAND
        return "${brand.uppercase()} $model"
    }

    open fun getMaxHardwareRefreshRate(): Float {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        val display = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        var maxHz = 60.0f
        display?.supportedModes?.forEach { mode ->
            if (mode.refreshRate > maxHz) {
                maxHz = mode.refreshRate
            }
        }
        return maxHz
    }

    private fun readSystemProperty(key: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine().orEmpty().trim() }
        } catch (_: Exception) {
            ""
        }
    }
}
