package com.framex.app.ui.screens.overlay.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModeSelector(
    modes: List<String>,
    selectedMode: String,
    accentColor: Color,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { mode ->
            val isSelected = selectedMode == mode
            val bgPillColor by animateColorAsState(
                targetValue = if (isSelected) accentColor else Color.Transparent,
                animationSpec = tween(250),
                label = "modePillColor"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.Gray,
                animationSpec = tween(250),
                label = "modeTextColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(CircleShape)
                    .background(bgPillColor)
                    .semantics {
                        this.selected = isSelected
                    }
                    .clickable(
                        role = Role.Tab,
                        onClick = { onModeSelected(mode) }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode,
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
