package com.framex.app.ui.screens.permissions.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framex.app.R

@Composable
fun PermissionSummaryHeader(
    grantedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val progress = (grantedCount.toFloat() / totalCount.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "permProgress")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.perm_required_section),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$grantedCount of $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (grantedCount == totalCount) Color(0xFF10B981) else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = if (grantedCount == totalCount) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.08f),
            strokeCap = StrokeCap.Round
        )
    }
}
