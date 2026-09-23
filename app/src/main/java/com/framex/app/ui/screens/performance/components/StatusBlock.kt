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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.gaming.ledger.AppliedOp
import com.framex.app.gaming.ledger.CommandLabelParser
import com.framex.app.gaming.ledger.OpStatus
import com.framex.app.gaming.ledger.Stage
import com.framex.app.gaming.ledger.StageStatus
import com.framex.app.gaming.ledger.StageSummary

private val StatusBoxShape = RoundedCornerShape(4.dp)
private val RowClipShape = RoundedCornerShape(6.dp)
private val BlockClipShape = RoundedCornerShape(8.dp)

private val ActiveGreen = Color(0xFF10B981)
private val PartialAmber = Color(0xFFF59E0B)
private val FailedRed = Color(0xFFEF4444)
private val FailedTextRed = Color(0xFFF87171)
private val SkippedGrey = Color(0xFF6B7280)
private val SkippedTextGrey = Color(0xFF9CA3AF)
private val AppliedTextWhite = Color(0xFFE2E8F0)
private val ContainerBg = Color.White.copy(alpha = 0.02f)

fun shouldAutoExpand(stageSummary: StageSummary): Boolean = stageSummary.status == StageStatus.FAILED

@Composable
fun StatusBlock(
    stageSummary: StageSummary,
    modifier: Modifier = Modifier,
    isExpanded: Boolean? = null,
    onToggleExpand: (() -> Unit)? = null
) {
    var internalExpanded by remember(stageSummary.stage) {
        mutableStateOf(shouldAutoExpand(stageSummary))
    }

    val effectiveExpanded = isExpanded ?: internalExpanded
    val toggleAction = onToggleExpand ?: { internalExpanded = !internalExpanded }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BlockClipShape)
            .background(ContainerBg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ParentStageRow(
            stageSummary = stageSummary,
            isExpanded = effectiveExpanded,
            onToggleExpand = toggleAction
        )

        AnimatedVisibility(
            visible = effectiveExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            StageChildrenSection(stageSummary = stageSummary)
        }
    }
}

