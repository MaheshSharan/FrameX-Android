package com.framex.app.ui.screens.performance

import android.content.Context
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance, zero-churn memory cache for application icons.
 * Prevents redundant Binder transactions to PackageManager and Bitmap allocations
 * while scrolling lists of apps at 60/120Hz.
 */
object AppIconCache {
    private const val MAX_ENTRIES = 150
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        val cached = get(packageName)
        if (cached != null) return cached

        return withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap().asImageBitmap()
                put(packageName, bitmap)
                bitmap
            }.getOrNull()
        }
    }

    fun clear() {
        cache.evictAll()
    }
}
