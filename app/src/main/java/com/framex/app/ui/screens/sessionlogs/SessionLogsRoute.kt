package com.framex.app.ui.screens.sessionlogs

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framex.app.R

/**
 * Stateful Route wrapper for the Session Logs screen.
 * Observes lifecycle-aware UI state and dispatches UI effects.
 */
@Composable
fun SessionLogsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionLogsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SessionLogsUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SessionLogsUiEffect.CopyToClipboard -> {
                    clipboardManager.setText(AnnotatedString(effect.text))
                    val toastMsg = if (effect.label == "Session Logs") {
                        context.getString(R.string.session_logs_copied_toast)
                    } else {
                        context.getString(R.string.session_logs_entry_copied_toast)
                    }
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SessionLogsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}
