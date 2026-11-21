package com.framex.app.ui.screens.sessionlogs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.gaming.LogStatus
import com.framex.app.ui.screens.sessionlogs.SessionLogFilter
import com.framex.app.ui.screens.sessionlogs.color
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.StatusEmerald
import com.framex.app.ui.theme.StatusRed

private val ChipShape = RoundedCornerShape(8.dp)

/**
 * Horizontally scrollable category chips for the audit log stream.
 */
@Composable
fun SessionLogsFilterBar(
    selectedFilter: SessionLogFilter,
    totalCount: Int,
    successCount: Int,
    failedCount: Int,
    infoCount: Int,
    onSelectFilter: (SessionLogFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChipsRow(
        selectedFilter = selectedFilter,
        totalCount = totalCount,
        successCount = successCount,
        failedCount = failedCount,
        infoCount = infoCount,
        onSelectFilter = onSelectFilter,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun FilterChipsRow(
    selectedFilter: SessionLogFilter,
    totalCount: Int,
    successCount: Int,
    failedCount: Int,
    infoCount: Int,
    onSelectFilter: (SessionLogFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SessionLogFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            val count = when (filter) {
                SessionLogFilter.ALL -> totalCount
                SessionLogFilter.SUCCESS -> successCount
                SessionLogFilter.FAILED -> failedCount
                SessionLogFilter.INFO -> infoCount
            }

            val filterLabel = when (filter) {
                SessionLogFilter.ALL -> stringResource(R.string.session_logs_filter_all)
                SessionLogFilter.SUCCESS -> stringResource(R.string.session_logs_filter_success)
                SessionLogFilter.FAILED -> stringResource(R.string.session_logs_filter_failed)
                SessionLogFilter.INFO -> stringResource(R.string.session_logs_filter_info)
            }

            FilterPillChip(
                label = filterLabel,
                count = count,
                accent = filter.accent(),
                isSelected = isSelected,
                onClick = { onSelectFilter(filter) }
            )
        }
    }
}

@Composable
private fun FilterPillChip(
    label: String,
    count: Int,
    accent: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f)
    val border = if (isSelected) accent.copy(alpha = 0.85f) else FrameXBorders.CardStroke
    val textCol = if (isSelected) accent else Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(ChipShape)
            .background(bg)
            .border(1.dp, border, ChipShape)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = textCol,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = count.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = textCol.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SessionLogFilter.accent(): Color = when (this) {
    SessionLogFilter.ALL -> MaterialTheme.colorScheme.primary
    SessionLogFilter.SUCCESS -> StatusEmerald
    SessionLogFilter.FAILED -> StatusRed
    SessionLogFilter.INFO -> LogStatus.INFO.color
}
