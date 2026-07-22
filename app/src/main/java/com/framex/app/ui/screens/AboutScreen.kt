package com.framex.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.ui.components.VivoDiagnosticDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    val deviceDiagnosticManager: DeviceDiagnosticManager,
    val updateRepository: com.framex.app.update.UpdateRepository,
    val updateInstaller: com.framex.app.update.UpdateInstaller
) : ViewModel() {
    val vivoOptEnabled = settingsRepository.vivoOptEnabled
    val autoUpdateCheckEnabled = settingsRepository.autoUpdateCheckEnabled

    fun setVivoOptEnabled(enabled: Boolean) {
        settingsRepository.setVivoOptEnabled(enabled)
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        settingsRepository.setAutoUpdateCheckEnabled(enabled)
    }
}

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    val autoUpdateEnabled by viewModel.autoUpdateCheckEnabled.collectAsState()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoState by remember { mutableStateOf<com.framex.app.update.AppUpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<com.framex.app.update.DownloadState>(com.framex.app.update.DownloadState.Idle) }
    var signatureErrorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val packageInfo = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    } catch (e: Exception) {
        null
    }
    val versionName = packageInfo?.versionName ?: com.framex.app.BuildConfig.VERSION_NAME
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode ?: 9L
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toLong() ?: 9L
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .background(Color.White.copy(0.05f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "About & Legal",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(end = 48.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Hero
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(2.dp, accentColor.copy(0.2f), RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "FrameX Logo",
                    modifier = Modifier.size(80.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("FrameX", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("v$versionName (Build $versionCode)", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Contact Button
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MaheshSharan"))
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = CircleShape,
                modifier = Modifier
                    .border(1.dp, Color.White.copy(0.1f), CircleShape)
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Mail, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Contact Developer", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App Updates Card
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Application Updates", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(24.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-check for updates", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Check GitHub releases automatically on app startup.", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.setAutoUpdateCheckEnabled(checked)
                                }
                            )
                        }

                        HorizontalDivider(
                            color = Color.White.copy(0.06f),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = statusMessage ?: "Current Version: v$versionName",
                                fontSize = 12.sp,
                                color = if (statusMessage != null) accentColor else Color.Gray,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    isCheckingUpdate = true
                                    statusMessage = "Checking GitHub..."
                                    scope.launch {
                                        val result = viewModel.updateRepository.checkForUpdates()
                                        isCheckingUpdate = false
                                        result.onSuccess { info ->
                                            if (info.isUpdateAvailable) {
                                                updateInfoState = info
                                                statusMessage = "Update available: v${info.versionName}"
                                            } else {
                                                statusMessage = "FrameX is up to date (v$versionName)"
                                            }
                                        }.onFailure { err ->
                                            statusMessage = err.localizedMessage ?: "Check failed"
                                        }
                                    }
                                },
                                enabled = !isCheckingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.15f), contentColor = accentColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Check for Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Hardware Optimization Card (Vivo / iQOO Diagnostic)
            val isVivoOptActive by viewModel.vivoOptEnabled.collectAsState()
            var showVivoDiagModal by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Hardware Optimizations", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vivo / iQOO Hardware Optimizations", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Enable OriginOS / FuntouchOS OEM power governor overrides and touch boost.", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isVivoOptActive,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showVivoDiagModal = true
                                } else {
                                    viewModel.setVivoOptEnabled(false)
                                }
                            }
                        )
                    }
                }
            }

            if (showVivoDiagModal) {
                val isVivoDevice = viewModel.deviceDiagnosticManager.isVivoOrIqoo()
                val modelInfo = viewModel.deviceDiagnosticManager.getDeviceModelInfo()
                VivoDiagnosticDialog(
                    isVivoOrIqoo = isVivoDevice,
                    deviceModelInfo = modelInfo,
                    onDismiss = { showVivoDiagModal = false },
                    onConfirmEnable = { viewModel.setVivoOptEnabled(true) }
                )
            }

    var downloadedApkFile by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(Unit) {
        com.framex.app.update.UpdateInstallerBus.installEvents.collect { result ->
            when (result) {
                is com.framex.app.update.InstallResult.SignatureMismatch -> {
                    signatureErrorMessage = result.errorMessage
                    updateInfoState = null
                }
                is com.framex.app.update.InstallResult.PermissionRequired -> {
                    viewModel.updateInstaller.openUnknownAppSourcesSettings()
                }
                else -> {}
            }
        }
    }

            // Update Dialog
            updateInfoState?.let { info ->
                com.framex.app.ui.components.UpdateDialog(
                    updateInfo = info,
                    downloadState = downloadState,
                    onDownloadAndInstallClicked = {
                        if (!viewModel.updateInstaller.canInstallPackages()) {
                            viewModel.updateInstaller.openUnknownAppSourcesSettings()
                            statusMessage = "Please allow unknown app installation, then tap Download & Install again."
                        } else {
                            scope.launch {
                                viewModel.updateRepository.downloadUpdateApk(info.downloadUrl, info.versionName)
                                    .collect { state ->
                                        downloadState = state
                                        if (state is com.framex.app.update.DownloadState.Completed) {
                                            downloadedApkFile = state.apkFile
                                            viewModel.updateInstaller.installApk(state.apkFile) { installRes ->
                                                when (installRes) {
                                                    is com.framex.app.update.InstallResult.PermissionRequired -> {
                                                        viewModel.updateInstaller.openUnknownAppSourcesSettings()
                                                    }
                                                    is com.framex.app.update.InstallResult.SignatureMismatch -> {
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
                        downloadState = com.framex.app.update.DownloadState.Idle
                    }
                )
            }

            // Signature Mismatch Dialog
            signatureErrorMessage?.let { msg ->
                com.framex.app.ui.components.SignatureMismatchDialog(
                    errorMessage = msg,
                    onUninstallClicked = {
                        scope.launch {
                            val targetVer = updateInfoState?.versionName ?: com.framex.app.BuildConfig.VERSION_NAME
                            viewModel.updateInstaller.handleSignatureMismatch(downloadedApkFile, targetVer) {
                                signatureErrorMessage = null
                                updateInfoState = null
                            }
                        }
                    },
                    onDismiss = {
                        signatureErrorMessage = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Privacy Card
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Our Privacy Commitment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CommitmentRow(title = "No user data collected", subtitle = "Your personal info stays on device.")
                        CommitmentRow(title = "No background tracking", subtitle = "The app sleeps when you do.")
                        CommitmentRow(title = "No ads - ever", subtitle = "FrameX is completely ad-free.")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Legal List
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Legal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(24.dp))
                ) {
                    LegalListItem(
                        icon = Icons.Default.Policy,
                        title = "Privacy Policy",
                        url = "https://maheshsharan.github.io/FrameX-Android/privacy-policy"
                    )
                    HorizontalDivider(color = Color.White.copy(0.05f))
                    LegalListItem(
                        icon = Icons.Default.Code,
                        title = "Open-source License",
                        url = "https://github.com/MaheshSharan/FrameX-Android/blob/main/LICENSE"
                    )
                    HorizontalDivider(color = Color.White.copy(0.05f))
                    LegalListItem(
                        icon = Icons.Default.Gavel,
                        title = "Known Limitations",
                        url = "https://github.com/MaheshSharan/FrameX-Android/blob/main/KNOWN_LIMITATIONS.md"
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            // Footer
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Powered by Shizuku API", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun CommitmentRow(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape)
                .padding(4.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun LegalListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    url: String = ""
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (url.isNotEmpty()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.DarkGray)
    }
}
