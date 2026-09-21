package com.framex.app.ui.screens.performance.sections

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.ledger.Stage
import com.framex.app.ui.screens.performance.ActiveGamingSession
import com.framex.app.ui.screens.performance.components.StatusBlock

@Composable
fun HeroGamingCard(
    gamingState: GamingModeState,
    animatedProgress: Float,
    canActivate: Boolean,
    isActive: Boolean,
    isBusy: Boolean,
    activeColor: Color,
    primaryRed: Color,
    activeSession: ActiveGamingSession? = null,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(0.25f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            com.framex.app.ui.components.WovenNetBackground(modifier = Modifier.matchParentSize())
            Column(modifier = Modifier.padding(20.dp)) {

                // ── Title + Status Badge ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "GAMING MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedContent(
                            targetState = gamingState,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "stateLabel"
                        ) { state ->
                            Text(
                                text = when (state) {
                                    is GamingModeState.Idle -> "Inactive"
                                    is GamingModeState.Enabling -> "Activating…"
                                    is GamingModeState.Active -> "Active"
                                    is GamingModeState.Disabling -> "Restoring…"
                                    is GamingModeState.Error -> "Error"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                                color = Color.White
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                color = when {
                                    isActive -> activeColor.copy(0.1f)
                                    gamingState is GamingModeState.Error -> primaryRed.copy(0.1f)
                                    else -> Color.Gray.copy(0.1f)
                                },
                                shape = CircleShape
                            )
                            .border(
                                1.dp,
                                color = when {
                                    isActive -> activeColor.copy(0.25f)
                                    gamingState is GamingModeState.Error -> primaryRed.copy(0.25f)
                                    else -> Color.Gray.copy(0.2f)
                                },
                                shape = CircleShape
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = when {
                                            isActive -> activeColor
                                            gamingState is GamingModeState.Error -> primaryRed
                                            else -> Color.Gray
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isActive -> "ACTIVE"
                                    gamingState is GamingModeState.Error -> "ERROR"
                                    else -> "INACTIVE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isActive -> activeColor
                                    gamingState is GamingModeState.Error -> primaryRed
                                    else -> Color.Gray
                                }
                            )
                        }
                    }
                }

                // ── Progress Indicator (while busy) ──────────────────────────
                AnimatedVisibility(visible = isBusy) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (isActive) activeColor else primaryRed,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val statusText = when (val s = gamingState) {
                            is GamingModeState.Enabling -> s.statusText
                            is GamingModeState.Disabling -> "Restoring system state…"
                            else -> ""
                        }
                        Text(
                            text = statusText,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                // ── Error Banner ──────────────────────────────────────────────
                AnimatedVisibility(visible = gamingState is GamingModeState.Error) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(primaryRed.copy(0.08f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = primaryRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (gamingState as? GamingModeState.Error)?.message ?: "",
                                color = primaryRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Status cards + action button ──────────────────────────────
                if (!isBusy) {
                    if (isActive) {
                        ActiveSessionStatusCard(session = activeSession)

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onDeactivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                            containerColor = primaryRed.copy(0.15f),
                                contentColor = primaryRed
                            )
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deactivate Gaming Mode", fontWeight = FontWeight.Bold)
                        }
                    } else if (gamingState is GamingModeState.Error && gamingState.message.contains("Deactivation", ignoreCase = true)) {
                        Button(
                            onClick = onDeactivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryRed,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Deactivation", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onActivate,
                            enabled = canActivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(0.06f),
                                disabledContentColor = Color.Gray
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (canActivate) "Activate Gaming Mode" else "Complete setup first",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color.White.copy(0.06f),
                            disabledContentColor = Color.Gray
                        )
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Gray,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (gamingState is GamingModeState.Enabling) "Activating…" else "Restoring…",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private val SessionCardShape = RoundedCornerShape(16.dp)
private val HeaderTagShape = RoundedCornerShape(4.dp)

@Composable
private fun ActiveSessionStatusCard(session: ActiveGamingSession?) {
    val accentColor = Color(0xFF10B981)
    val stages = session?.summary?.stages.orEmpty()

    var userExpandedOverrides by remember { mutableStateOf<Map<Stage, Boolean>>(emptyMap()) }

    val allExpanded = stages.isNotEmpty() && stages.all { stageSummary ->
        userExpandedOverrides[stageSummary.stage] == true
    }

    Card(
        shape = SessionCardShape,
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(0.2f), SessionCardShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActiveSessionHeaderRow(
                title = session?.title ?: "Gaming Mode Active",
                hasStages = stages.isNotEmpty(),
                allExpanded = allExpanded,
                onToggleExpandAll = {
                    val target = !allExpanded
                    userExpandedOverrides = stages.associate { it.stage to target }
                },
                accentColor = accentColor
            )

            session?.summary?.let { summary ->
                ActiveSessionStatsSummary(summary = summary)
            }

            HorizontalDivider(color = accentColor.copy(0.15f))

            ActiveSessionStagesList(
                stages = stages,
                userExpandedOverrides = userExpandedOverrides,
                onToggleStage = { stage, targetExpanded ->
                    userExpandedOverrides = userExpandedOverrides + (stage to targetExpanded)
                }
            )
        }
    }
}

@Composable
private fun ActiveSessionStatsSummary(summary: com.framex.app.gaming.ledger.LedgerSummary) {
    if (summary.totalOps <= 0) return
    val statsText = buildString {
        append("Applied ${summary.totalApplied}/${summary.totalOps}")
        if (summary.totalFailed > 0) {
            append(" · ${summary.totalFailed} failed")
        }
        if (summary.totalSkipped > 0) {
            append(" · ${summary.totalSkipped} skipped")
        }
    }
    Text(
        text = statsText,
        color = Color.Gray,
        fontSize = 11.sp
    )
}

@Composable
private fun ActiveSessionStagesList(
    stages: List<com.framex.app.gaming.ledger.StageSummary>,
    userExpandedOverrides: Map<Stage, Boolean>,
    onToggleStage: (Stage, Boolean) -> Unit
) {
    if (stages.isEmpty()) {
        Text(
            text = "No execution data for this session",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    } else {
        stages.forEach { stageSummary ->
            key(stageSummary.stage) {
                val isExpanded = userExpandedOverrides[stageSummary.stage] ?: false
                StatusBlock(
                    stageSummary = stageSummary,
                    isExpanded = isExpanded,
                    onToggleExpand = { onToggleStage(stageSummary.stage, !isExpanded) }
                )
            }
        }
    }
}

@Composable
private fun ActiveSessionHeaderRow(
    title: String,
    hasStages: Boolean,
    allExpanded: Boolean,
    onToggleExpandAll: () -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        if (hasStages) {
            Text(
                text = if (allExpanded) "Collapse all" else "Expand all",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(HeaderTagShape)
                    .clickable(role = Role.Button, onClick = onToggleExpandAll)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}
