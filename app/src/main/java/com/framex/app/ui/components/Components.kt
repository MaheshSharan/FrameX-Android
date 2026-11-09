package com.framex.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framex.app.R

/**
 * High-performance texture background that draws a woven diagonal net.
 * Uses [Modifier.drawWithCache] to construct the [Path] exclusively when viewport
 * dimensions change, guaranteeing 0 object allocations during 60/120 FPS draw sweeps.
 */
@Composable
fun WovenNetBackground(
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White.copy(alpha = 0.025f)
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val step = 14.dp.toPx()
            val strokePx = 1.dp.toPx()
            val path = Path()
            var x = -size.height
            while (x < size.width + size.height) {
                path.moveTo(x, 0f)
                path.lineTo(x + size.height, size.height)

                path.moveTo(x, size.height)
                path.lineTo(x + size.height, 0f)
                x += step
            }
            onDrawBehind {
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = strokePx)
                )
            }
        }
    )
}

/**
 * Standardized sub-screen top app bar with 48dp optical balance back button,
 * centered title, and 48dp balance spacer.
 */
@Composable
fun FrameXTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarsPadding: Boolean = true,
    navigationIcon: ImageVector = Icons.Default.ArrowBackIosNew,
    navigationContentDescription: String = stringResource(R.string.action_back)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = navigationIcon,
                contentDescription = navigationContentDescription,
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.weight(1f))

        // 48dp optical balance spacer matching the back button touch target
        Spacer(modifier = Modifier.width(48.dp))
    }
}
