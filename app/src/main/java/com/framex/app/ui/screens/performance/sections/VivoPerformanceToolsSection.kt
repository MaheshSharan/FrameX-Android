package com.framex.app.ui.screens.performance.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing

/**
 * Performance Tools section with streamlined action buttons and live collapsible list cards:
 * - LIVE perf_game_list
 * - LIVE 144Game_mergelist (rendered when 144Hz is available)
 */
@Composable
fun VivoPerformanceToolsSection(
    launcherGames: Set<String>,
    perfGameList: List<String>,
    rawPerfGameList: String?,
    isRefreshingPerfList: Boolean,
    mergeList144: List<String>,
    raw144MergeList: String?,
    isRefreshing144List: Boolean,
    show144MergeList: Boolean,
    onRefreshPerfList: () -> Unit,
    onAddAllToPerfList: (Set<String>, (Boolean) -> Unit) -> Unit,
    onRemoveAllFromPerfList: (Set<String>, (Boolean) -> Unit) -> Unit,
    onCompileAll: (Set<String>, (Boolean) -> Unit) -> Unit,
    onRefresh144MergeList: () -> Unit,
    onAddAllTo144MergeList: (Set<String>, (Boolean) -> Unit) -> Unit,
    onRemoveAllFrom144MergeList: (Set<String>, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onRefreshPerfList()
        if (show144MergeList) {
            onRefresh144MergeList()
        }
    }

    val addedCount = remember(launcherGames, perfGameList) {
        launcherGames.count { it in perfGameList }
    }
    val allAdded = launcherGames.isNotEmpty() && addedCount == launcherGames.size

    val added144Count = remember(launcherGames, mergeList144) {
        launcherGames.count { it in mergeList144 }
    }
    val all144Added = launcherGames.isNotEmpty() && added144Count == launcherGames.size

    var isAddingOrRemoving by remember { mutableStateOf(false) }
    var isAddingOrRemoving144 by remember { mutableStateOf(false) }
    var isCompiling by remember { mutableStateOf(false) }
    var compileStatusText by remember { mutableStateOf<String?>(null) }

    var isPerfListExpanded by rememberSaveable { mutableStateOf(false) }
    var is144ListExpanded by rememberSaveable { mutableStateOf(false) }

    val sectionTitle = if (show144MergeList) "Performance Tools & 144Hz" else "Performance Tools"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FrameXSpacing.XLarge),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Text(
                text = if (allAdded) "$addedCount games in list" else "$addedCount / ${launcherGames.size} in list",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (allAdded) Color(0xFF10B981) else Color(0xFF0EA5E9)
            )
        }

        // Action Buttons Row (perf list & AOT)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Button 1: Add/Remove All from perf_game_list
            Button(
                onClick = {
                    if (!isAddingOrRemoving) {
                        isAddingOrRemoving = true
                        if (allAdded) {
                            onRemoveAllFromPerfList(launcherGames) { isAddingOrRemoving = false }
                        } else {
                            onAddAllToPerfList(launcherGames) { isAddingOrRemoving = false }
                        }
                    }
                },
                enabled = launcherGames.isNotEmpty() && !isAddingOrRemoving,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allAdded) Color(0xFF1E293B) else MaterialTheme.colorScheme.primary,
                    contentColor = if (allAdded) Color(0xFF38BDF8) else Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                if (isAddingOrRemoving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (allAdded) "Remove All" else "Add All Games",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Button 2: Speed Compile (AOT)
            OutlinedButton(
                onClick = {
                    if (!isCompiling) {
                        isCompiling = true
                        compileStatusText = null
                        onCompileAll(launcherGames) { ok ->
                            isCompiling = false
                            compileStatusText = if (ok) "Compiled successfully" else "Compilation failed"
                        }
                    }
                },
                enabled = launcherGames.isNotEmpty() && !isCompiling,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF14B8A6).copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF14B8A6)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                if (isCompiling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF14B8A6),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Compile All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }

        AnimatedVisibility(visible = compileStatusText != null) {
            compileStatusText?.let { msg ->
                Text(
                    text = msg,
                    fontSize = 12.sp,
                    color = if (msg.contains("success", ignoreCase = true)) Color(0xFF10B981) else Color(0xFFFF5252),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Collapsible Card 1: LIVE perf_game_list
        CollapsibleListCard(
            title = "LIVE perf_game_list",
            rawContent = rawPerfGameList,
            emptyMessage = "perf_game_list is currently empty on this device",
            isExpanded = isPerfListExpanded,
            isRefreshing = isRefreshingPerfList,
            onToggleExpand = { isPerfListExpanded = !isPerfListExpanded },
            onRefresh = onRefreshPerfList
        )

        // Collapsible Card 2: LIVE 144Game_mergelist (Only when show144MergeList is true)
        if (show144MergeList) {
            CollapsibleListCard(
                title = "LIVE 144Game_mergelist",
                rawContent = raw144MergeList,
                emptyMessage = "144Game_mergelist is currently empty on this device",
                isExpanded = is144ListExpanded,
                isRefreshing = isRefreshing144List,
                onToggleExpand = { is144ListExpanded = !is144ListExpanded },
                onRefresh = onRefresh144MergeList,
                actionSlot = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (!isAddingOrRemoving144) {
                                    isAddingOrRemoving144 = true
                                    if (all144Added) {
                                        onRemoveAllFrom144MergeList(launcherGames) { isAddingOrRemoving144 = false }
                                    } else {
                                        onAddAllTo144MergeList(launcherGames) { isAddingOrRemoving144 = false }
                                    }
                                }
                            },
                            enabled = launcherGames.isNotEmpty() && !isAddingOrRemoving144,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (all144Added) Color(0xFF1E293B) else Color(0xFF0284C7),
                                contentColor = if (all144Added) Color(0xFF38BDF8) else Color.White
                            ),
                            modifier = Modifier.height(38.dp)
                        ) {
                            if (isAddingOrRemoving144) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (all144Added) "Remove All from 144Hz" else "Add All Games to 144Hz",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CollapsibleListCard(
    title: String,
    rawContent: String?,
    emptyMessage: String,
    isExpanded: Boolean,
    isRefreshing: Boolean,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    actionSlot: @Composable (() -> Unit)? = null
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Card(
        shape = FrameXShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(FrameXBorders.ActiveBorderWidth, FrameXBorders.CardStroke),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.Gray
                    )
                }

                IconButton(
                    onClick = {
                        if (!isRefreshing) onRefresh()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh list",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090A0D))
                            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        when {
                            rawContent == null -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF0EA5E9),
                                    strokeWidth = 2.dp
                                )
                            }
                            rawContent.isBlank() -> {
                                Text(
                                    text = emptyMessage,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF6B7080)
                                )
                            }
                            else -> {
                                Text(
                                    text = rawContent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFC7CAD9),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    actionSlot?.invoke()
                }
            }
        }
    }
}