@Composable
private fun ParentStageRow(
    stageSummary: StageSummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(RowClipShape)
            .clickable(
                role = Role.Button,
                onClickLabel = if (isExpanded) "Collapse ${stageSummary.stage.label}" else "Expand ${stageSummary.stage.label}"
            ) { onToggleExpand() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParentStatusBox(status = stageSummary.status)

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = stageSummary.stage.label.uppercase(),
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f)
        )

        if (stageSummary.stage != Stage.APPS && stageSummary.ranCount > 0) {
            Text(
                text = "${stageSummary.appliedCount}/${stageSummary.ranCount}",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ParentStatusBox(
    status: StageStatus,
    modifier: Modifier = Modifier
) {
    val contentDesc = when (status) {
        StageStatus.ACTIVE -> "Stage active"
        StageStatus.PARTIAL -> "Stage partial"
        StageStatus.FAILED -> "Stage failed"
        StageStatus.SKIPPED -> "Stage skipped"
    }
    val boxModifier = when (status) {
        StageStatus.ACTIVE -> Modifier.clip(StatusBoxShape).background(ActiveGreen)
        StageStatus.PARTIAL -> Modifier.clip(StatusBoxShape).background(PartialAmber)
        StageStatus.FAILED -> Modifier.border(1.dp, FailedRed, StatusBoxShape)
        StageStatus.SKIPPED -> Modifier.border(1.dp, SkippedGrey, StatusBoxShape)
    }

    Box(
        modifier = modifier
            .size(16.dp)
            .semantics { contentDescription = contentDesc }
            .then(boxModifier),
        contentAlignment = Alignment.Center
    ) {
        ParentStatusIcon(status = status)
    }
}

@Composable
private fun ParentStatusIcon(status: StageStatus) {
    when (status) {
        StageStatus.ACTIVE -> Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
        StageStatus.PARTIAL -> Text("−", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
        StageStatus.FAILED -> Text("✕", color = FailedRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
        StageStatus.SKIPPED -> Unit
    }
}

@Composable
private fun StageChildrenSection(
    stageSummary: StageSummary,
    modifier: Modifier = Modifier
) {
    val (alwaysVisibleOps, collapsibleDetailOps) = remember(stageSummary) {
        val primaryList = if (stageSummary.primaryOps.isNotEmpty()) {
            stageSummary.primaryOps
        } else {
            stageSummary.detailOps.take(1)
        }
        val detailList = if (stageSummary.primaryOps.isNotEmpty()) {
            stageSummary.detailOps
        } else {
            stageSummary.detailOps.drop(1)
        }
        val alwaysVisibleDetails = detailList.filter {
            it.status == OpStatus.FAILED || it.status == OpStatus.SKIPPED
        }
        val collapsibleDetails = detailList.filter {
            it.status == OpStatus.APPLIED
        }
        (primaryList + alwaysVisibleDetails) to collapsibleDetails
    }

    var detailsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        alwaysVisibleOps.forEach { op ->
            key(op.key) {
                ChildOpRow(op = op)
            }
        }

        if (collapsibleDetailOps.isNotEmpty()) {
            CollapsibleDetailsBlock(
                collapsibleOps = collapsibleDetailOps,
                isExpanded = detailsExpanded,
                onToggle = { detailsExpanded = !detailsExpanded }
            )
        }
    }
}

@Composable
private fun CollapsibleDetailsBlock(
    collapsibleOps: List<AppliedOp>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        DetailMoreToggle(
            count = collapsibleOps.size,
            isExpanded = isExpanded,
            onToggle = onToggle
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                collapsibleOps.forEach { op ->
                    key(op.key) {
                        ChildOpRow(op = op)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMoreToggle(
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(start = 24.dp)
            .defaultMinSize(minHeight = 40.dp)
            .clip(RowClipShape)
            .clickable(
                role = Role.Button,
                onClickLabel = if (isExpanded) "Hide details" else "Show details",
                onClick = onToggle
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isExpanded) "▴ Hide $count more" else "▾ +$count more",
            color = ActiveGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChildOpRow(
    op: AppliedOp,
    modifier: Modifier = Modifier
) {
    var showRawCommand by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .defaultMinSize(minHeight = 32.dp)
            .clip(RowClipShape)
            .clickable(
                role = Role.Button,
                onClickLabel = if (showRawCommand) "Hide raw command" else "Show raw command"
            ) {
                showRawCommand = !showRawCommand
            }
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChildStatusBox(status = op.status)

            val ellipsizedValue = CommandLabelParser.formatMiddleEllipsis(op.displayValue)
            val labelText = CommandLabelParser.formatLabel(op.key, ellipsizedValue)

            Text(
                text = labelText,
                color = when (op.status) {
                    OpStatus.FAILED -> FailedTextRed
                    OpStatus.SKIPPED -> SkippedTextGrey
                    OpStatus.APPLIED -> AppliedTextWhite
                },
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        if (showRawCommand) {
            val raw = op.rawCommand.ifBlank { op.key }
            Text(
                text = raw,
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun ChildStatusBox(
    status: OpStatus,
    modifier: Modifier = Modifier
) {
    val contentDesc = when (status) {
        OpStatus.APPLIED -> "Applied"
        OpStatus.FAILED -> "Failed"
        OpStatus.SKIPPED -> "Skipped"
    }
    val boxModifier = when (status) {
        OpStatus.APPLIED -> Modifier.border(1.dp, ActiveGreen, StatusBoxShape)
        OpStatus.FAILED -> Modifier.border(1.dp, FailedRed, StatusBoxShape)
        OpStatus.SKIPPED -> Modifier.border(1.dp, SkippedGrey, StatusBoxShape)
    }

    Box(
        modifier = modifier
            .size(16.dp)
            .semantics { contentDescription = contentDesc }
            .then(boxModifier),
        contentAlignment = Alignment.Center
    ) {
        ChildStatusIcon(status = status)
    }
}

@Composable
private fun ChildStatusIcon(status: OpStatus) {
    when (status) {
        OpStatus.APPLIED -> Text("✓", color = ActiveGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
        OpStatus.FAILED -> Text("✕", color = FailedRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
        OpStatus.SKIPPED -> Text("○", color = SkippedTextGrey, fontSize = 9.sp, fontWeight = FontWeight.Normal, lineHeight = 9.sp)
    }
}
