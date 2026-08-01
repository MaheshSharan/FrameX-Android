package com.framex.app.ui.featurediscovery

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes

enum class DiscoveryScreenId {
    PERMISSIONS
}

data class DiscoveryAction(
    val label: String,
    val intentBuilder: (Context) -> Intent
)

data class DiscoveryGuideDefinition(
    val title: String,
    val subtitle: String,
    val steps: List<String> = emptyList(),
    @DrawableRes val imageResId: Int? = null,
    val actions: List<DiscoveryAction> = emptyList()
)

sealed class DiscoveryGuideState {
    data object Hidden : DiscoveryGuideState()

    data class Active(
        val screenId: DiscoveryScreenId,
        val guide: DiscoveryGuideDefinition
    ) : DiscoveryGuideState()
}

data class DiscoveryCompletionEvent(
    val screenId: DiscoveryScreenId,
    val message: String,
    val actionLabel: String? = null
)
