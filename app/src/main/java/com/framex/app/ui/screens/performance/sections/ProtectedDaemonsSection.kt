package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProtectedDaemonsSection(daemonsList: List<String>) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            "PROTECTED GAMING DAEMONS",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF22C55E).copy(0.1f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Vivo hardware daemons dynamically protected:",
                    color = Color.White.copy(0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                daemonsList.forEach { pkg ->
                    Text("• $pkg", color = Color(0xFF22C55E), fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
