package com.framex.app.ui.screens.appearance.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.screens.appearance.TextSizeOption
import com.framex.app.ui.theme.PresetAccentColors

@Composable
fun TypographyCard(
    textSizeIndex: Int,
    scale: Float,
    useMonospace: Boolean,
    accentColorIndex: Int,
    onTextSizeSelect: (Int) -> Unit,
    onScaleChange: (Float) -> Unit,
    onMonospaceToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = PresetAccentColors.getOrElse(accentColorIndex) { Color.White }
    val sizeOptions = remember { TextSizeOption.entries }
    val percentText = "${(scale * 100).toInt()}%"

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.appearance_typography_header),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.appearance_text_size_scale_title),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = percentText,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Segmented size tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(4.dp)
                    ) {
                        sizeOptions.forEachIndexed { index, option ->
                            val isSelected = textSizeIndex == index
                            val optionLabel = stringResource(option.labelRes)
                            val tabBg by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                label = "sizeTabBg"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(tabBg)
                                    .semantics {
                                        role = Role.RadioButton
                                        selected = isSelected
                                        contentDescription = "$optionLabel text size"
                                    }
                                    .clickable { onTextSizeSelect(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = optionLabel,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = scale,
                        onValueChange = onScaleChange,
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Overlay text scale $percentText"
                            },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                // Monospace Metrics Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = useMonospace,
                            role = Role.Switch,
                            onValueChange = onMonospaceToggle
                        )
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = stringResource(R.string.appearance_monospace_title),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.appearance_monospace_desc),
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Switch(
                        checked = useMonospace,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor
                        )
                    )
                }
            }
        }
    }
}
