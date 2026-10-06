package com.framex.app.ui.screens.sessionlogs.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.framex.app.R

/**
 * Confirmation dialog before wiping recorded session logs.
 */
@Composable
fun ClearLogsConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.session_logs_clear_confirm_title),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Text(
                text = stringResource(R.string.session_logs_clear_confirm_desc),
                color = Color(0xFF94A3B8)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.session_logs_clear_action),
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.session_logs_cancel_action),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        containerColor = Color(0xFF161822)
    )
}
