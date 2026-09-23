package com.framex.app.ui.screens.permissions.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.screens.permissions.PermissionItem

@Composable
fun PermissionRow(
    item: PermissionItem,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emeraldColor = Color(0xFF10B981)
    val statusText = if (item.isGranted) {
        stringResource(R.string.action_granted)
    } else {
        stringResource(item.actionTextRes)
    }
    val title = stringResource(item.titleRes)
    val fullSemanticsDescription = stringResource(R.string.perm_status_format, title, statusText)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.05f))
            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = fullSemanticsDescription
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Icon Container
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and Description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(item.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Status Indicator or Action Button
        AnimatedContent(
            targetState = item.isGranted,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
            },
            label = "permActionAnim"
        ) { isGranted ->
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(emeraldColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.action_granted),
                            tint = emeraldColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = stringResource(item.actionTextRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
