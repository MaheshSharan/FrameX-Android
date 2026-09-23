package com.framex.app.ui.screens.appearance.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.screens.appearance.BackgroundOption
import com.framex.app.ui.screens.appearance.BorderStyleOption
import com.framex.app.ui.theme.PresetAccentColors

@Composable
fun ContainerStyleCard(
    selectedBgColorIndex: Int,
    selectedBorderColorIndex: Int,
    accentColorIndex: Int,
    onBgColorSelect: (Int) -> Unit,
    onBorderColorSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = PresetAccentColors.getOrElse(accentColorIndex) { Color.White }
    val bgOptions = remember { BackgroundOption.entries }
    val borderOptions = remember { BorderStyleOption.entries }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.appearance_container_header),
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
                // Background Color
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.appearance_background_title),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    val activeBgOption = bgOptions.getOrNull(selectedBgColorIndex) ?: BackgroundOption.BLACK
                    Text(
                        text = stringResource(activeBgOption.labelRes),
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    bgOptions.forEachIndexed { index, option ->
                        val isSelected = selectedBgColorIndex == index
                        val optionLabel = stringResource(option.labelRes)
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) accentColor else Color.White.copy(alpha = 0.12f),
                            label = "bgColorBorder"
                        )
                        val surfaceColor = if (option == BackgroundOption.CLEAR) {
                            Color.White.copy(alpha = 0.06f)
                        } else {
                            option.color
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(surfaceColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                    contentDescription = "$optionLabel background"
                                }
                                .clickable { onBgColorSelect(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (option == BackgroundOption.CLEAR) {
                                Text(
                                    text = "T",
                                    color = if (isSelected) accentColor else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    bgOptions.forEach { option ->
                        Text(
                            text = stringResource(option.labelRes),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Border Style
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.appearance_border_title),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    val activeBorder = borderOptions.getOrNull(selectedBorderColorIndex) ?: BorderStyleOption.ACCENT
                    Text(
                        text = stringResource(activeBorder.labelRes),
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    borderOptions.forEachIndexed { index, option ->
                        val isSelected = selectedBorderColorIndex == index
                        val optionLabel = stringResource(option.labelRes)
                        val tabBackground by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            label = "borderTabBg"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tabBackground)
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                    contentDescription = "$optionLabel border style"
                                }
                                .clickable { onBorderColorSelect(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = optionLabel,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
