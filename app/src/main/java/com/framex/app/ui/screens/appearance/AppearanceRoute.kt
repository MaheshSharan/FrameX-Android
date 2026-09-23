package com.framex.app.ui.screens.appearance

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppearanceRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AppearanceUiEffect.ShowToast -> {
                    Toast.makeText(context, context.getString(effect.messageResId), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AppearanceScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}
