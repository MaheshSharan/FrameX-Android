package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.gaming.AppInfo
import com.framex.app.ui.screens.performance.components.AppWhitelistRow

fun LazyListScope.GoogleAppsSection(
    googleApps: List<AppInfo>,
    whitelist: Set<String>,
    onToggleWhitelist: (String) -> Unit
) {
    if (googleApps.isEmpty()) return

    item {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "GOOGLE APPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Text(
                    "${googleApps.count { whitelist.contains(it.packageName) }} protected",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4285F4)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Google apps that will be suspended during Gaming Mode. " +
                    "Toggle ON to keep an app running.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }
    }

    items(
        items = googleApps,
        key = { app -> "google_${app.packageName}" }
    ) { app ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 3.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF4285F4).copy(0.15f))
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                AppWhitelistRow(
                    app = app,
                    isWhitelisted = whitelist.contains(app.packageName),
                    onToggle = { onToggleWhitelist(app.packageName) }
                )
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(24.dp))
    }
}
