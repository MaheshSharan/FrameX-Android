package com.framex.app.ui.screens.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.permissions.AppPermission
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.ui.screens.onboarding.OnboardingUiState
import com.framex.app.ui.screens.permissions.components.PermissionRow
import com.framex.app.ui.screens.permissions.components.PrivilegeModeSelector

/** Last onboarding page: privilege mode plus the permissions needed to show the overlay. */
@Composable
fun OnboardingEssentialsPage(
    uiState: OnboardingUiState,
    onSelectMode: (PrivilegeMode) -> Unit,
    onGrantPermission: (AppPermission) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.onboarding_essentials_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_essentials_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrivilegeModeSelector(
            selectedMode = uiState.selectedMode,
            isReady = uiState.isPrivilegeReady,
            onModeSelected = onSelectMode
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            uiState.essentials.forEach { item ->
                PermissionRow(item = item, onGrantClick = { onGrantPermission(item.permission) })
            }
        }
    }
}
