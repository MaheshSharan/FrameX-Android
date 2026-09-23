package com.framex.app.ui.screens.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.components.OverlayDisplayConfig
import com.framex.app.ui.components.OverlayPreviewContent
import com.framex.app.ui.screens.overlay.ModuleRowState

@Composable
fun OverlayPreviewCard(
    modules: List<ModuleRowState>,
    selectedMode: String,
    opacity: Float,
    accentColor: Color,
    colorIndex: Int,
    fontFamily: FontFamily?,
    textScale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(0.2f))
                        .border(1.dp, accentColor.copy(0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.overlay_preview_label),
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OverlayPreviewContent(
                    config = OverlayDisplayConfig(
                        mode = selectedMode,
                        enabledModules = modules.filter { it.enabled }.map { it.id.storageKey }.toSet(),
                        moduleOrder = modules.map { it.id.storageKey },
                        opacity = opacity,
                        overlayScale = textScale,
                        useMonospace = fontFamily == FontFamily.Monospace,
                        colorIndex = colorIndex,
                        enabledModuleIcons = modules.filter { it.showIcon }.map { it.id.storageKey }.toSet()
                    )
                )
            }
        }
    }
}
