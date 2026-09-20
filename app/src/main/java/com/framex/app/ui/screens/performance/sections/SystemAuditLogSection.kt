package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter(val label: String) {
    ALL("All"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed")
}

@Composable
fun SystemAuditLogSection(
    isLoggingEnabled: Boolean,
    onToggleLogging: (Boolean) -> Unit,
    auditLogs: List<SystemAuditLog>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }

    val filteredLogs = remember(auditLogs, selectedFilter) {
        when (selectedFilter) {
            LogFilter.ALL -> auditLogs
            LogFilter.SUCCEEDED -> auditLogs.filter { it.status == LogStatus.SUCCESS }
            LogFilter.FAILED -> auditLogs.filter { it.status == LogStatus.FAILED }
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = if (isLoggingEnabled) Color.Cyan else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "SYSTEM OPTIMIZATION AUDIT CONSOLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLoggingEnabled) Color.White.copy(0.9f) else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoggingEnabled && auditLogs.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        color = Color(0xFF818CF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onClearLogs() }
                            .padding(end = 12.dp)
                    )
                }

                Switch(
                    checked = isLoggingEnabled,
                    onCheckedChange = onToggleLogging,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.Cyan,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF1E1E2E)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.White.copy(0.06f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!isLoggingEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D0E11))
                            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Logging is currently disabled to preserve gaming performance.\nToggle on to capture real-time hardware audit events.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF6B7080),
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    // Filter Tabs: All, Succeeded, Failed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LogFilter.values().forEach { filter ->
                            val isSelected = filter == selectedFilter
                            val count = when (filter) {
                                LogFilter.ALL -> auditLogs.size
                                LogFilter.SUCCEEDED -> auditLogs.count { it.status == LogStatus.SUCCESS }
                                LogFilter.FAILED -> auditLogs.count { it.status == LogStatus.FAILED }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFF6366F1).copy(0.2f)
                                        else Color.White.copy(0.04f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF6366F1).copy(0.6f)
                                        else Color.White.copy(0.06f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedFilter = filter }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${filter.label} ($count)",
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color(0xFF818CF8) else Color.Gray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Terminal / Console View Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D0E11))
                            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (filteredLogs.isEmpty()) {
                            Text(
                                text = if (auditLogs.isEmpty()) "No system optimization actions recorded yet.\nStart Gaming Mode or launch a game to view audit trail."
                                else "No logs matching filter '${selectedFilter.label}'",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = Color(0xFF6B7080),
                                lineHeight = 17.sp
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filteredLogs.take(15).forEach { log ->
                                    val statusColor = when (log.status) {
                                        LogStatus.SUCCESS -> Color(0xFF10B981)
                                        LogStatus.FAILED -> Color(0xFFFF5252)
                                        LogStatus.INFO -> Color.Cyan
                                    }
                                    val statusBadge = when (log.status) {
                                        LogStatus.SUCCESS -> "OK"
                                        LogStatus.FAILED -> "ERR"
                                        LogStatus.INFO -> "INFO"
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = timeFormat.format(Date(log.timestamp)),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = statusBadge,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = log.action,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.5.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = log.details,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp,
                                                color = Color(0xFFB0B3C1),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
