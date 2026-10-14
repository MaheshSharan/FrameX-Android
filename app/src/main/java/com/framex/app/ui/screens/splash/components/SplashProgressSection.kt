package com.framex.app.ui.screens.splash.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.screens.splash.SplashLoaderPhase
import kotlinx.coroutines.delay

/**
 * Stages 5–8 of the splash sequence:
 * Coordinates the update-verification delay and completes the transition.
 */
@Composable
fun SplashProgressSection(
    isCheckingUpdates: Boolean,
    hasUpdateAvailable: Boolean = false,
    onSequenceComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf(SplashLoaderPhase.CHECKING) }
    val currentIsChecking by rememberUpdatedState(isCheckingUpdates)
    val currentHasUpdate by rememberUpdatedState(hasUpdateAvailable)
    val currentOnComplete by rememberUpdatedState(onSequenceComplete)

    // Observes live update check progress and ensures a smooth minimum
    // display window before transitioning to completion or yielding to dialog.
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (currentIsChecking) {
            delay(100)
        }
        val elapsed = System.currentTimeMillis() - startTime
        val minDisplayDuration = 900L
        if (elapsed < minDisplayDuration) {
            delay(minDisplayDuration - elapsed)
        }

        if (!currentHasUpdate) {
            phase = SplashLoaderPhase.COMPLETE
            delay(650)
            currentOnComplete()
        }
    }

    SplashStatusIndicator(
        phase = phase,
        modifier = modifier
    )
}

/**
 * Universal unified status component that displays a circular progress loader
 * and transitions into the completion checkmark at the identical anchor position.
 */
@Composable
fun SplashStatusIndicator(
    phase: SplashLoaderPhase,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Universal indicator container: Circular loader morphs into Tick at the exact same location
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                },
                label = "splashStatusIndicator"
            ) { currentPhase ->
                when (currentPhase) {
                    SplashLoaderPhase.CHECKING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                            trackColor = Color.White.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                    SplashLoaderPhase.COMPLETE -> {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status text container
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
            },
            label = "splashStatusText"
        ) { currentPhase ->
            when (currentPhase) {
                SplashLoaderPhase.CHECKING -> {
                    Text(
                        text = stringResource(R.string.splash_checking_updates),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                SplashLoaderPhase.COMPLETE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.splash_no_updates_found),
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.splash_launching_framex),
                            color = Color.White.copy(alpha = 0.50f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

