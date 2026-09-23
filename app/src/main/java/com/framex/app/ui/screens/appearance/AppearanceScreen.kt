package com.framex.app.ui.screens.appearance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.screens.appearance.components.AppearancePreviewCard
import com.framex.app.ui.screens.appearance.components.ContainerStyleCard
import com.framex.app.ui.screens.appearance.components.OpacityCard
import com.framex.app.ui.screens.appearance.components.ThemeCard
import com.framex.app.ui.screens.appearance.components.TypographyCard
import com.framex.app.ui.theme.PresetAccentColors

@Composable
fun AppearanceScreen(
    uiState: AppearanceUiState,
    onEvent: (AppearanceUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColors = PresetAccentColors
    val currentAccent = accentColors.getOrElse(uiState.colorIndex) { Color.White }
    val hasChanges = uiState.hasChanges

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with statusBarsPadding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp)) // Optical balance against Back button
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // Live Preview
                AppearancePreviewCard(uiState = uiState)

                Spacer(modifier = Modifier.height(24.dp))

                // Visibility / Opacity
                OpacityCard(
                    opacity = uiState.opacity,
                    colorIndex = uiState.colorIndex,
                    onOpacityChange = { onEvent(AppearanceUiEvent.SetOpacity(it)) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Container Background & Border
                ContainerStyleCard(
                    selectedBgColorIndex = uiState.bgColorIndex,
                    selectedBorderColorIndex = uiState.borderColorIndex,
                    accentColorIndex = uiState.colorIndex,
                    onBgColorSelect = { onEvent(AppearanceUiEvent.SetBgColorIndex(it)) },
                    onBorderColorSelect = { onEvent(AppearanceUiEvent.SetBorderColorIndex(it)) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Typography & Scale
                TypographyCard(
                    textSizeIndex = uiState.textSize,
                    scale = uiState.scale,
                    useMonospace = uiState.useMonospace,
                    accentColorIndex = uiState.colorIndex,
                    onTextSizeSelect = { onEvent(AppearanceUiEvent.SetTextSize(it)) },
                    onScaleChange = { onEvent(AppearanceUiEvent.SetScale(it)) },
                    onMonospaceToggle = { onEvent(AppearanceUiEvent.SetMonospace(it)) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Theme Colors & Alerts
                ThemeCard(
                    selectedColorIndex = uiState.colorIndex,
                    selectedTextColorIndex = uiState.textColorIndex,
                    cpuHotWarning = uiState.cpuHotWarning,
                    onColorSelect = { onEvent(AppearanceUiEvent.SetColorIndex(it)) },
                    onTextColorSelect = { onEvent(AppearanceUiEvent.SetTextColorIndex(it)) },
                    onCpuHotWarningToggle = { onEvent(AppearanceUiEvent.SetCpuHotWarning(it)) }
                )

                Spacer(modifier = Modifier.height(112.dp)) // Extra clearance for sticky button
            }
        }

        // Bottom Sticky Action Bar with navigationBarsPadding
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val buttonScale by animateFloatAsState(
                targetValue = if (isPressed) 0.97f else 1.0f,
                animationSpec = spring(stiffness = 500f),
                label = "applyButtonScale"
            )
            val buttonContainerColor by animateColorAsState(
                targetValue = if (hasChanges) currentAccent else Color.DarkGray.copy(alpha = 0.4f),
                label = "applyButtonBg"
            )

            Button(
                onClick = {
                    if (hasChanges) {
                        onEvent(AppearanceUiEvent.SaveChanges)
                    }
                },
                enabled = hasChanges,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainerColor,
                    contentColor = Color.White,
                    disabledContainerColor = Color.DarkGray.copy(alpha = 0.35f),
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
            ) {
                AnimatedContent(
                    targetState = hasChanges,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "applyButtonText"
                ) { targetHasChanges ->
                    Text(
                        text = stringResource(
                            if (targetHasChanges) R.string.appearance_apply_changes else R.string.appearance_applied
                        ),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
