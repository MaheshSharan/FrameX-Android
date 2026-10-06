package com.framex.app.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.framex.app.ui.components.SignatureMismatchDialog
import com.framex.app.ui.components.UpdateDialog
import com.framex.app.ui.components.VivoDiagnosticDialog
import com.framex.app.ui.screens.about.components.AboutHeroCard
import com.framex.app.ui.screens.about.components.AppUpdatesCard
import com.framex.app.ui.screens.about.components.CrashDiagnosticsCard
import com.framex.app.ui.screens.about.components.CreatorCard
import com.framex.app.ui.screens.about.components.HardwareOptimizationCard
import com.framex.app.ui.screens.about.components.LegalDialogType
import com.framex.app.ui.screens.about.components.LegalInfoDialog
import com.framex.app.ui.screens.about.components.LegalListCard
import com.framex.app.ui.screens.about.components.PrivacyCommitmentCard

private val DarkBackground = Color(0xFF0C0D12)

/**
 * About & Legal root screen.
 * Contains Hero section, Application Updates, Creator info,
 * Hardware Optimizations, Execution Center, and Legal resources with in-app dialogs.
 */
@Composable
fun AboutScreen(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    deviceModelInfo: String,
    modifier: Modifier = Modifier
) {
    var activeLegalDialog by remember { mutableStateOf<LegalDialogType?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            AboutHeroCard(
                versionName = state.versionName,
                versionCode = state.versionCode,
                onNavigateBack = onNavigateBack,
                onOpenUrl = onOpenUrl
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                AppUpdatesCard(
                    autoUpdateEnabled = state.autoUpdateEnabled,
                    isCheckingUpdate = state.isCheckingUpdate,
                    statusMessage = state.statusMessage,
                    versionName = state.versionName,
                    versionCode = state.versionCode,
                    onAutoUpdateToggled = { onEvent(AboutUiEvent.SetAutoUpdateCheck(it)) },
                    onCheckForUpdates = { onEvent(AboutUiEvent.CheckForUpdates) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                CreatorCard(
                    onOpenUrl = onOpenUrl
                )

                Spacer(modifier = Modifier.height(24.dp))

                HardwareOptimizationCard(
                    isVivoDevice = state.isVivoDevice,
                    isIqooDevice = state.isIqooDevice,
                    isVivoOptActive = state.isVivoOptActive,
                    onToggleVivoOpt = { enabled ->
                        if (enabled) {
                            onEvent(AboutUiEvent.SetShowVivoDiagModal(true))
                        } else {
                            onEvent(AboutUiEvent.SetVivoOptEnabled(false))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                ExecutionCenterSection(
                    state = state,
                    onEvent = onEvent
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrivacyCommitmentCard()

                Spacer(modifier = Modifier.height(24.dp))

                LegalListCard(
                    onSelectLegalItem = { type -> activeLegalDialog = type }
                )

                if (state.hasCrashLog) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CrashDiagnosticsCard(
                        onShareCrashLog = { onEvent(AboutUiEvent.ShareCrashLog) },
                        onClearCrashLog = { onEvent(AboutUiEvent.ClearCrashLog) }
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        AboutScreenDialogs(
            state = state,
            onEvent = onEvent,
            deviceModelInfo = deviceModelInfo
        )

        activeLegalDialog?.let { legalType ->
            LegalInfoDialog(
                type = legalType,
                onDismiss = { activeLegalDialog = null },
                onOpenUrl = onOpenUrl
            )
        }
    }
}

@Composable
private fun AboutScreenDialogs(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit,
    deviceModelInfo: String
) {
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
