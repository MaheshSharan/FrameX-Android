package com.framex.app.ui.screens.overlay.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R

private data class ModeCardItem(
    val name: String,
    val icon: ImageVector,
    val descRes: Int
)

private val MODE_ITEMS = listOf(
    ModeCardItem("Minimal", Icons.Outlined.ViewStream, R.string.overlay_mode_minimal_desc),
    ModeCardItem("Compact", Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.overlay_mode_compact_desc),
    ModeCardItem("Expanded", Icons.Outlined.GridView, R.string.overlay_mode_expanded_desc)
)

/**
 * Three-card layout mode selector with icon, mode title, and contextual description.
 */
@Composable
fun ModeSelector(
    selectedMode: String,
    accentColor: Color,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.overlay_layout_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MODE_ITEMS.forEach { item ->
                val isSelected = selectedMode == item.name

                val bgCardColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(200),
                    label = "modeCardBg"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                    animationSpec = tween(200),
                    label = "modeCardBorder"
                )
                val iconTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color(0xFF9E9E9E),
                    animationSpec = tween(200),
                    label = "modeCardIconText"
                )
                val descTextColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color(0xFF6B7280),
                    animationSpec = tween(200),
                    label = "modeCardDesc"
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgCardColor)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .semantics {
                                this.selected = isSelected
                            }
                            .clickable(
                                role = Role.Tab,
                                onClick = { onModeSelected(item.name) }
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = iconTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.name,
                                color = iconTextColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(item.descRes),
                        color = descTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}
