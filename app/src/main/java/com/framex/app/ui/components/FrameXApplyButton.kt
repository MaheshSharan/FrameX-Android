package com.framex.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.theme.FrameXMotion

/**
 * Unified stadium action button for committing configuration changes.
 * Used consistently across Overlay Customization and Appearance screens.
 */
@Composable
fun FrameXApplyButton(
    hasChanges: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "framex_apply_button",
    applyText: String = stringResource(R.string.action_apply_changes),
    appliedText: String = stringResource(R.string.action_applied)
) {
    val reduceMotion = FrameXMotion.rememberReduceMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = if (reduceMotion) snap() else spring(stiffness = 500f),
        label = "applyButtonScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (hasChanges) accentColor else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "applyButtonContainerColor"
    )

    Button(
        onClick = {
            if (hasChanges) {
                onClick()
            }
        },
        enabled = hasChanges,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = Color.Gray
        ),
        shape = CircleShape,
        modifier = modifier
            .testTag(testTag)
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
    ) {
        AnimatedContent(
            targetState = hasChanges,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "applyButtonContent"
        ) { targetHasChanges ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (targetHasChanges) Color.White else Color.Gray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (targetHasChanges) applyText else appliedText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
