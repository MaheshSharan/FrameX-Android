package com.framex.app.ui.screens.performance.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CircularGauge(
    percentage: Float?,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress = animateFloatAsState(
        targetValue = (percentage ?: 0f) / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "gauge"
    )
    Card(
        modifier = modifier
            .height(160.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            com.framex.app.ui.components.WovenNetBackground(modifier = Modifier.matchParentSize())
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(90.dp)) {
                    drawArc(
                        color = Color.White.copy(0.05f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress.value * 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = percentage?.let { "${it.toInt()}%" } ?: "N/A",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
}
