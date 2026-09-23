package com.framex.app.ui.screens.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.components.SignatureMismatchDialog
import com.framex.app.ui.components.UpdateDialog

@Composable
fun SplashScreen(
    state: SplashUiState,
    onEvent: (SplashUiEvent) -> Unit,
    canInstallPackages: () -> Boolean,
    modifier: Modifier = Modifier
) {
    var animationStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animationStarted = true
    }

    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splashLogoScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "splashLogoAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
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
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = state.isCheckingUpdates,
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

        // Update Dialog Over Splash
        state.updateInfoState?.let { info ->
            UpdateDialog(
                updateInfo = info,
                downloadState = state.downloadState,
                canInstallPackages = canInstallPackages,
                onRequestInstallPermission = {
                    onEvent(SplashUiEvent.InstallDownloadedApk(state.pendingInstallApk ?: return@UpdateDialog))
                },
                onDownloadAndInstallClicked = {
                    onEvent(SplashUiEvent.ResumeOrDownloadUpdate)
                },
                onCancelDownload = {
                    onEvent(SplashUiEvent.CancelOrResetDownload)
                },
                onRemindLaterClicked = {
                    onEvent(SplashUiEvent.DismissUpdateDialog)
                }
            )
        }

        // Signature Mismatch Dialog
        state.signatureErrorMessage?.let { msg ->
            SignatureMismatchDialog(
                errorMessage = msg,
                onUninstallClicked = {
                    onEvent(SplashUiEvent.HandleSignatureMismatchUninstall)
                },
                onDismiss = {
                    onEvent(SplashUiEvent.DismissSignatureError)
                }
            )
        }
    }
}
