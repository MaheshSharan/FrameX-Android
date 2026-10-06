package com.framex.app.ui.screens.sessionlogs.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R

private val HeaderCardShape = RoundedCornerShape(16.dp)
private val StatBoxShape = RoundedCornerShape(10.dp)

/**
 * Top dashboard card displaying real-time recording status, toggle switch, and key statistics.
 */
@Composable
fun SessionLogsHeaderCard(
    isLoggingEnabled: Boolean,
    totalCount: Int,
    successRate: Int,
    failedCount: Int,
    onToggleLogging: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(HeaderCardShape)
            .background(Color(0xFF0F1015))
            .border(1.dp, Color.White.copy(alpha = 0.08f), HeaderCardShape)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RecordingStatusIndicatorRow(
                isLoggingEnabled = isLoggingEnabled,
                onToggleLogging = onToggleLogging
            )

            Text(
                text = stringResource(R.string.session_logs_toggle_desc),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                lineHeight = 17.sp
            )

            SessionLogsTelemetryStatsRow(
                totalCount = totalCount,
                successRate = successRate,
                failedCount = failedCount
            )
        }
    }
}

@Composable
private fun RecordingStatusIndicatorRow(
    isLoggingEnabled: Boolean,
    onToggleLogging: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulsingLedDot(isLoggingEnabled = isLoggingEnabled)

            val statusText = if (isLoggingEnabled) {
                stringResource(R.string.session_logs_recording_active)
            } else {
                stringResource(R.string.session_logs_recording_paused)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isLoggingEnabled) Color(0xFF34D399) else Color(0xFF94A3B8),
                letterSpacing = 0.6.sp
            )
        }

        Switch(
            checked = isLoggingEnabled,
            onCheckedChange = onToggleLogging,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E2028)
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun PulsingLedDot(isLoggingEnabled: Boolean) {
    if (!isLoggingEnabled) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF64748B))
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .graphicsLayer { alpha = pulseAlpha }
            .background(Color(0xFF10B981))
    )
}

@Composable
private fun SessionLogsTelemetryStatsRow(
    totalCount: Int,
    successRate: Int,
    failedCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile(
            label = stringResource(R.string.session_logs_stat_total),
            value = totalCount.toString(),
            valueColor = Color.White,
            modifier = Modifier.weight(1f)
        )

        val rateColor = when {
            successRate >= 90 -> Color(0xFF10B981)
            successRate >= 70 -> Color(0xFFFBBF24)
            else -> Color(0xFFEF4444)
        }
        StatTile(
            label = stringResource(R.string.session_logs_stat_success),
            value = "$successRate%",
            valueColor = rateColor,
            modifier = Modifier.weight(1f)
        )

        val errColor = if (failedCount > 0) Color(0xFFEF4444) else Color(0xFF64748B)
        StatTile(
            label = stringResource(R.string.session_logs_stat_errors),
            value = failedCount.toString(),
            valueColor = errColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(StatBoxShape)
            .background(Color(0xFF161822))
            .border(1.dp, Color.White.copy(alpha = 0.05f), StatBoxShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                letterSpacing = 0.3.sp
            )
        }
    }
}
