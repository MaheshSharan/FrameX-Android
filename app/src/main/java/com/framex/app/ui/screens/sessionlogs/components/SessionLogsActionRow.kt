package com.framex.app.ui.screens.sessionlogs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.theme.FrameXAccessibility
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing
import com.framex.app.ui.theme.StatusRed

/**
 * Dedicated row for the export and clear actions, kept apart from the
 * summary card and the filter chips so nothing overlaps on narrow screens.
 */
@Composable
fun SessionLogsActionRow(
    onCopyAll: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FrameXSpacing.Medium)
    ) {
        LogActionButton(
            icon = Icons.Outlined.ContentCopy,
            label = stringResource(R.string.session_logs_copy_all),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onCopyAll,
            modifier = Modifier.weight(1f)
        )
        LogActionButton(
            icon = Icons.Outlined.DeleteOutline,
            label = stringResource(R.string.session_logs_clear_logs),
            tint = StatusRed,
            onClick = onClearClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LogActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = FrameXShapes.Medium,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
        modifier = modifier.heightIn(min = FrameXAccessibility.MinTouchTarget)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            modifier = Modifier.padding(start = FrameXSpacing.Small)
        )
    }
}
