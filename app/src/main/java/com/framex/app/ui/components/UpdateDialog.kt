package com.framex.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.DownloadState

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    downloadState: DownloadState,
    onDownloadAndInstallClicked: () -> Unit,
    onRemindLaterClicked: () -> Unit
) {
    Dialog(onDismissRequest = onRemindLaterClicked) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "New Update Ready",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f))
                    ) {
                        Text(
                            text = "v${updateInfo.versionName}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Release Notes",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Markdown Release Notes Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.03f))
                        .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FormattedMarkdownText(markdownText = updateInfo.releaseNotes)
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (downloadState) {
                    is DownloadState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading APK...",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${(downloadState.progress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(0.1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is DownloadState.Failed -> {
                        Text(
                            text = "Download failed: ${downloadState.error}",
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    else -> {}
                }

                // Stacked Action Buttons for 100% Text Fit & Clean UX
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDownloadAndInstallClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = downloadState !is DownloadState.Downloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Download & Install", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onRemindLaterClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(0.12f)),
                        enabled = downloadState !is DownloadState.Downloading
                    ) {
                        Text("Remind Me Later", color = Color.White.copy(0.8f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FormattedMarkdownText(markdownText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val lines = markdownText.lines()
        var insideCallout = false
        var calloutType = "NOTE"
        var calloutText = ""

        val alertRegex = Regex("^>\\s*\\[!([A-Za-z]+)\\]\\s*(.*)$")

        lines.forEach { line ->
            val trimmed = line.trim()
            val alertMatch = alertRegex.find(trimmed)

            when {
                alertMatch != null -> {
                    if (insideCallout && calloutText.isNotEmpty()) {
                        CalloutBox(type = calloutType, text = calloutText)
                    }
                    insideCallout = true
                    calloutType = alertMatch.groupValues[1].uppercase()
                    calloutText = alertMatch.groupValues[2].trim()
                }
                insideCallout && trimmed.startsWith(">") -> {
                    val content = trimmed.removePrefix(">").trim()
                    calloutText += (if (calloutText.isNotEmpty()) "\n" else "") + content
                }
                insideCallout -> {
                    if (calloutText.isNotEmpty()) {
                        CalloutBox(type = calloutType, text = calloutText)
                    }
                    insideCallout = false
                    calloutText = ""
                    RenderMarkdownLine(line = line)
                }
                else -> {
                    RenderMarkdownLine(line = line)
                }
            }
        }

        if (insideCallout && calloutText.isNotEmpty()) {
            CalloutBox(type = calloutType, text = calloutText)
        }
    }
}

@Composable
private fun CalloutBox(type: String, text: String) {
    val (accentColor, bgColor) = when (type) {
        "IMPORTANT", "WARNING", "CAUTION" -> Color(0xFFF59E0B) to Color(0xFFF59E0B).copy(alpha = 0.08f)
        "NOTE", "INFO" -> Color(0xFF3B82F6) to Color(0xFF3B82F6).copy(alpha = 0.08f)
        "TIP" -> Color(0xFF10B981) to Color(0xFF10B981).copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = type,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = parseMarkdownInline(text),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun RenderMarkdownLine(line: String) {
    val trimmed = line.trim()
    when {
        trimmed.startsWith("### ") -> {
            Text(
                text = parseMarkdownInline(trimmed.removePrefix("### ")),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        trimmed.startsWith("## ") -> {
            Text(
                text = parseMarkdownInline(trimmed.removePrefix("## ")),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        trimmed.startsWith("# ") -> {
            Text(
                text = parseMarkdownInline(trimmed.removePrefix("# ")),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
            Row(modifier = Modifier.padding(start = 4.dp)) {
                Text("• ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = parseMarkdownInline(trimmed.substring(2)),
                    fontSize = 12.sp,
                    color = Color.White.copy(0.9f),
                    lineHeight = 17.sp
                )
            }
        }
        trimmed.isNotBlank() -> {
            Text(
                text = parseMarkdownInline(line),
                fontSize = 12.sp,
                color = Color.White.copy(0.85f),
                lineHeight = 17.sp
            )
        }
    }
}

private fun parseMarkdownInline(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*{2}(.*?)\\*{2})|(`(.*?)`)")
        val matches = regex.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            val value = match.value
            if (value.startsWith("**") && value.endsWith("**")) {
                val content = value.substring(2, value.length - 2)
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                    append(content)
                }
            } else if (value.startsWith("`") && value.endsWith("`")) {
                val content = value.substring(1, value.length - 1)
                withStyle(
                    style = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFA5B4FC),
                        background = Color.White.copy(0.06f)
                    )
                ) {
                    append(content)
                }
            }
            cursor = end
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
