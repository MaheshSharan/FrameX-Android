package com.framex.app.ui.screens.permissions.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.privilege.PrivilegeMode

/** Stateless picker for how FrameX gets elevated access. Every row is at least 48dp tall. */
@Composable
fun PrivilegeModeSelector(
    selectedMode: PrivilegeMode,
    isReady: Boolean,
    onModeSelected: (PrivilegeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .testTag("privilege_mode_selector"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.perm_mode_title),
            style = MaterialTheme.typography.titleMedium
        )
        MODES.forEach { option ->
            val selected = option.mode == selectedMode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onModeSelected(option.mode) }
                    )
                    .padding(horizontal = 4.dp)
                    .testTag("privilege_mode_${option.mode.key}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = stringResource(option.titleRes),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(
                            if (selected && option.mode != PrivilegeMode.NONE) {
                                if (isReady) R.string.perm_mode_state_connected else R.string.perm_mode_state_not_ready
                            } else {
                                option.descriptionRes
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private class ModeOption(val mode: PrivilegeMode, val titleRes: Int, val descriptionRes: Int)

private val MODES = listOf(
    ModeOption(PrivilegeMode.SHIZUKU, R.string.perm_mode_shizuku, R.string.perm_mode_shizuku_desc),
    ModeOption(PrivilegeMode.ROOT, R.string.perm_mode_root, R.string.perm_mode_root_desc),
    ModeOption(PrivilegeMode.NONE, R.string.perm_mode_none, R.string.perm_mode_none_desc)
)
