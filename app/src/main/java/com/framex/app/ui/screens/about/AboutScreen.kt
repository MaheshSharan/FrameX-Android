package com.framex.app.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.components.SignatureMismatchDialog
import com.framex.app.ui.components.UpdateDialog
import com.framex.app.ui.components.VivoDiagnosticDialog
import com.framex.app.ui.screens.about.components.AboutHeroCard
import com.framex.app.ui.screens.about.components.AppUpdatesCard
import com.framex.app.ui.screens.about.components.CrashDiagnosticsCard
import com.framex.app.ui.screens.about.components.HardwareOptimizationCard
import com.framex.app.ui.screens.about.components.LegalListCard
import com.framex.app.ui.screens.about.components.PrivacyCommitmentCard

@Composable
fun AboutScreen(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    canInstallPackages: () -> Boolean,
    deviceModelInfo: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Color.White
                )
            }

            Text(
                text = "About & Legal",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp)
            )
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            AboutHeroCard(
                versionName = state.versionName,
                versionCode = state.versionCode
            )

            Spacer(modifier = Modifier.height(28.dp))

            AppUpdatesCard(
                autoUpdateEnabled = state.autoUpdateEnabled,
                isCheckingUpdate = state.isCheckingUpdate,
                statusMessage = state.statusMessage,
                versionName = state.versionName,
                onAutoUpdateToggled = { onEvent(AboutUiEvent.SetAutoUpdateCheck(it)) },
                onCheckForUpdates = { onEvent(AboutUiEvent.CheckForUpdates) }
            )

            Spacer(modifier = Modifier.height(28.dp))

            HardwareOptimizationCard(
                isVivoDevice = state.isVivoDevice,
                isVivoOptActive = state.isVivoOptActive,
                onToggleVivoOpt = { enabled ->
                    if (enabled) {
                        onEvent(AboutUiEvent.SetShowVivoDiagModal(true))
                    } else {
                        onEvent(AboutUiEvent.SetVivoOptEnabled(false))
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            ExecutionCenterSection(
                disableThermalThrottling = state.disableThermalThrottling,
                onToggleDisableThermalThrottling = { onEvent(AboutUiEvent.SetDisableThermalThrottling(it)) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrivacyCommitmentCard()

            if (state.hasCrashLog) {
                Spacer(modifier = Modifier.height(32.dp))
                CrashDiagnosticsCard(
                    onShareCrashLog = { onEvent(AboutUiEvent.ShareCrashLog) },
                    onClearCrashLog = { onEvent(AboutUiEvent.ClearCrashLog) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            LegalListCard(
                onOpenUrl = onOpenUrl
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Footer
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Powered by Shizuku API",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Modal Dialogs
    if (state.showVivoDiagModal) {
        VivoDiagnosticDialog(
            isVivoOrIqoo = state.isVivoDevice,
            deviceModelInfo = deviceModelInfo,
            onDismiss = { onEvent(AboutUiEvent.SetShowVivoDiagModal(false)) },
            onConfirmEnable = {
                onEvent(AboutUiEvent.SetVivoOptEnabled(true))
                onEvent(AboutUiEvent.SetShowVivoDiagModal(false))
            }
        )
    }

    state.updateInfoState?.let { info ->
        UpdateDialog(
            updateInfo = info,
            downloadState = state.downloadState,
            canInstallPackages = canInstallPackages,
            onRequestInstallPermission = {
                onEvent(AboutUiEvent.InstallDownloadedApk(state.pendingInstallApk ?: return@UpdateDialog))
            },
            onDownloadAndInstallClicked = {
                onEvent(AboutUiEvent.ResumeOrDownloadUpdate)
            },
            onCancelDownload = {
                onEvent(AboutUiEvent.CancelOrResetDownload)
            },
            onRemindLaterClicked = {
                onEvent(AboutUiEvent.SetUpdateInfo(null))
                onEvent(AboutUiEvent.CancelOrResetDownload)
            }
        )
    }

    state.signatureErrorMessage?.let { msg ->
        SignatureMismatchDialog(
            errorMessage = msg,
            onUninstallClicked = {
                onEvent(AboutUiEvent.HandleSignatureMismatchUninstall)
            },
            onDismiss = {
                onEvent(AboutUiEvent.SetSignatureError(null))
            }
        )
    }
}
