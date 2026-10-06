package com.framex.app.ui.screens.sessionlogs.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.screens.sessionlogs.SessionLogFilter

private val ChipShape = RoundedCornerShape(8.dp)

/**
 * Filter bar presenting category chips and quick actions for Export and Clear.
 */
@Composable
fun SessionLogsFilterBar(
    selectedFilter: SessionLogFilter,
    totalCount: Int,
    successCount: Int,
    failedCount: Int,
    infoCount: Int,
    onSelectFilter: (SessionLogFilter) -> Unit,
    onCopyAll: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Scrollable filter chips
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SessionLogFilter.values().forEach { filter ->
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
                    isSelected = isSelected,
                    onClick = { onSelectFilter(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action buttons: Export and Clear
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onCopyAll,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.session_logs_copy_all),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onClearClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.session_logs_clear_logs),
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterPillChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f)
    val border = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.06f)
    val textCol = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .clip(ChipShape)
            .background(bg)
            .border(1.dp, border, ChipShape)
            .clickable(onClick = onClick)
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
