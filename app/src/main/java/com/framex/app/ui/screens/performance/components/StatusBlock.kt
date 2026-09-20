package com.framex.app.ui.screens.performance.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.gaming.ledger.AppliedOp
import com.framex.app.gaming.ledger.CommandLabelParser
import com.framex.app.gaming.ledger.OpStatus
import com.framex.app.gaming.ledger.StageStatus
import com.framex.app.gaming.ledger.StageSummary

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.semantics.Role

@Composable
fun StatusBlock(
    stageSummary: StageSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StageHeaderRow(stageSummary = stageSummary)
        PrimaryOpsSection(stageSummary = stageSummary)
        DetailOpsSection(stageSummary = stageSummary)
    }
}

@Composable
private fun StageHeaderRow(stageSummary: StageSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stageSummary.stage.label.uppercase(),
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )

        StageStatusPill(
            status = stageSummary.status,
            appliedCount = stageSummary.appliedCount,
            ranCount = stageSummary.ranCount
        )
    }
}

@Composable
private fun PrimaryOpsSection(stageSummary: StageSummary) {
    if (stageSummary.primaryOps.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            stageSummary.primaryOps.forEach { op ->
                PrimaryOpRow(op = op)
            }
        }
    } else if (stageSummary.detailOps.isNotEmpty()) {
        // Fallback when no explicit primary op is present
        PrimaryOpRow(op = stageSummary.detailOps.first())
    }
}

@Composable
private fun DetailOpsSection(stageSummary: StageSummary) {
    val startIndex = if (stageSummary.primaryOps.isNotEmpty()) 0 else 1
    val detailCount = (stageSummary.detailOps.size - startIndex).coerceAtLeast(0)
    if (detailCount <= 0) return

    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = if (isExpanded) "Hide details" else "Show details"
            ) { isExpanded = !isExpanded }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isExpanded) "▴ Hide $detailCount commands" else "▾ +$detailCount commands",
            color = Color(0xFF60A5FA),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in startIndex until stageSummary.detailOps.size) {
                DetailOpRow(op = stageSummary.detailOps[i])
            }
        }
    }
}

@Composable
private fun PrimaryOpRow(op: AppliedOp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val label = CommandLabelParser.formatLabel(op.key, op.displayValue)
        Text(
            text = label,
            color = when (op.status) {
                OpStatus.FAILED -> Color(0xFFF87171)
                OpStatus.SKIPPED -> Color.Gray
                OpStatus.APPLIED -> Color(0xFFE2E8F0)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun DetailOpRow(op: AppliedOp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val label = CommandLabelParser.formatLabel(op.key, op.displayValue)
        Text(
            text = label,
            color = when (op.status) {
                OpStatus.FAILED -> Color(0xFFF87171)
                OpStatus.SKIPPED -> Color.Gray
                OpStatus.APPLIED -> Color(0xFF94A3B8)
            },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = when (op.status) {
                OpStatus.APPLIED -> "✓"
                OpStatus.FAILED -> "✕"
                OpStatus.SKIPPED -> "○"
            },
            color = when (op.status) {
                OpStatus.APPLIED -> Color(0xFF22C55E)
                OpStatus.FAILED -> Color(0xFFEF4444)
                OpStatus.SKIPPED -> Color.Gray
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun StageStatusPill(
    status: StageStatus,
    appliedCount: Int,
    ranCount: Int
) {
    val bgColor = when (status) {
        StageStatus.ACTIVE -> Color(0xFF22C55E).copy(alpha = 0.12f)
        StageStatus.PARTIAL -> Color(0xFFF59E0B).copy(alpha = 0.12f)
        StageStatus.FAILED -> Color(0xFFEF4444).copy(alpha = 0.12f)
        StageStatus.SKIPPED -> Color.Gray.copy(alpha = 0.12f)
    }
    val textColor = when (status) {
        StageStatus.ACTIVE -> Color(0xFF22C55E)
        StageStatus.PARTIAL -> Color(0xFFF59E0B)
        StageStatus.FAILED -> Color(0xFFEF4444)
        StageStatus.SKIPPED -> Color.Gray
    }
    val borderColor = when (status) {
        StageStatus.ACTIVE -> Color(0xFF22C55E).copy(alpha = 0.25f)
        StageStatus.PARTIAL -> Color(0xFFF59E0B).copy(alpha = 0.25f)
        StageStatus.FAILED -> Color(0xFFEF4444).copy(alpha = 0.25f)
        StageStatus.SKIPPED -> Color.Gray.copy(alpha = 0.25f)
    }
    val label = when (status) {
        StageStatus.ACTIVE -> if (ranCount > 1) "✓ $appliedCount/$ranCount" else "✓ ACTIVE"
        StageStatus.PARTIAL -> "! $appliedCount/$ranCount"
        StageStatus.FAILED -> "✕ FAILED"
        StageStatus.SKIPPED -> "○ SKIPPED"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
