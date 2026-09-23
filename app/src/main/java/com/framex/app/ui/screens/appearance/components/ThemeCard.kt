package com.framex.app.ui.screens.appearance.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.screens.appearance.TextColorOption
import com.framex.app.ui.theme.PresetAccentColors

// Zero-allocation static gradient for the Auto Value Color swatch
private val autoDynamicFpsGradient = Brush.sweepGradient(
    listOf(Color(0xFF22C55E), Color(0xFFEAB308), Color(0xFFEF4444), Color(0xFF22C55E))
)

@Composable
fun ThemeCard(
    selectedColorIndex: Int,
    selectedTextColorIndex: Int,
    cpuHotWarning: Boolean,
    onColorSelect: (Int) -> Unit,
    onTextColorSelect: (Int) -> Unit,
    onCpuHotWarningToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColors = PresetAccentColors
    val currentAccent = accentColors.getOrElse(selectedColorIndex) { Color.White }
    val textColorOptions = remember { TextColorOption.entries }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.appearance_theme_header),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Accent Color Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.appearance_accent_color_title),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.appearance_custom_label),
                        color = currentAccent,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    accentColors.forEachIndexed { index, color ->
                        val isSelected = selectedColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                    contentDescription = "Accent color option ${index + 1}"
                                }
                                .clickable { onColorSelect(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Metric Value Text Color
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.appearance_value_color_title),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    val activeTextColorOption = textColorOptions.getOrNull(selectedTextColorIndex) ?: TextColorOption.WHITE
                    Text(
                        text = stringResource(activeTextColorOption.labelRes),
                        color = if (selectedTextColorIndex == 3) Color(0xFF22C55E) else currentAccent,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val textColorSamples = remember(currentAccent) {
                    listOf(Color.White, currentAccent, Color(0xFFCBD5E1), Color(0xFF22C55E))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    textColorOptions.forEachIndexed { index, option ->
                        val isSelected = selectedTextColorIndex == index
                        val isAuto = option == TextColorOption.AUTO
                        val col = textColorSamples.getOrElse(index) { Color.White }
                        val label = stringResource(option.labelRes)

                        val bgModifier = if (isAuto) {
                            Modifier.background(autoDynamicFpsGradient)
                        } else {
                            Modifier.background(col.copy(alpha = 0.15f))
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(bgModifier)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else if (isAuto) Color.White.copy(alpha = 0.4f) else col.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                    contentDescription = if (isAuto) {
                                        "Auto Value Color: Dynamically colors metrics based on live FPS performance"
                                    } else {
                                        "$label Value Color"
                                    }
                                }
                                .clickable { onTextColorSelect(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isAuto) {
                                Text(
                                    text = label.take(1),
                                    color = col,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // CPU Hot Warning Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = cpuHotWarning,
                            role = Role.Switch,
                            onValueChange = onCpuHotWarningToggle
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.appearance_cpu_hot_title),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.appearance_cpu_hot_desc),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = cpuHotWarning,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentAccent
                        )
                    )
                }
            }
        }
    }
}
