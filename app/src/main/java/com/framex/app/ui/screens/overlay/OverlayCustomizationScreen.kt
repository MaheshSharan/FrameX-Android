package com.framex.app.ui.screens.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.components.ReorderableList
import com.framex.app.ui.screens.overlay.components.ModeSelector
import com.framex.app.ui.screens.overlay.components.ModuleRow
import com.framex.app.ui.screens.overlay.components.OverlayPreviewCard
import com.framex.app.ui.theme.getAccentColor

/**
 * Pure, stateless screen for overlay appearance and metric module customization.
 */
@Composable
fun OverlayCustomizationScreen(
    uiState: OverlayCustomizationUiState,
    onEvent: (OverlayCustomizationUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = getAccentColor(uiState.colorIndex)
    val fontFamily = if (uiState.useMonospace) FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
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
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.overlay_config_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Selector
            ModeSelector(
                modes = OVERLAY_MODES,
                selectedMode = uiState.selectedMode,
                accentColor = accentColor,
                onModeSelected = { onEvent(OverlayCustomizationUiEvent.SelectMode(it)) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Preview Card
            OverlayPreviewCard(
                modules = uiState.modules,
                selectedMode = uiState.selectedMode,
                opacity = uiState.opacity,
                accentColor = accentColor,
                colorIndex = uiState.colorIndex,
                fontFamily = fontFamily,
                textScale = uiState.overlayScale,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Active Modules Title & Reset Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.overlay_active_modules),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.overlay_reorder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                IconButton(
                    onClick = { onEvent(OverlayCustomizationUiEvent.ResetToDefaultOrder) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = stringResource(R.string.overlay_reset_order_cd),
                        tint = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reorderable list of active modules
            ReorderableList(
                items = uiState.modules,
                key = { it.id.storageKey },
                itemHeight = MODULE_ROW_HEIGHT,
                itemSpacing = MODULE_ROW_SPACING,
                minReorderIndex = 1,
                onMove = { from, to ->
                    onEvent(OverlayCustomizationUiEvent.ReorderModules(from, to))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) { module, dragHandleModifier, isDragging ->
                ModuleRow(
                    module = module,
                    accentColor = accentColor,
                    isDragging = isDragging,
                    dragHandleModifier = dragHandleModifier,
                    onEnabledChanged = { isChecked ->
                        onEvent(OverlayCustomizationUiEvent.ToggleModuleEnabled(module.id, isChecked))
                    },
                    onToggleIcon = {
                        onEvent(OverlayCustomizationUiEvent.ToggleModuleIcon(module.id))
                    }
                )
            }
        }

        // Bottom Action Bar with Animated State
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Button(
                onClick = { onEvent(OverlayCustomizationUiEvent.SaveSettings) },
                enabled = uiState.hasChanges,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = Color.Gray
                ),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
            ) {
                AnimatedContent(
                    targetState = uiState.hasChanges,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "saveBtnAnim"
                ) { hasChanges ->
                    Text(
                        text = if (hasChanges) {
                            stringResource(R.string.action_apply_changes)
                        } else {
                            stringResource(R.string.action_applied)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
