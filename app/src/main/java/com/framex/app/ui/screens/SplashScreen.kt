package com.framex.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.framex.app.repository.SettingsRepository
import com.framex.app.ui.components.SignatureMismatchDialog
import com.framex.app.ui.components.UpdateDialog
import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.DownloadState
import com.framex.app.update.InstallResult
import com.framex.app.update.UpdateInstaller
import com.framex.app.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    val updateRepository: UpdateRepository,
    val updateInstaller: UpdateInstaller
) : ViewModel() {
    val isOnboardingCompleted = settingsRepository.isOnboardingCompleted
    val autoUpdateCheckEnabled = settingsRepository.autoUpdateCheckEnabled
}

@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()

    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateCheckEnabled.collectAsState()

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfoState by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var signatureErrorMessage by remember { mutableStateOf<String?>(null) }

    fun proceedToNextScreen() {
        if (isOnboardingCompleted) {
            onNavigateToDashboard()
        } else {
            onNavigateToOnboarding()
        }
    }

    LaunchedEffect(key1 = true) {
        if (autoUpdateEnabled) {
            isCheckingUpdates = true
            scope.launch(Dispatchers.Main) {
                val result = viewModel.updateRepository.checkForUpdates()
                isCheckingUpdates = false
                result.onSuccess { info ->
                    if (info.isUpdateAvailable) {
                        updateInfoState = info
                    } else {
                        delay(600)
                        proceedToNextScreen()
                    }
                }.onFailure {
                    delay(600)
                    proceedToNextScreen()
                }
            }
            delay(1500)
        } else {
            delay(1500)
            proceedToNextScreen()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = com.framex.app.R.mipmap.ic_launcher),
                contentDescription = "App Logo",
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "FrameX",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Text(
                text = "PERFORMANCE SUITE",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }

        // Minimal Update Loader & Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = isCheckingUpdates,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Checking for updates...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = "Powered by Shizuku",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            )
        }

    var downloadedApkFile by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(Unit) {
        com.framex.app.update.UpdateInstallerBus.installEvents.collect { result ->
            when (result) {
                is InstallResult.SignatureMismatch -> {
                    signatureErrorMessage = result.errorMessage
                    updateInfoState = null
                }
                is InstallResult.PermissionRequired -> {
                    viewModel.updateInstaller.openUnknownAppSourcesSettings()
                }
                else -> {}
            }
        }
    }

        // Update Dialog Over Splash
        updateInfoState?.let { info ->
            UpdateDialog(
                updateInfo = info,
                downloadState = downloadState,
                onDownloadAndInstallClicked = {
                    if (!viewModel.updateInstaller.canInstallPackages()) {
                        viewModel.updateInstaller.openUnknownAppSourcesSettings()
                    } else {
                        scope.launch {
                            viewModel.updateRepository.downloadUpdateApk(info.downloadUrl, info.versionName)
                                .collect { state ->
                                    downloadState = state
                                    if (state is DownloadState.Completed) {
                                        downloadedApkFile = state.apkFile
                                        viewModel.updateInstaller.installApk(state.apkFile) { installRes ->
                                            when (installRes) {
                                                is InstallResult.PermissionRequired -> {
                                                    viewModel.updateInstaller.openUnknownAppSourcesSettings()
                                                }
                                                is InstallResult.SignatureMismatch -> {
                                                    signatureErrorMessage = installRes.errorMessage
                                                    updateInfoState = null
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                        }
                    }
                },
                onRemindLaterClicked = {
                    updateInfoState = null
                    downloadState = DownloadState.Idle
                    proceedToNextScreen()
                }
            )
        }

        // Signature Mismatch Dialog
        signatureErrorMessage?.let { msg ->
            SignatureMismatchDialog(
                errorMessage = msg,
                onUninstallClicked = {
                    scope.launch {
                        val targetVer = updateInfoState?.versionName ?: "1.5.3"
                        viewModel.updateInstaller.handleSignatureMismatch(downloadedApkFile, targetVer) {
                            signatureErrorMessage = null
                            updateInfoState = null
                            proceedToNextScreen()
                        }
                    }
                },
                onDismiss = {
                    signatureErrorMessage = null
                    proceedToNextScreen()
                }
            )
        }
    }
}
