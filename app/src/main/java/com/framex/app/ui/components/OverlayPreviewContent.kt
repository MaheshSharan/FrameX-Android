package com.framex.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.metrics.METRIC_MODULE_REGISTRY
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.metricValueFor
import com.framex.app.metrics.resolveMetricModuleOrder

@Composable
fun OverlayPreviewContent(
    mode: String,
    enabledModules: Set<String>,
    moduleOrder: List<String>,
    opacity: Float,
    textSize: Int = 1,
    overlayScale: Float = 1.0f,
    useMonospace: Boolean,
    colorIndex: Int,
    bgColorIndex: Int = 0,
    borderColorIndex: Int = 0,
    textColorIndex: Int = 0,
    metricsState: MetricsState? = null,
    modifier: Modifier = Modifier
) {
    val activeList = resolveMetricModuleOrder(moduleOrder)
        .filter { enabledModules.contains(it.storageKey) }
        .map { id ->
            val info = METRIC_MODULE_REGISTRY.getValue(id)
            val displayValue = if (metricsState != null) {
                metricValueFor(id, metricsState)
            } else {
                info.previewSampleValue
            }
            Triple(id.storageKey, info.overlayShortLabel, displayValue) to info.icon
        }

    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF60A5FA),
        Color(0xFF34D399),
        Color(0xFF2DD4BF),
        Color(0xFFA78BFA),
        Color(0xFFFBBF24)
    )
    val accentColor = colors.getOrElse(colorIndex) { MaterialTheme.colorScheme.primary }
    val fontFamily = if (useMonospace) FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily
    val textScale = if (overlayScale != 1.0f || textSize == 1) overlayScale else when (textSize) { 0 -> 0.8f; 2 -> 1.2f; else -> 1.0f }

    val bgColors = listOf(Color.Black, Color(0xFF0D1117), Color(0xFF1C1C1E), Color.Transparent)
    val bgBase = bgColors.getOrElse(bgColorIndex) { Color.Black }
    val effectiveBg = if (bgBase == Color.Transparent) Color.Transparent else bgBase.copy(alpha = opacity)

    val effectiveBorderColor = when (borderColorIndex) {
        1 -> Color.Transparent
        2 -> Color.White.copy(alpha = 0.2f)
        3 -> Color.White.copy(alpha = 0.05f)
        else -> accentColor
    }
    val effectiveBorderWidth = if (borderColorIndex == 1) 0.dp else 1.dp

    val currentFps = metricsState?.fps ?: 60
    val autoFpsColor = when {
        currentFps >= 60 -> Color(0xFF22C55E)
        currentFps >= 30 -> Color(0xFFFBBF24)
        else -> Color(0xFFEF4444)
    }
    val textValueColor = when (textColorIndex) {
        1 -> accentColor
        2 -> Color(0xFFCBD5E1)
        3 -> autoFpsColor
        else -> Color.White
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(effectiveBg)
            .border(effectiveBorderWidth, effectiveBorderColor, RoundedCornerShape(8.dp))
            .padding(if (mode == "Minimal") (4 * textScale).dp else (8 * textScale).dp)
    ) {
        if (mode == "Expanded") {
            Column(verticalArrangement = Arrangement.spacedBy((8 * textScale).dp)) {
                activeList.forEach { (info, icon) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size((16 * textScale).dp))
                        Spacer(modifier = Modifier.width((8 * textScale).dp))
                        Text(info.second, color = Color.Gray, fontSize = (10 * textScale).sp, fontFamily = fontFamily, modifier = Modifier.weight(1f))
                        Text(info.third, color = textValueColor, fontSize = (12 * textScale).sp, fontFamily = fontFamily, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy((12 * textScale).dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = (4 * textScale).dp)
            ) {
                activeList.forEachIndexed { index, (info, _) ->
                    if (mode == "Minimal") {
                        Text(info.third, color = textValueColor, fontFamily = fontFamily, fontSize = (14 * textScale).sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(info.second, color = Color.Gray, fontFamily = fontFamily, fontSize = (10 * textScale).sp, fontWeight = FontWeight.Bold)
                            Text(info.third, color = textValueColor, fontFamily = fontFamily, fontSize = (16 * textScale).sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    if (index < activeList.size - 1) {
                        Box(modifier = Modifier.width(1.dp).height(if (mode == "Minimal") (12 * textScale).dp else (24 * textScale).dp).background(Color.DarkGray))
                    }
                }
                if (activeList.isEmpty()) {
                    Text("No modules", color = Color.Gray, fontFamily = fontFamily, fontSize = (12 * textScale).sp)
                }
            }
        }
    }
}
