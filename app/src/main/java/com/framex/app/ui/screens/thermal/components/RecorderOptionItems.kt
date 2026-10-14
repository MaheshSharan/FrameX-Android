package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.metrics.RecordableMetric
import com.framex.app.ui.screens.performance.AppIconCache
import com.framex.app.ui.screens.thermal.TargetAppOption
import com.framex.app.ui.theme.PrimaryRed

@Composable
internal fun TargetGameCard(
    option: TargetAppOption,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var iconBitmap by remember(option.packageName) {
        mutableStateOf<ImageBitmap?>(option.packageName?.let { AppIconCache.get(it) })
    }
    LaunchedEffect(option.packageName) {
        val pkg = option.packageName
        iconBitmap = if (!pkg.isNullOrBlank()) AppIconCache.loadIcon(context, pkg) else null
    }

    val borderCol = if (isSelected) PrimaryRed else Color.White.copy(alpha = 0.12f)
    val bgCol = if (isSelected) PrimaryRed.copy(alpha = 0.14f) else Color(0xFF141414)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgCol)
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = iconBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Icon(
                imageVector = if (option.packageName == null) Icons.Outlined.PhoneAndroid else Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = if (isSelected) PrimaryRed else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        val displayTitle = if (option.versionName.isNotBlank()) {
            "${option.label} (${option.versionName})"
        } else {
            option.label
        }
        Text(
            text = displayTitle,
            color = if (isSelected) Color.White else Color.LightGray,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
internal fun MetricCheckboxItem(
    metric: RecordableMetric,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) PrimaryRed.copy(alpha = 0.1f) else Color(0xFF141414))
            .border(
                1.dp,
                if (checked) PrimaryRed.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = PrimaryRed,
                uncheckedColor = Color.Gray,
                checkmarkColor = Color.White
            ),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = metric.label,
            color = if (checked) Color.White else Color.Gray,
            fontSize = 11.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
