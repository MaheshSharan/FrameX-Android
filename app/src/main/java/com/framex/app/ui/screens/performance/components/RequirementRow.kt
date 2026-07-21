package com.framex.app.ui.screens.performance.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RequirementRow(
    label: String,
    description: String,
    satisfied: Boolean,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "Grant"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.05f))
            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (satisfied) Color(0xFF22C55E).copy(0.15f)
                    else MaterialTheme.colorScheme.primary.copy(0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (satisfied) Icons.Default.Check else Icons.Default.Lock,
                contentDescription = null,
                tint = if (satisfied) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, color = Color.Gray, fontSize = 11.sp)
        }
        if (!satisfied && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.height(32.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
