package com.framex.app.ui.screens.sessionlogs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.gaming.LogStatus
import com.framex.app.gaming.SystemAuditLog
import com.framex.app.ui.screens.sessionlogs.SessionLogsUtils

private val LogCardShape = RoundedCornerShape(12.dp)
private val BadgeShape = RoundedCornerShape(6.dp)

/**
 * Terminal-styled card displaying an individual system audit log record.
 */
@Composable
fun SessionLogEntryCard(
    log: SystemAuditLog,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(log.timestamp) {
        SessionLogsUtils.formatTimestamp(log.timestamp)
    }

    val (statusText, statusColor, statusBg) = when (log.status) {
        LogStatus.SUCCESS -> Triple("OK", Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.12f))
        LogStatus.FAILED -> Triple("ERR", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.12f))
        LogStatus.INFO -> Triple("INFO", Color(0xFF06B6D4), Color(0xFF06B6D4).copy(alpha = 0.12f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(LogCardShape)
            .background(Color(0xFF0D0E13))
            .border(1.dp, Color.White.copy(alpha = 0.06f), LogCardShape)
            .clickable(onClick = onCopy)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Badge + Action + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(BadgeShape)
                            .background(statusBg)
                            .border(1.dp, statusColor.copy(alpha = 0.35f), BadgeShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    // Action Label
                    Text(
                        text = log.action,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                // Timestamp
                Text(
                    text = formattedTime,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Bottom Row: Details / Shell output
            if (log.details.isNotBlank()) {
                Text(
                    text = log.details,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFA0A5B5),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
