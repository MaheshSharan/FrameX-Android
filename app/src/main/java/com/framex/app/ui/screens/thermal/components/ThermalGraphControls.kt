package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.screens.thermal.GraphMetricMode
import com.framex.app.ui.screens.thermal.TimeWindow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun dropdownFieldColors(): TextFieldColors =
    ExposedDropdownMenuDefaults.outlinedTextFieldColors(
        focusedBorderColor = Color.White.copy(alpha = 0.15f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
        disabledBorderColor = Color.White.copy(alpha = 0.08f),
        errorBorderColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = Color.Gray,
        unfocusedLabelColor = Color.Gray,
        disabledLabelColor = Color.Gray,
        errorLabelColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.5f),
        errorTextColor = Color.White,
        cursorColor = MaterialTheme.colorScheme.primary,
        errorCursorColor = MaterialTheme.colorScheme.primary,
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.Gray,
        errorTrailingIconColor = Color.Gray,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalGraphControls(
    selectedWindow: TimeWindow,
    onWindowSelected: (TimeWindow) -> Unit,
    selectedMode: GraphMetricMode,
    onModeSelected: (GraphMetricMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var timeDropdownExpanded by remember { mutableStateOf(false) }
    var modeDropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time Window Dropdown
        ExposedDropdownMenuBox(
            expanded = timeDropdownExpanded,
            onExpandedChange = { timeDropdownExpanded = !timeDropdownExpanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedWindow.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Timeframe", fontSize = 11.sp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeDropdownExpanded) },
                colors = dropdownFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            ExposedDropdownMenu(
                expanded = timeDropdownExpanded,
                onDismissRequest = { timeDropdownExpanded = false },
                modifier = Modifier.background(Color(0xFF1E1E2A))
            ) {
                TimeWindow.values().forEach { window ->
                    DropdownMenuItem(
                        text = { Text(window.label, fontSize = 13.sp, color = Color.White) },
                        onClick = {
                            onWindowSelected(window)
                            timeDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Metric Mode Dropdown
        ExposedDropdownMenuBox(
            expanded = modeDropdownExpanded,
            onExpandedChange = { modeDropdownExpanded = !modeDropdownExpanded },
            modifier = Modifier.weight(1.3f)
        ) {
            OutlinedTextField(
                value = selectedMode.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Graph Mode", fontSize = 11.sp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeDropdownExpanded) },
                colors = dropdownFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            ExposedDropdownMenu(
                expanded = modeDropdownExpanded,
                onDismissRequest = { modeDropdownExpanded = false },
                modifier = Modifier.background(Color(0xFF1E1E2A))
            ) {
                GraphMetricMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, fontSize = 13.sp, color = Color.White) },
                        onClick = {
                            onModeSelected(mode)
                            modeDropdownExpanded = false
                        }
                    )
                }
            }
        }
    }
}
