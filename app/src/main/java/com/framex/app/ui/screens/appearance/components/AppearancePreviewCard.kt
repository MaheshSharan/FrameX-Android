package com.framex.app.ui.screens.appearance.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.components.OverlayPreviewContent
import com.framex.app.ui.screens.appearance.AppearanceUiState
import com.framex.app.ui.screens.appearance.PreviewBackground

@Composable
fun AppearancePreviewCard(
    uiState: AppearanceUiState,
    onCycleBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = PreviewBackground.entries[
        uiState.previewBackgroundIndex.coerceIn(0, PreviewBackground.entries.lastIndex)
    ]
    val backgroundName = stringResource(background.labelRes)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.appearance_preview_header),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(
                text = stringResource(R.string.appearance_preview_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray.copy(alpha = 0.7f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background.brush)
                .clickable(
                    onClickLabel = stringResource(R.string.appearance_preview_bg_action),
                    role = Role.Button,
                    onClick = onCycleBackground
                )
                .semantics { stateDescription = backgroundName }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                OverlayPreviewContent(
                    mode = uiState.mode,
                    enabledModules = uiState.enabledModules,
                    enabledModuleIcons = uiState.enabledModuleIcons,
                    cpuHotWarningEnabled = uiState.cpuHotWarning,
                    moduleOrder = uiState.moduleOrder,
                    opacity = uiState.opacity,
                    textSize = uiState.textSize,
                    overlayScale = uiState.scale,
                    useMonospace = uiState.useMonospace,
                    colorIndex = uiState.colorIndex,
                    bgColorIndex = uiState.bgColorIndex,
                    borderColorIndex = uiState.borderColorIndex,
                    textColorIndex = uiState.textColorIndex
                )
            }
            Text(
                text = stringResource(R.string.appearance_preview_bg_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
