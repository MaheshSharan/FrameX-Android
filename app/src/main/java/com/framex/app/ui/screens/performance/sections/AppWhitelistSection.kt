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

fun LazyListScope.AppWhitelistSection(
    userApps: List<AppInfo>,
    whitelist: Set<String>,
    onToggleWhitelist: (String) -> Unit
) {
    item {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "APP WHITELIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Text(
                    "${whitelist.size} protected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Apps switched ON will NOT be killed or restricted when Gaming Mode activates. " +
                    "Shizuku and FrameX are always protected.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }
    }

    if (userApps.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    } else {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(
            items = userApps,
            key = { app -> app.packageName }
        ) { app ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 3.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    com.framex.app.ui.components.WovenNetBackground(modifier = Modifier.matchParentSize())
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        AppWhitelistRow(
                            app = app,
                            isWhitelisted = whitelist.contains(app.packageName),
                            onToggle = { onToggleWhitelist(app.packageName) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
