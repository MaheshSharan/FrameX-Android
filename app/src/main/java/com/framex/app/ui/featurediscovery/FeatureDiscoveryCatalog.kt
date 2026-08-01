package com.framex.app.ui.featurediscovery

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.framex.app.R

object FeatureDiscoveryCatalog {

    fun guideFor(screenId: DiscoveryScreenId): DiscoveryGuideDefinition? = when (screenId) {
        DiscoveryScreenId.PERMISSIONS -> permissionsGuide()
    }

    private fun permissionsGuide() = DiscoveryGuideDefinition(
        title = "Unrestricted battery",
        subtitle = "Keeps FrameX and Shizuku alive during long gaming sessions.",
        steps = listOf(
            "App Info → Battery",
            "Turn on Allow background activity",
            "Select Unrestricted — repeat for Shizuku"
        ),
        imageResId = R.drawable.guide_battery_unrestricted,
        actions = listOf(
            DiscoveryAction("Open FrameX App Info") { ctx ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
            }
        )
    )

    fun completionMessage(screenId: DiscoveryScreenId): String = when (screenId) {
        DiscoveryScreenId.PERMISSIONS -> "Setup noted"
    }
}
