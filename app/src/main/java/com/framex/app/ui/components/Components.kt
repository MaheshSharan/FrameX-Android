package com.framex.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 2.dp
        )
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
fun QuickActionButton(
    title: String,
    subtitle: String,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = false,
    showChevron: Boolean = false,
    statusTag: (@Composable () -> Unit)? = null,
    icon: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Woven net texture background
            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                val step = 14.dp.toPx()
                val lineColor = Color.White.copy(alpha = 0.025f)
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x + size.height, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x, size.height), end = androidx.compose.ui.geometry.Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                    x += step
                }
            }

            if (isFullWidth) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconContainerColor)
                            .border(1.dp, iconContentColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                            icon()
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (showChevron) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconContainerColor)
                            .border(1.dp, iconContentColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                            icon()
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (statusTag != null) {
                        statusTag()
                    } else {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = Color.White.copy(alpha = 0.6f),
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

