package com.framex.app.ui.screens.performance.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vivo & iQOO Hardware Performance Suite.
 *
 * Operates on the entire set of games added in the Game Launcher section:
 * - Perf List card: Add all / Remove all from Vivo's internal perf_game_list.
 * - AOT card: Compile all launcher games to speed filter.
 * - Live perf_game_list display card.
 *
 * No per-game tab system. No hardcoded app names.
 */
@Composable
fun VivoPerformanceToolsSection(
    launcherGames: Set<String>,
    perfGameList: List<String>,
    rawPerfGameList: String?,
    onRefreshPerfList: () -> Unit,
    onAddAllToPerfList: (Set<String>, (Boolean) -> Unit) -> Unit,
    onRemoveAllFromPerfList: (Set<String>, (Boolean) -> Unit) -> Unit,
    onCompileAll: (Set<String>, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onRefreshPerfList()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PerfListCard(
            launcherGames = launcherGames,
            perfGameList = perfGameList,
            onAddAll = { onComplete -> onAddAllToPerfList(launcherGames, onComplete) },
            onRemoveAll = { onComplete -> onRemoveAllFromPerfList(launcherGames, onComplete) }
        )

        AotCompileCard(
            onCompileAll = { onComplete -> onCompileAll(launcherGames, onComplete) }
        )

        LivePerfGameListCard(rawList = rawPerfGameList)
    }
}

@Composable
private fun PerfListCard(
    launcherGames: Set<String>,
    perfGameList: List<String>,
    onAddAll: ((Boolean) -> Unit) -> Unit,
    onRemoveAll: ((Boolean) -> Unit) -> Unit
) {
    var isAdding by remember { mutableStateOf(false) }
    var isRemoving by remember { mutableStateOf(false) }

    // All launcher games present in perf list → show Remove, otherwise show Add
    val allAdded = remember(launcherGames, perfGameList) {
        launcherGames.isNotEmpty() && launcherGames.all { it in perfGameList }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Perf List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Adds all games in Game Launcher to Vivo's internal perf_game_list. This grants sustained Cortex-X CPU priority, relaxed thermal throttling, and priority GPU scheduling for the duration of each session.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0B3C1),
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (allAdded) {
                // Remove from Perf List — full width
                Button(
                    onClick = {
                        if (!isRemoving) {
                            isRemoving = true
                            onRemoveAll { isRemoving = false }
                        }
                    },
                    enabled = !isRemoving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3F1315),
                        contentColor = Color(0xFFFF5252),
                        disabledContainerColor = Color(0xFF3F1315).copy(alpha = 0.5f),
                        disabledContentColor = Color(0xFFFF5252).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isRemoving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFFFF5252),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Removing…",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            text = "Remove from Perf List",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Add to Perf List — full width
                Button(
                    onClick = {
                        if (!isAdding) {
                            isAdding = true
                            onAddAll { isAdding = false }
                        }
                    },
                    enabled = !isAdding,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F3B4A),
                        contentColor = Color.Cyan,
                        disabledContainerColor = Color(0xFF0F3B4A).copy(alpha = 0.5f),
                        disabledContentColor = Color.Cyan.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Cyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Adding…",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            text = "Add to Perf List",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AotCompileCard(
    onCompileAll: ((Boolean) -> Unit) -> Unit
) {
    var isCompiling by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = Color.Cyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AOT Speed Compilation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Pre-compiles all Game Launcher apps into native machine code using ART's speed filter. Eliminates JIT freeze spikes during gameplay.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0B3C1),
                lineHeight = 19.sp
            )

            AnimatedVisibility(visible = statusText != null) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusText.orEmpty(),
                        fontSize = 12.sp,
                        color = if (statusText?.startsWith("Success") == true) Color(0xFF10B981) else Color(0xFFFF5252)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (!isCompiling) {
                        isCompiling = true
                        statusText = null
                        onCompileAll { ok ->
                            isCompiling = false
                            statusText = if (ok) "Success — all apps compiled to speed filter" else "Compilation failed for one or more apps"
                        }
                    }
                },
                enabled = !isCompiling,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F3B4A),
                    contentColor = Color.Cyan,
                    disabledContainerColor = Color(0xFF0F3B4A).copy(alpha = 0.5f),
                    disabledContentColor = Color.Cyan.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isCompiling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.Cyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Compiling Apps…",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Compile Apps",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LivePerfGameListCard(rawList: String?) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "LIVE perf_game_list",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E92A2),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D0E11))
                    .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                when {
                    rawList == null -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF818CF8),
                            strokeWidth = 2.dp
                        )
                    }
                    rawList.isBlank() -> {
                        Text(
                            text = "perf_game_list is empty on this device",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = Color(0xFF6B7080),
                            lineHeight = 17.sp
                        )
                    }
                    else -> {
                        Text(
                            text = rawList,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = Color(0xFFC7CAD9),
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}
