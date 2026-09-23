package com.framex.app.ui.screens.appearance.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.theme.PresetAccentColors

@Composable
fun OpacityCard(
    opacity: Float,
    colorIndex: Int,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = PresetAccentColors.getOrElse(colorIndex) { Color.White }
    val percentText = "${(opacity * 100).toInt()}%"

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.appearance_visibility_header),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.appearance_opacity_title),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = percentText,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = opacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Overlay opacity $percentText"
                        },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}
