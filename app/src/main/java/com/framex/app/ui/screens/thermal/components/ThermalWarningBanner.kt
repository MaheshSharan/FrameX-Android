package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.screens.thermal.SessionKpiSummary
import com.framex.app.ui.screens.thermal.formatDurationMinSec
import com.framex.app.ui.theme.FrameXShapes
import java.util.Locale

/**
 * Top excursion warning strip showing peak SoC temperature and duration spent above threshold.
 */
@Composable
fun ThermalWarningBanner(
    kpiSummary: SessionKpiSummary,
    isThrottling: Boolean,
    thermalStatus: Int,
    modifier: Modifier = Modifier
) {
    val showBanner = kpiSummary.maxSocTempC >= 70f || isThrottling || thermalStatus >= 2
    if (!showBanner) return

    val isSevere = kpiSummary.maxSocTempC >= SessionKpiSummary.HIGH_SOC_TEMP_THRESHOLD_C ||
        isThrottling ||
        thermalStatus >= 3
    val accentColor = if (isSevere) Color(0xFFEF4444) else Color(0xFFF59E0B)

    val leftText = remember(kpiSummary.maxSocTempC, isThrottling) {
        if (kpiSummary.maxSocTempC > 0f) {
            String.format(Locale.US, "SoC temperature elevated MAX: %.1f°C", kpiSummary.maxSocTempC)
        } else {
            "Hardware thermal throttling detected"
        }
    }

    val rightText = remember(kpiSummary.highTempDurationSec) {
        "≥${SessionKpiSummary.HIGH_SOC_TEMP_THRESHOLD_C.toInt()}°C ${formatDurationMinSec(kpiSummary.highTempDurationSec)}"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = FrameXShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = leftText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = 12.sp
                )
            }
            Text(
                text = rightText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(accentColor.copy(alpha = 0.22f), FrameXShapes.Small)
                    .border(1.dp, accentColor.copy(alpha = 0.4f), FrameXShapes.Small)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
