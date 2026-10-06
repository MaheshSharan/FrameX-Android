package com.framex.app.ui.screens.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.components.SignatureMismatchDialog
import com.framex.app.ui.components.UpdateDialog
import com.framex.app.ui.screens.splash.components.SplashAnimatedLogo
import com.framex.app.ui.screens.splash.components.SplashProgressSection
import kotlinx.coroutines.delay

private val SplashBackgroundColor = Color(0xFF0A0A0A)

/**
 * High-performance 8-stage animated Splash Screen:
 * 1. Clockwise outer frame drawing from left stub with a glowing leading tip.
 * 2. Inner X building and contour stroke.
 * 3. Text reveal for FrameX and PERFORMANCE SUITE.
 * 4. Subtle neon bloom pulse and scale settle.
 * 5. 400ms pause, followed by circular loader appearance.
 * 6. Smooth update check state.
 * 7. In-place transition into checkmark status ("No updates found" / "Launching FrameX...").
 * 8. Automatic transition to dashboard or onboarding.
 */
@Composable
fun SplashScreen(
    state: SplashUiState,
    onEvent: (SplashUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLogoComplete by remember { mutableStateOf(false) }
    var showProgressSection by remember { mutableStateOf(false) }

    LaunchedEffect(isLogoComplete) {
        if (isLogoComplete) {
            // 400ms delay between logo pulse finish and loader start
            delay(400)
            showProgressSection = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Stages 1–4: Animated FrameX Master Logo
            SplashAnimatedLogo(
                onLogoComplete = { isLogoComplete = true }
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Stages 5–8: Progress Track & Status Completion
            AnimatedVisibility(
                visible = showProgressSection,
                enter = fadeIn(tween(350))
            ) {
                SplashProgressSection(
                    isCheckingUpdates = state.isCheckingUpdates,
                    onSequenceComplete = {
                        onEvent(SplashUiEvent.ProceedToNextScreen)
                    }
                )
            }
        }

        // Subdued Footer
        Text(
            text = stringResource(R.string.splash_powered_by_shizuku),
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        )

        // Update Dialog Over Splash
        state.updateInfoState?.let { info ->
            UpdateDialog(
                updateInfo = info,
                downloadState = state.downloadState,
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
