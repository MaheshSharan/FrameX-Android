package com.framex.app.ui.screens.performance.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.gaming.AppInfo
import com.framex.app.ui.screens.performance.AppIconCache

@Composable
fun AppWhitelistRow(
    app: AppInfo,
    isWhitelisted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = AppIconCache.get(app.packageName),
        key1 = app.packageName
    ) {
        if (value == null) {
            value = AppIconCache.loadIcon(context, app.packageName)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = isWhitelisted,
                role = Role.Switch,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "${app.label} whitelist switch"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val currentBitmap = iconBitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.label.take(2).uppercase(),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = app.packageName,
                color = Color.Gray,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = isWhitelisted,
            onCheckedChange = null,
            modifier = Modifier.graphicsLayer { scaleX = 0.85f; scaleY = 0.85f },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF22C55E)
            )
        )
    }
}
