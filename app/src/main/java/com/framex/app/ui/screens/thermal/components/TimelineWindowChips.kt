package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.screens.thermal.TimelineWindow

/** Chip row choosing how far back the telemetry timelines reach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineWindowChips(
    selected: TimelineWindow,
    onSelect: (TimelineWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupLabel = stringResource(R.string.cd_timeline_window_label)
    Row(
        modifier = modifier.semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimelineWindow.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.labelRes)) }
            )
        }
    }
}
