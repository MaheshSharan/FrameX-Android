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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.metrics.METRIC_MODULE_REGISTRY
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.isIconShown
import com.framex.app.metrics.metricValueFor
import com.framex.app.metrics.resolveMetricModuleOrder
import com.framex.app.metrics.resolveModuleIcon

private data class MetricRenderItem(
    val storageKey: String,
    val shortLabel: String,
    val displayValue: String,
    val icon: ImageVector,
    val showIcon: Boolean
)

private const val MINIMAL_SINGLE_ROW_MAX_ITEMS = 4
private const val COMPACT_SINGLE_ROW_MAX_ITEMS = 3

@OptIn(ExperimentalLayoutApi::class)
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
    enabledModuleIcons: Set<String> = emptySet(),
    cpuHotWarningEnabled: Boolean = true,
    metricsState: MetricsState? = null,
    modifier: Modifier = Modifier
) {
    val activeList = resolveMetricModuleOrder(moduleOrder)
        .filter { enabledModules.contains(it.storageKey) }
        .map { id ->
            val info = METRIC_MODULE_REGISTRY.getValue(id)
            val icon = resolveModuleIcon(id, metricsState)
            val displayValue = if (metricsState != null) {
                metricValueFor(id, metricsState, cpuHotWarningEnabled)
            } else {
                info.previewSampleValue
            }
            val showIcon = isIconShown(enabledModuleIcons, id.storageKey)
            MetricRenderItem(
                storageKey = id.storageKey,
                shortLabel = info.overlayShortLabel,
                displayValue = displayValue,
                icon = icon,
                showIcon = showIcon
            )
        }

    val accentColor = com.framex.app.ui.theme.getAccentColor(colorIndex)
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
        if (activeList.isEmpty()) {
            EmptyModulesText(textScale, fontFamily)
        } else {
            when (mode) {
                "Expanded" -> {
                    Column(
                        modifier = Modifier.widthIn(min = (130 * textScale).dp, max = (200 * textScale).dp),
                        verticalArrangement = Arrangement.spacedBy((6 * textScale).dp)
                    ) {
                        activeList.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.showIcon) {
                                    Icon(
                                        item.icon,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size((14 * textScale).dp)
                                    )
                                    Spacer(modifier = Modifier.width((6 * textScale).dp))
                                }
                                Text(
                                    text = item.shortLabel,
                                    color = Color.Gray,
                                    fontSize = (10 * textScale).sp,
                                    fontFamily = fontFamily,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width((8 * textScale).dp))
                                Text(
                                    text = item.displayValue,
                                    color = textValueColor,
                                    fontSize = (12 * textScale).sp,
                                    fontFamily = fontFamily,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
                "Minimal" -> {
                    if (activeList.size <= MINIMAL_SINGLE_ROW_MAX_ITEMS) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((8 * textScale).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = (4 * textScale).dp)
                        ) {
                            activeList.forEachIndexed { index, item ->
                                MinimalMetricItem(item, textScale, accentColor, textValueColor, fontFamily)
                                if (index < activeList.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height((12 * textScale).dp)
                                            .background(Color.DarkGray)
                                    )
                                }
                            }
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy((8 * textScale).dp),
                            verticalArrangement = Arrangement.spacedBy((4 * textScale).dp),
                            modifier = Modifier.padding(horizontal = (4 * textScale).dp)
                        ) {
                            activeList.forEach { item ->
                                MinimalMetricItem(item, textScale, accentColor, textValueColor, fontFamily)
                            }
                        }
                    }
                }
                else -> { // Compact
                    if (activeList.size <= COMPACT_SINGLE_ROW_MAX_ITEMS) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((10 * textScale).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = (4 * textScale).dp)
                        ) {
                            activeList.forEachIndexed { index, item ->
                                CompactMetricItem(item, textScale, accentColor, textValueColor, fontFamily)
                                if (index < activeList.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height((24 * textScale).dp)
                                            .background(Color.DarkGray)
                                    )
                                }
                            }
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy((10 * textScale).dp),
                            verticalArrangement = Arrangement.spacedBy((6 * textScale).dp),
                            modifier = Modifier.padding(horizontal = (4 * textScale).dp)
                        ) {
                            activeList.forEach { item ->
                                CompactMetricItem(item, textScale, accentColor, textValueColor, fontFamily)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyModulesText(textScale: Float, fontFamily: FontFamily?) {
    Text(
        text = "No modules",
        color = Color.Gray,
        fontFamily = fontFamily,
        fontSize = (12 * textScale).sp,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun MinimalMetricItem(
    item: MetricRenderItem,
    textScale: Float,
    accentColor: Color,
    textValueColor: Color,
    fontFamily: FontFamily?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.showIcon) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size((13 * textScale).dp)
            )
            Spacer(modifier = Modifier.width((4 * textScale).dp))
        }
        Text(
            text = item.displayValue,
            color = textValueColor,
            fontFamily = fontFamily,
            fontSize = (14 * textScale).sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun CompactMetricItem(
    item: MetricRenderItem,
    textScale: Float,
    accentColor: Color,
    textValueColor: Color,
    fontFamily: FontFamily?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.showIcon) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size((10 * textScale).dp)
                )
                Spacer(modifier = Modifier.width((3 * textScale).dp))
            }
            Text(
                text = item.shortLabel,
                color = Color.Gray,
                fontFamily = fontFamily,
                fontSize = (10 * textScale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = item.displayValue,
            color = textValueColor,
            fontFamily = fontFamily,
            fontSize = (16 * textScale).sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}
