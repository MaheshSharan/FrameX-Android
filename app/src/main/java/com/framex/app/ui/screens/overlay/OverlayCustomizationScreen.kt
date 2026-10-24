package com.framex.app.ui.screens.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.components.FrameXApplyButton
import com.framex.app.ui.components.FrameXTopBar
import com.framex.app.ui.components.ReorderableList
import com.framex.app.ui.screens.overlay.components.ModeSelector
import com.framex.app.ui.screens.overlay.components.ModuleRow
import com.framex.app.ui.screens.overlay.components.OverlayPreviewCard
import com.framex.app.ui.theme.FrameXSpacing
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
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = FrameXSpacing.MaxContentWidth)
                .fillMaxSize()
        ) {
            // Header Bar
            FrameXTopBar(
                title = stringResource(R.string.overlay_config_title),
                onNavigateBack = onNavigateBack
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Mode Selector
            ModeSelector(
                selectedMode = uiState.selectedMode,
                accentColor = accentColor,
                onModeSelected = { onEvent(OverlayCustomizationUiEvent.SelectMode(it)) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

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

            Spacer(modifier = Modifier.height(20.dp))

            // Active Modules Title & Outlined Reset Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.overlay_active_modules),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Outlined Reset Button (48dp minimum touch target via minimumInteractiveComponentSize before clip)
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable(
                                role = Role.Button,
                                onClick = { onEvent(OverlayCustomizationUiEvent.ResetToDefaultOrder) }
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = stringResource(R.string.overlay_reset_order_cd),
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.overlay_reset_btn),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.overlay_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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

        // Bottom Sticky Action Bar with Unified Apply Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            FrameXApplyButton(
                hasChanges = uiState.hasChanges,
                accentColor = accentColor,
                onClick = { onEvent(OverlayCustomizationUiEvent.SaveSettings) },
                modifier = Modifier.widthIn(max = FrameXSpacing.MaxContentWidth)
            )
        }
    }
}
