# FrameX Developer Guide: Execution Center & Command Toggles

This guide documents the architecture of the **Execution Center** and provides a step-by-step tutorial for adding new granular optimization commands that users can toggle ON/OFF in FrameX.

---

## 1. Overview & Architecture

FrameX uses an atomic, ledger-backed optimization pipeline:

```text
SettingsRepository (StateFlow)
        ↓
ExecutionCenterSection (UI Switch)
        ↓
Optimization Engines (EsportsOptimizationEngine / VivoGamingOptimizer)
        ↓
LedgerExecutor (Batch Shell Payload) → Shizuku IPC
        ↓
ExecutionLedger (Real-time AppliedOp State) → Gaming Hero Card & Audit Logs
```

- **Dynamic Execution**: If a toggle is `false`, the command is excluded or marked `SKIPPED`. It is **never sent to the privileged shell**.
- **Dynamic UI Reporting**: The Gaming Mode Hero card and Audit Logs reflect only what actually executed via `ExecutionLedger.getSummary()`. No hardcoded strings.

---

## 2. What Was Implemented for Issue #85

### Thermal Throttling Bypass Toggle
- **Problem**: Previously, `cmd thermalservice override-status 0` and Vivo's `game_cube_temper_control 0` executed unconditionally on Gaming Mode activation, which could accelerate hardware wear on devices prone to overheating.
- **Solution**:
  - Added `disableThermalThrottling: StateFlow<Boolean>` defaulting to **`false`** (Safe Mode).
  - Gated universal `cmd thermalservice override-status 0` in `EsportsOptimizationEngine.kt`.
  - Gated Vivo/iQOO `game_cube_temper_control 0` in `VivoGamingOptimizer.kt`.
  - Added collapsible **Execution Center** section in `AboutScreen.kt` (`ExecutionCenterSection.kt`).
  - Added a configuration footnote in `DeepFreezeSafeguardDialog.kt`.

---

## 3. How to Add a New Command Toggle in the Future

Follow these 4 steps to introduce any new optimization command for Universal Android or Vivo/iQOO devices.

### Step 1: Define Setting in `SettingsRepository.kt`

1. Add the constant key in `companion object`:
   ```kotlin
   private const val KEY_ENABLE_FEATURE_XYZ = "gaming_enable_feature_xyz"
   ```
2. Declare the `MutableStateFlow` and public `StateFlow`:
   ```kotlin
   private val _enableFeatureXyz = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_FEATURE_XYZ, false))
   val enableFeatureXyz: StateFlow<Boolean> = _enableFeatureXyz.asStateFlow()
   ```
3. Add the setter method:
   ```kotlin
   fun setEnableFeatureXyz(enabled: Boolean) {
       prefs.edit().putBoolean(KEY_ENABLE_FEATURE_XYZ, enabled).apply()
       _enableFeatureXyz.value = enabled
   }
   ```

---

### Step 2: Gate Execution in the Appropriate Engine

#### A. For Universal Android Devices (`EsportsOptimizationEngine.kt`)
In `EsportsOptimizationEngine.kt`, locate the relevant stage (e.g. `applyMemoryAndThermalOptimizations` or `applyProcessPriorities`):

```kotlin
val xyzSpecs = listOf(
    CommandSpec("cmd custom_service set-mode 1", OpPriority.PRIMARY)
)

if (settingsRepository.enableFeatureXyz.value) {
    ledgerExecutor.executeBatch(Stage.POWER, xyzSpecs)
} else {
    ledgerExecutor.recordSkipped(Stage.POWER, xyzSpecs)
}
```

#### B. For Vivo / iQOO Devices (`VivoGamingOptimizer.kt`)
In `VivoGamingOptimizer.kt`, locate the relevant payload method (e.g. `executePowerAndThermalPayload`, `executeDisplayAndGameSpacePayload`):

```kotlin
val specs = mutableListOf(
    CommandSpec("content insert --uri content://settings/system --bind name:s:base_key --bind value:s:1", OpPriority.PRIMARY)
)

if (settingsRepository.enableFeatureXyz.value) {
    specs.add(CommandSpec("content insert --uri content://settings/secure --bind name:s:vivo_specific_key --bind value:s:1", OpPriority.PRIMARY))
}

ledgerExecutor.executeBatch(Stage.DISPLAY, specs)
```

---

### Step 3: Add the Toggle Card in `ExecutionCenterSection.kt`

1. Pass the flow and callback to `ExecutionCenterSection`:
   ```kotlin
   @Composable
   fun ExecutionCenterSection(
       disableThermalThrottling: Boolean,
       onToggleDisableThermalThrottling: (Boolean) -> Unit,
       enableFeatureXyz: Boolean,
       onToggleEnableFeatureXyz: (Boolean) -> Unit,
       modifier: Modifier = Modifier
   )
   ```
2. Inside the collapsible `Column`, add a new command card:
   ```kotlin
   CommandToggleCard(
       title = "Feature XYZ Name",
       commandSummary = "• Universal: 'cmd custom_service set-mode 1'",
       statusText = if (enableFeatureXyz) "Active" else "Default (Safe)",
       statusColor = if (enableFeatureXyz) Color.Cyan else Color(0xFF10B981),
       isChecked = enableFeatureXyz,
       onCheckedChange = onToggleEnableFeatureXyz,
       warningText = "Optional warning if this setting impacts battery or hardware."
   )
   ```
3. Expose the state in `AboutViewModel.kt` and collect in `AboutScreen.kt`.

---

### Step 4: Add Unit Tests in `VivoSuiteGateTest.kt`

Add a test verifying default state and persistence:

```kotlin
@Test
fun featureXyz_defaultsToFalseAndPersistsState() = runBlocking {
    val prefsData = mutableMapOf<String, Any?>()
    val prefs = createMockSharedPreferences(prefsData)
    val repo = SettingsRepository(MockContext(prefs))

    assertFalse("featureXyz must default to false", repo.enableFeatureXyz.first())

    repo.setEnableFeatureXyz(true)
    assertTrue("featureXyz flow must emit true", repo.enableFeatureXyz.first())
    assertEquals(true, prefsData["gaming_enable_feature_xyz"])
}
```

---

## 4. Best Practices Checklist

- [ ] **Default-Safe**: Destructive or high-heat commands must default to `false`.
- [ ] **Idempotent**: Toggling back and forth must never corrupt device state.
- [ ] **Teardown**: If a command alters system settings, ensure the inverse command is registered in `revert` / `cleanup` routines.
- [ ] **Ledger Compliance**: Always route commands through `LedgerExecutor` so the Hero card and audit logs dynamically update.
