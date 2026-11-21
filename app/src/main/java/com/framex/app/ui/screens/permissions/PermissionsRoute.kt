package com.framex.app.ui.screens.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framex.app.permissions.ShizukuIntents
import com.framex.app.ui.components.ToastEffect
import com.framex.app.ui.featurediscovery.DiscoveryScreenId
import com.framex.app.ui.featurediscovery.ScreenDiscoveryEffect

@Composable
fun PermissionsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    ScreenDiscoveryEffect(DiscoveryScreenId.PERMISSIONS)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val grantPermission = rememberPermissionGranter {
        viewModel.onEvent(PermissionsUiEvent.RefreshPermissions)
    }
    ToastEffect(viewModel.toasts)

    PermissionsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onEvent = viewModel::onEvent,
        onGrantPermission = grantPermission,
        onLaunchShizuku = { ShizukuIntents.launch(context) },
        modifier = modifier
    )
}
