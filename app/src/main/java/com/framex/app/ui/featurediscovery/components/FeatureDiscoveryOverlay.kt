package com.framex.app.ui.featurediscovery.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.components.WovenNetBackground
import com.framex.app.ui.featurediscovery.DiscoveryAction
import com.framex.app.ui.featurediscovery.DiscoveryCompletionEvent
import com.framex.app.ui.featurediscovery.DiscoveryGuideDefinition
import com.framex.app.ui.featurediscovery.DiscoveryGuideState
import com.framex.app.ui.theme.SurfaceDark

@Composable
fun FeatureDiscoveryOverlay(
    guideState: DiscoveryGuideState,
    completionEvent: DiscoveryCompletionEvent?,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onDismissOutside: () -> Unit,
    onLaunchAction: (android.content.Context, DiscoveryAction) -> Unit,
    onCompletionDismiss: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(completionEvent) {
        val event = completionEvent ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = event.message,
            actionLabel = event.actionLabel,
            withDismissAction = event.actionLabel == null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
            onCompletionDismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = guideState) {
            is DiscoveryGuideState.Active -> {
                SetupGuideModal(
                    guide = state.guide,
                    onComplete = onComplete,
                    onSkip = onSkip,
                    onDismissOutside = onDismissOutside,
                    onLaunchAction = { action -> onLaunchAction(context, action) }
                )
            }
            DiscoveryGuideState.Hidden -> Unit
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = SurfaceDark,
                contentColor = Color.White,
                actionColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun SetupGuideModal(
    guide: DiscoveryGuideDefinition,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onDismissOutside: () -> Unit,
    onLaunchAction: (DiscoveryAction) -> Unit
) {
    val scrollState = rememberScrollState()

    // Scrim overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissOutside
            ),
        contentAlignment = Alignment.Center
    ) {
        // Content Card Modal with max width constraints
        Card(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .navigationBarsPadding()
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                WovenNetBackground(modifier = Modifier.matchParentSize())

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(scrollState)
                ) {
                    // Header Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = guide.title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            )
                            if (guide.subtitle.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = guide.subtitle,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                        IconButton(
                            onClick = onSkip,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Guide Image display
                    if (guide.imageResId != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(guide.imageResId),
                                contentDescription = guide.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Steps List
                    if (guide.steps.isNotEmpty()) {
                        guide.steps.forEachIndexed { index, step ->
                            SetupStepRow(index = index + 1, text = step)
                            if (index < guide.steps.size - 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // Action Button (Primary)
                    guide.actions.firstOrNull()?.let { action ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onLaunchAction(action)
                                onComplete()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = action.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStepRow(index: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$index",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}