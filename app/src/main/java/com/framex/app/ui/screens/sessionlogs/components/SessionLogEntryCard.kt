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

import com.framex.app.ui.screens.sessionlogs.tag
import com.framex.app.ui.screens.sessionlogs.color
import com.framex.app.ui.screens.sessionlogs.containerColor

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(LogCardShape)
            .background(Color(0xFF0D0E13))
            .border(1.dp, Color.White.copy(alpha = 0.06f), LogCardShape)
            .clickable(
                onClickLabel = "Copy log details",
                onClick = onCopy
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LogEntryHeaderRow(
                status = log.status,
                action = log.action,
                formattedTime = formattedTime
            )

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

@Composable
private fun LogEntryHeaderRow(
    status: LogStatus,
    action: String,
    formattedTime: String
) {
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
            Box(
                modifier = Modifier
                    .clip(BadgeShape)
                    .background(status.containerColor)
                    .border(1.dp, status.color.copy(alpha = 0.35f), BadgeShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = status.tag,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = status.color
                )
            }

            Text(
                text = action,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Text(
            text = formattedTime,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            color = Color(0xFF64748B)
        )
    }
}
