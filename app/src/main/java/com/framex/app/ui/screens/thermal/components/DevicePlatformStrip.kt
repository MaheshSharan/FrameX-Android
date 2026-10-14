package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.components.WovenNetBackground
import com.framex.app.ui.screens.thermal.DevicePlatformInfo
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.MutedBlue
import com.framex.app.ui.theme.MutedGreen
import com.framex.app.ui.theme.PrimaryRed
import com.framex.app.ui.theme.Purple

/**
 * 4-column hardware identity strip: CHIPSET (SoC) | DEVICE (Model) | SYSTEM (OS) | DISPLAY (Max Hz).
 * Styled with WovenNetBackground and squircle icon plates matching the rest of FrameX.
 */
@Composable
fun DevicePlatformStrip(
    platformInfo: DevicePlatformInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = FrameXShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(FrameXBorders.ActiveBorderWidth, FrameXBorders.CardStroke)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            WovenNetBackground(modifier = Modifier.matchParentSize())

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformItem(
                    label = "CHIPSET",
                    value = platformInfo.socName,
                    icon = Icons.Outlined.Memory,
                    tint = Purple,
                    modifier = Modifier.weight(1f)
                )
                PlatformItem(
                    label = "DEVICE",
                    value = platformInfo.modelName,
                    icon = Icons.Outlined.PhoneAndroid,
                    tint = MutedBlue,
                    modifier = Modifier.weight(1f)
                )
                PlatformItem(
                    label = "SYSTEM",
                    value = platformInfo.osVersion,
                    icon = Icons.Outlined.Android,
                    tint = MutedGreen,
                    modifier = Modifier.weight(1f)
                )
                PlatformItem(
                    label = "DISPLAY",
                    value = "${platformInfo.displayRefreshHz} Hz",
                    icon = Icons.Outlined.Speed,
                    tint = PrimaryRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PlatformItem(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f))
                .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
