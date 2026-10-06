package com.framex.app.gaming

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.ledger.AppliedOp
import com.framex.app.gaming.ledger.ExecutionLedger
import com.framex.app.gaming.ledger.LedgerExecutor
import com.framex.app.gaming.ledger.OpPriority
import com.framex.app.gaming.ledger.OpStatus
import com.framex.app.gaming.ledger.Stage
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class VivoSuiteGateTest {

    @After
    fun tearDown() {
        GamingModeEngine.resetSessionStateForTesting()
    }

    private fun createMockSharedPreferences(data: MutableMap<String, Any?> = mutableMapOf()): SharedPreferences {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putBoolean" -> {
                    data[args[0] as String] = args[1] as Boolean
                    editor
                }
                "putString" -> {
                    data[args[0] as String] = args[1] as String
                    editor
                }
                "putInt" -> {
                    data[args[0] as String] = args[1] as Int
                    editor
                }
                "putLong" -> {
                    data[args[0] as String] = args[1] as Long
                    editor
                }
                "putFloat" -> {
                    data[args[0] as String] = args[1] as Float
                    editor
                }
                "putStringSet" -> {
                    @Suppress("UNCHECKED_CAST")
                    data[args[0] as String] = args[1] as Set<String>
                    editor
                }
                "remove" -> {
                    data.remove(args[0] as String)
                    editor
                }
                "clear" -> {
                    data.clear()
                    editor
                }
                "apply" -> null
                "commit" -> true
                else -> null
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> data[args[0] as String] as? Boolean ?: (args.getOrNull(1) as? Boolean ?: false)
                "getString" -> data[args[0] as String] as? String ?: (args.getOrNull(1) as? String)
                "getStringSet" -> {
                    @Suppress("UNCHECKED_CAST")
                    data[args[0] as String] as? Set<String> ?: (args.getOrNull(1) as? Set<String> ?: emptySet<String>())
                }
                "getInt" -> data[args[0] as String] as? Int ?: (args.getOrNull(1) as? Int ?: 0)
                "getLong" -> data[args[0] as String] as? Long ?: (args.getOrNull(1) as? Long ?: 0L)
                "getFloat" -> data[args[0] as String] as? Float ?: (args.getOrNull(1) as? Float ?: 0f)
                "contains" -> data.containsKey(args[0] as String)
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
    }

    private class MockContext(private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getPackageName(): String = "com.framex.app"
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
    }

    private fun createMockContext(prefs: SharedPreferences): Context = MockContext(prefs)

    private class TestDeviceDiagnosticManager(
        context: Context,
        private val isVivo: Boolean = false,
        private val isIqoo: Boolean = false
    ) : DeviceDiagnosticManager(context) {
        override fun isVivoOnly(): Boolean = isVivo
        override fun isIqooOnly(): Boolean = isIqoo
        override fun isVivoOrIqoo(): Boolean = isVivo || isIqoo
    }

    // =========================================================================
    // 1. Truth Table of resolvePlatformPath (Combinations across Vivo, iQOO, Generic)
    // =========================================================================

    @Test
    fun resolvePlatformPath_truthTable_allCombinations() {
        // Combination 1: Vivo hardware + toggle ON -> VIVO
        assertEquals(
            GamingPlatformPath.VIVO,
            resolvePlatformPath(isVivoHardware = true, isIqooHardware = false, toggleOn = true)
        )

        // Combination 2: iQOO hardware + toggle ON -> IQOO
        assertEquals(
            GamingPlatformPath.IQOO,
            resolvePlatformPath(isVivoHardware = false, isIqooHardware = true, toggleOn = true)
        )

        // Combination 3: Vivo hardware + toggle OFF -> NONE
        assertEquals(
            GamingPlatformPath.NONE,
            resolvePlatformPath(isVivoHardware = true, isIqooHardware = false, toggleOn = false)
        )

        // Combination 4: iQOO hardware + toggle OFF -> NONE
        assertEquals(
            GamingPlatformPath.NONE,
            resolvePlatformPath(isVivoHardware = false, isIqooHardware = true, toggleOn = false)
        )

        // Combination 5: Non-Vivo/iQOO hardware + toggle ON -> GENERIC
        assertEquals(
            GamingPlatformPath.GENERIC,
            resolvePlatformPath(isVivoHardware = false, isIqooHardware = false, toggleOn = true)
        )

        // Combination 6: Non-Vivo/iQOO hardware + toggle OFF -> GENERIC
        assertEquals(
            GamingPlatformPath.GENERIC,
            resolvePlatformPath(isVivoHardware = false, isIqooHardware = false, toggleOn = false)
        )
    }

    private class TestVivoGamingOptimizer(
        context: Context,
        shizukuManager: ShizukuManager,
        settingsRepo: SettingsRepository,
        ledgerExecutor: LedgerExecutor,
        auditRepo: SystemAuditLogRepository,
        gate: VivoSuiteGate,
        deviceDiagnosticManager: DeviceDiagnosticManager = TestDeviceDiagnosticManager(context, isVivo = true)
    ) : VivoGamingOptimizer(context, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, gate, deviceDiagnosticManager) {
        var revertCalled = false
        var revertResult = true
        var revertThrows: Throwable? = null

        override suspend fun revertOptimizations(): Boolean {
            revertCalled = true
            revertThrows?.let { throw it }
            return revertResult
        }
    }

    private class TestIqooGamingOptimizer(
        context: Context,
        shizukuManager: ShizukuManager,
        settingsRepo: SettingsRepository,
        ledgerExecutor: LedgerExecutor,
        auditRepo: SystemAuditLogRepository,
        deviceDiagnosticManager: DeviceDiagnosticManager = TestDeviceDiagnosticManager(context, isVivo = false, isIqoo = true)
    ) : IqooGamingOptimizer(context, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, deviceDiagnosticManager) {
        var revertCalled = false
        override suspend fun revertOptimizations(): Boolean {
            revertCalled = true
            return true
        }
    }
    // =========================================================================
    // 2. Persisted Session Path Behavior Across Toggle Flips & Mid-Session
    // =========================================================================

    @Test
    fun persistedPath_activateWithVivo_flipToggle_revertRetainsVivoPath() = runBlocking {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val gate = VivoSuiteGate(vivoManager, settingsRepo)
        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)
        val testOptimizer = TestVivoGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, gate)
        val esportsEngine = EsportsOptimizationEngine(shizukuManager, settingsRepo, vivoManager, ledgerExecutor)
        val oemResolver = OemPackageResolver(gate)

        val engine = GamingModeEngine(
            context = mockContext,
            shizukuManager = shizukuManager,
            esportsOptimizationEngine = esportsEngine,
            vivoGamingOptimizer = testOptimizer,
            iqooGamingOptimizer = TestIqooGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo),
            settingsRepository = settingsRepo,
            oemPackageResolver = oemResolver,
            deviceDiagnosticManager = vivoManager,
            executionLedger = ledger,
            ledgerExecutor = ledgerExecutor,
            vivoSuiteGate = gate
        )

        // Step 1: Ensure toggle is ON initially and gate resolves to VIVO
        settingsRepo.setVivoOptEnabled(true)
        assertEquals(GamingPlatformPath.VIVO, gate.resolveCurrentPlatformPath())

        // Step 2: Simulate Gaming Mode activation - path persisted as VIVO
        settingsRepo.setGamingPlatformPath(GamingPlatformPath.VIVO)
        assertEquals(GamingPlatformPath.VIVO, settingsRepo.getGamingPlatformPath())

        // Step 3: Flip toggle to OFF while session is active
        settingsRepo.setVivoOptEnabled(false)

        // Live gate now resolves to NONE
        assertEquals(GamingPlatformPath.NONE, gate.resolveCurrentPlatformPath())
        assertFalse(gate.isVivoSuiteEnabled)

        // But persisted session path remains VIVO
        assertEquals(GamingPlatformPath.VIVO, settingsRepo.getGamingPlatformPath())

        // Step 4: Run revertPlatformOptimizations() - must execute Vivo revert path
        engine.revertPlatformOptimizations()
        assertTrue("Vivo revert must be called based on persisted path VIVO even with toggle OFF", testOptimizer.revertCalled)

        // Step 5: Full deactivation clears the path cleanly
        settingsRepo.setGamingPlatformPath(null)
        assertNull(settingsRepo.getGamingPlatformPath())
    }

    @Test
    fun midSessionToggleOff_whileVivoPath_stopsPulse_revertsOptimizations_setsPathNone() = runBlocking {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val gate = VivoSuiteGate(vivoManager, settingsRepo)
        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)
        val testOptimizer = TestVivoGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, gate)
        val esportsEngine = EsportsOptimizationEngine(shizukuManager, settingsRepo, vivoManager, ledgerExecutor)
        val oemResolver = OemPackageResolver(gate)

        val engine = GamingModeEngine(
            context = mockContext,
            shizukuManager = shizukuManager,
            esportsOptimizationEngine = esportsEngine,
            vivoGamingOptimizer = testOptimizer,
            iqooGamingOptimizer = TestIqooGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo),
            settingsRepository = settingsRepo,
            oemPackageResolver = oemResolver,
            deviceDiagnosticManager = vivoManager,
            executionLedger = ledger,
            ledgerExecutor = ledgerExecutor,
            vivoSuiteGate = gate
        )

        // Step 1: Active session on Vivo
        try {
            engine.setSessionActiveForTesting(true)
            settingsRepo.setVivoOptEnabled(true)
            settingsRepo.setGamingPlatformPath(GamingPlatformPath.VIVO)

            // Verify: Pulse must be active when session is active, path is VIVO, and toggle is ON
            assertTrue("Pulse must be active when session is active and path is VIVO", engine.shouldPulseMaintenance.first())
            assertTrue("isPulseActive must be true", engine.isPulseActive)

            // Step 2: Toggle flipped OFF mid-session
            settingsRepo.setVivoOptEnabled(false)
            val revertJob = engine.onVivoOptToggledOffMidSession()
            revertJob?.join()

            // Verify: Pulse stopped, optimizer reverted, and path transitioned to NONE
            assertFalse("shouldPulseMaintenance must flip to false after mid-session toggle-off", engine.shouldPulseMaintenance.first())
            assertFalse(engine.isPulseActive)
            assertTrue(testOptimizer.revertCalled)
            assertEquals(GamingPlatformPath.NONE, settingsRepo.getGamingPlatformPath())
        } finally {
            engine.setSessionActiveForTesting(false)
        }
    }

    @Test
    fun midSessionToggleOff_whenRevertFails_keepsVivoPathAndStopsPulse() = runBlocking {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val gate = VivoSuiteGate(vivoManager, settingsRepo)
        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)
        val testOptimizer = TestVivoGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, gate).apply {
            revertResult = false // Simulate failure (e.g. Shizuku unavailable)
        }
        val esportsEngine = EsportsOptimizationEngine(shizukuManager, settingsRepo, vivoManager, ledgerExecutor)
        val oemResolver = OemPackageResolver(gate)

        val engine = GamingModeEngine(
            context = mockContext,
            shizukuManager = shizukuManager,
            esportsOptimizationEngine = esportsEngine,
            vivoGamingOptimizer = testOptimizer,
            iqooGamingOptimizer = TestIqooGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo),
            settingsRepository = settingsRepo,
            oemPackageResolver = oemResolver,
            deviceDiagnosticManager = vivoManager,
            executionLedger = ledger,
            ledgerExecutor = ledgerExecutor,
            vivoSuiteGate = gate
        )

        // Step 1: Active session on Vivo
        try {
            engine.setSessionActiveForTesting(true)
            settingsRepo.setVivoOptEnabled(true)
            settingsRepo.setGamingPlatformPath(GamingPlatformPath.VIVO)

            // Verify: Pulse must be active initially
            assertTrue("Pulse must be active initially", engine.shouldPulseMaintenance.first())
            assertTrue("isPulseActive must be true initially", engine.isPulseActive)

            // Step 2: Toggle flipped OFF mid-session, revert fails
            settingsRepo.setVivoOptEnabled(false)
            val revertJob = engine.onVivoOptToggledOffMidSession()
            revertJob?.join()

            // Verify: Pulse stopped, revert was attempted, but path remains VIVO so deactivation retries later
            assertFalse("shouldPulseMaintenance must flip to false even if revert fails", engine.shouldPulseMaintenance.first())
            assertFalse(engine.isPulseActive)
            assertTrue(testOptimizer.revertCalled)
            assertEquals(GamingPlatformPath.VIVO, settingsRepo.getGamingPlatformPath())
        } finally {
            engine.setSessionActiveForTesting(false)
        }
    }

    @Test
    fun revertPlatformOptimizations_whenPathIsNull_fallsBackToHardwareCheck() = runBlocking {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val gate = VivoSuiteGate(vivoManager, settingsRepo)
        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)
        val testOptimizer = TestVivoGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, gate)
        val esportsEngine = EsportsOptimizationEngine(shizukuManager, settingsRepo, vivoManager, ledgerExecutor)
        val oemResolver = OemPackageResolver(gate)

        val engine = GamingModeEngine(
            context = mockContext,
            shizukuManager = shizukuManager,
            esportsOptimizationEngine = esportsEngine,
            vivoGamingOptimizer = testOptimizer,
            iqooGamingOptimizer = TestIqooGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo),
            settingsRepository = settingsRepo,
            oemPackageResolver = oemResolver,
            deviceDiagnosticManager = vivoManager,
            executionLedger = ledger,
            ledgerExecutor = ledgerExecutor,
            vivoSuiteGate = gate
        )

        // Path is null (e.g. app upgrade or crash recovery without persisted path)
        settingsRepo.setGamingPlatformPath(null)
        assertNull(settingsRepo.getGamingPlatformPath())

        // On Vivo hardware, null path MUST fallback to Vivo revert
        engine.revertPlatformOptimizations()
        assertTrue("Fallback must invoke Vivo revert on Vivo hardware when path is null", testOptimizer.revertCalled)
    }

    @Test
    fun revertPlatformOptimizations_whenPathIsNone_skipsRevert() = runBlocking {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val gate = VivoSuiteGate(vivoManager, settingsRepo)
        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)
        val testOptimizer = TestVivoGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, gate)
        val esportsEngine = EsportsOptimizationEngine(shizukuManager, settingsRepo, vivoManager, ledgerExecutor)
        val oemResolver = OemPackageResolver(gate)

        val engine = GamingModeEngine(
            context = mockContext,
            shizukuManager = shizukuManager,
            esportsOptimizationEngine = esportsEngine,
            vivoGamingOptimizer = testOptimizer,
            iqooGamingOptimizer = TestIqooGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo),
            settingsRepository = settingsRepo,
            oemPackageResolver = oemResolver,
            deviceDiagnosticManager = vivoManager,
            executionLedger = ledger,
            ledgerExecutor = ledgerExecutor,
            vivoSuiteGate = gate
        )

        // Path is explicitly NONE (e.g. baseline-only session)
        settingsRepo.setGamingPlatformPath(GamingPlatformPath.NONE)

        engine.revertPlatformOptimizations()
        assertFalse("NONE path must not invoke Vivo revert", testOptimizer.revertCalled)
    }

    @Test
    fun midSessionToggleOff_whenNotVivoPath_doesNothing() = runBlocking {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val gate = VivoSuiteGate(vivoManager, settingsRepo)
        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)
        val testOptimizer = TestVivoGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, gate)
        val esportsEngine = EsportsOptimizationEngine(shizukuManager, settingsRepo, vivoManager, ledgerExecutor)
        val oemResolver = OemPackageResolver(gate)

        val engine = GamingModeEngine(
            context = mockContext,
            shizukuManager = shizukuManager,
            esportsOptimizationEngine = esportsEngine,
            vivoGamingOptimizer = testOptimizer,
            iqooGamingOptimizer = TestIqooGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo),
            settingsRepository = settingsRepo,
            oemPackageResolver = oemResolver,
            deviceDiagnosticManager = vivoManager,
            executionLedger = ledger,
            ledgerExecutor = ledgerExecutor,
            vivoSuiteGate = gate
        )

        // Session path is GENERIC
        settingsRepo.setGamingPlatformPath(GamingPlatformPath.GENERIC)
        val job = engine.onVivoOptToggledOffMidSession()
        assertNull(job)
        assertFalse(testOptimizer.revertCalled)
        assertEquals(GamingPlatformPath.GENERIC, settingsRepo.getGamingPlatformPath())
    }

    @Test
    fun vivoSuiteGate_flowReactiveConsistency() {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        // Test on Vivo device
        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val vivoGate = VivoSuiteGate(vivoManager, settingsRepo)

        settingsRepo.setVivoOptEnabled(true)
        assertTrue(vivoGate.isVivoSuiteEnabledFlow.value)

        settingsRepo.setVivoOptEnabled(false)
        assertFalse(vivoGate.isVivoSuiteEnabledFlow.value)

        // Test on Non-Vivo device: always false regardless of toggle
        val nonVivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = false)
        val nonVivoGate = VivoSuiteGate(nonVivoManager, settingsRepo)

        settingsRepo.setVivoOptEnabled(true)
        assertFalse(nonVivoGate.isVivoSuiteEnabledFlow.value)

        settingsRepo.setVivoOptEnabled(false)
        assertFalse(nonVivoGate.isVivoSuiteEnabledFlow.value)
    }

    // =========================================================================
    // 3. VivoGamingOptimizer Manual Methods Disabled When Gate Is Off
    // =========================================================================

    @Test
    fun optimizerManualMethods_returnDisabledValues_whenGateIsOff() = runBlocking {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        // Gate off: Vivo hardware, but toggle is OFF
        settingsRepo.setVivoOptEnabled(false)
        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val gate = VivoSuiteGate(vivoManager, settingsRepo)
        assertFalse("Gate must be disabled when toggle is off", gate.isVivoSuiteEnabled)

        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)

        val optimizer = VivoGamingOptimizer(
            context = mockContext,
            shizukuManager = shizukuManager,
            settingsRepository = settingsRepo,
            ledgerExecutor = ledgerExecutor,
            auditLogRepository = auditRepo,
            vivoSuiteGate = gate,
            deviceDiagnosticManager = vivoManager
        )

        val sharedTools = VivoIqooSharedTools(shizukuManager, gate)

        // Optimization methods are gated by isVivoSuiteEnabled and return disabled values immediately
        assertFalse("injectPerfGameList must return false when suite disabled", sharedTools.injectPerfGameList("com.example.game"))
        assertEquals("getPerfGameList must return emptyList when suite disabled", emptyList<String>(), sharedTools.getPerfGameList())
        assertEquals("getRawPerfGameList must return empty string when suite disabled", "", sharedTools.getRawPerfGameList())
        assertEquals("getPackageCompileFilter must return empty string when suite disabled", "", sharedTools.getPackageCompileFilter("com.example.game"))
        assertFalse("compileSpeedAot must return false when suite disabled", sharedTools.compileSpeedAot("com.example.game"))
        assertFalse("isSpeedCompiled must return false when suite disabled", sharedTools.isSpeedCompiled("com.example.game"))
    }

    @Test
    fun optimizerManualMethods_returnDisabledValues_whenNonVivoHardware() = runBlocking {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)

        // Gate off: Non-Vivo hardware, even if toggle is ON
        settingsRepo.setVivoOptEnabled(true)
        val nonVivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = false)
        val gate = VivoSuiteGate(nonVivoManager, settingsRepo)
        assertFalse("Gate must be disabled on non-Vivo hardware", gate.isVivoSuiteEnabled)
        assertFalse("Hardware is not Vivo", gate.isVivoHardware)

        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)

        val optimizer = VivoGamingOptimizer(
            context = mockContext,
            shizukuManager = shizukuManager,
            settingsRepository = settingsRepo,
            ledgerExecutor = ledgerExecutor,
            auditLogRepository = auditRepo,
            vivoSuiteGate = gate,
            deviceDiagnosticManager = nonVivoManager
        )
        val sharedTools = VivoIqooSharedTools(shizukuManager, gate)

        // All methods (both optimizations and cleanup) are disabled on non-Vivo hardware
        assertFalse(sharedTools.injectPerfGameList("com.example.game"))
        assertFalse("removePerfGame must return false on non-Vivo hardware", sharedTools.removePerfGame("com.example.game"))
        assertEquals(emptyList<String>(), sharedTools.getPerfGameList())
        assertEquals("", sharedTools.getRawPerfGameList())
        assertEquals("", sharedTools.getPackageCompileFilter("com.example.game"))
        assertFalse(sharedTools.compileSpeedAot("com.example.game"))
        assertFalse(sharedTools.isSpeedCompiled("com.example.game"))
    }

    private data class EngineTestRig(
        val engine: GamingModeEngine,
        val settingsRepo: SettingsRepository,
        val testOptimizer: TestVivoGamingOptimizer,
        val ledger: ExecutionLedger
    )

    private fun createTestEngine(revertResult: Boolean = true): EngineTestRig {
        val sharedPrefs = createMockSharedPreferences()
        val mockContext = createMockContext(sharedPrefs)
        val settingsRepo = SettingsRepository(mockContext)
        val vivoManager = TestDeviceDiagnosticManager(mockContext, isVivo = true)
        val gate = VivoSuiteGate(vivoManager, settingsRepo)
        val auditRepo = SystemAuditLogRepository(settingsRepo)
        val shizukuManager = ShizukuManager(auditRepo)
        val ledger = ExecutionLedger()
        val ledgerExecutor = LedgerExecutor(shizukuManager, ledger, auditRepo)
        val testOptimizer = TestVivoGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo, gate).apply {
            this.revertResult = revertResult
        }
        val esportsEngine = EsportsOptimizationEngine(shizukuManager, settingsRepo, vivoManager, ledgerExecutor)
        val oemResolver = OemPackageResolver(gate)

        val engine = GamingModeEngine(
            context = mockContext,
            shizukuManager = shizukuManager,
            esportsOptimizationEngine = esportsEngine,
            vivoGamingOptimizer = testOptimizer,
            iqooGamingOptimizer = TestIqooGamingOptimizer(mockContext, shizukuManager, settingsRepo, ledgerExecutor, auditRepo),
            settingsRepository = settingsRepo,
            oemPackageResolver = oemResolver,
            deviceDiagnosticManager = vivoManager,
            executionLedger = ledger,
            ledgerExecutor = ledgerExecutor,
            vivoSuiteGate = gate
        )
        return EngineTestRig(engine, settingsRepo, testOptimizer, ledger)
    }

    @Test
    fun cleanupStaleVivoPath_whenPriorPathWasVivoAndNewPathIsNone_revertsOptimizationsAndReturnsTrue() = runBlocking {
        val rig = createTestEngine(revertResult = true)
        rig.settingsRepo.setGamingPlatformPath(GamingPlatformPath.VIVO)
        rig.settingsRepo.saveGamingOptimizationSnapshot(
            GamingOptimizationSnapshot(
                activeGamePackage = "com.example.game",
                activeGameUid = 10001,
                timestamp = System.currentTimeMillis(),
                minRefreshRate = null,
                peakRefreshRate = null,
                touchResponseSpeed = null,
                userPreferredDisplayModeId = null,
                affectedPackages = emptySet()
            )
        )
        rig.ledger.upsert(
            AppliedOp(
                stage = Stage.POWER,
                key = "monster_mode",
                displayValue = "1",
                rawCommand = "cmd",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )
        rig.ledger.upsert(
            AppliedOp(
                stage = Stage.APPS,
                key = "suspended_apps",
                displayValue = "1",
                rawCommand = "cmd",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )

        val result = rig.engine.cleanupStaleVivoPath(GamingPlatformPath.NONE)

        assertTrue("cleanupStaleVivoPath must return true when revert succeeds", result)
        assertTrue("revertOptimizations must be called when previous path was VIVO and new path is NONE", rig.testOptimizer.revertCalled)
        assertFalse("Snapshot must be cleared after successful cleanup", rig.settingsRepo.hasActiveGamingSnapshot())
        assertTrue("Non-Vivo stages like APPS must remain in ledger", rig.ledger.ops.value.any { it.stage == Stage.APPS })
        assertFalse("Vivo platform stages like POWER must be removed from ledger", rig.ledger.ops.value.any { it.stage == Stage.POWER })
    }

    @Test
    fun cleanupStaleVivoPath_whenPriorPathWasVivoAndNewPathIsNone_whenRevertFails_returnsFalse() = runBlocking {
        val rig = createTestEngine(revertResult = false)
        rig.settingsRepo.setGamingPlatformPath(GamingPlatformPath.VIVO)
        rig.settingsRepo.saveGamingOptimizationSnapshot(
            GamingOptimizationSnapshot(
                activeGamePackage = "com.example.game",
                activeGameUid = 10001,
                timestamp = System.currentTimeMillis(),
                minRefreshRate = null,
                peakRefreshRate = null,
                touchResponseSpeed = null,
                userPreferredDisplayModeId = null,
                affectedPackages = emptySet()
            )
        )
        rig.ledger.upsert(
            AppliedOp(
                stage = Stage.POWER,
                key = "monster_mode",
                displayValue = "1",
                rawCommand = "cmd",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )

        val result = rig.engine.cleanupStaleVivoPath(GamingPlatformPath.NONE)

        assertFalse("cleanupStaleVivoPath must return false when revert fails", result)
        assertTrue("revertOptimizations must be called when attempting cleanup", rig.testOptimizer.revertCalled)
        assertTrue("Snapshot must NOT be cleared when revert fails", rig.settingsRepo.hasActiveGamingSnapshot())
        assertTrue("Ledger stages must NOT be removed when revert fails", rig.ledger.ops.value.any { it.stage == Stage.POWER })
    }

    @Test
    fun cleanupStaleVivoPath_whenRevertThrowsException_catchesAndReturnsFalse() = runBlocking {
        val rig = createTestEngine()
        rig.testOptimizer.revertThrows = RuntimeException("Remote IPC failure")
        rig.settingsRepo.setGamingPlatformPath(GamingPlatformPath.VIVO)

        val result = rig.engine.cleanupStaleVivoPath(GamingPlatformPath.NONE)

        assertFalse("cleanupStaleVivoPath must return false when revert throws exception", result)
        assertTrue("revertOptimizations must have been called", rig.testOptimizer.revertCalled)
    }

    @Test
    fun cleanupStaleVivoPath_whenPriorPathWasNotVivoOrNewPathIsVivo_skipsRevertAndReturnsTrue() = runBlocking {
        val rig = createTestEngine(revertResult = true)

        // Case 1: Prior path was NONE, new path is NONE
        rig.settingsRepo.setGamingPlatformPath(GamingPlatformPath.NONE)
        var result = rig.engine.cleanupStaleVivoPath(GamingPlatformPath.NONE)
        assertTrue("Must return true when prior path was NONE", result)
        assertFalse("Revert must not be called when prior path was NONE", rig.testOptimizer.revertCalled)

        // Case 2: Prior path was GENERIC, new path is NONE
        rig.testOptimizer.revertCalled = false
        rig.settingsRepo.setGamingPlatformPath(GamingPlatformPath.GENERIC)
        result = rig.engine.cleanupStaleVivoPath(GamingPlatformPath.NONE)
        assertTrue("Must return true when prior path was GENERIC", result)
        assertFalse("Revert must not be called when prior path was GENERIC", rig.testOptimizer.revertCalled)

        // Case 3: Prior path was VIVO, but new path is also VIVO
        rig.testOptimizer.revertCalled = false
        rig.settingsRepo.setGamingPlatformPath(GamingPlatformPath.VIVO)
        result = rig.engine.cleanupStaleVivoPath(GamingPlatformPath.VIVO)
        assertTrue("Must return true when new path is also VIVO", result)
        assertFalse("Revert must not be called when new path is also VIVO", rig.testOptimizer.revertCalled)

        // Case 4: Prior path was null, new path is NONE
        rig.testOptimizer.revertCalled = false
        rig.settingsRepo.setGamingPlatformPath(null)
        result = rig.engine.cleanupStaleVivoPath(GamingPlatformPath.NONE)
        assertTrue("Must return true when prior path was null", result)
        assertFalse("Revert must not be called when prior path was null", rig.testOptimizer.revertCalled)

        // Case 5: Prior path was VIVO, but new path is GENERIC -> Must revert Vivo settings
        rig.testOptimizer.revertCalled = false
        rig.settingsRepo.setGamingPlatformPath(GamingPlatformPath.VIVO)
        result = rig.engine.cleanupStaleVivoPath(GamingPlatformPath.GENERIC)
        assertTrue("Must return true when transitioning from VIVO to GENERIC", result)
        assertTrue("Revert must be called when transitioning from VIVO to GENERIC", rig.testOptimizer.revertCalled)
    }

    @Test
    fun deepFreezeSettings_defaultToFalseAndPersistState() = runBlocking {
        val prefsData = mutableMapOf<String, Any?>()
        val prefs = createMockSharedPreferences(prefsData)
        val context = MockContext(prefs)
        val repo = SettingsRepository(context)

        // Verify default-off state (Safeguards active)
        assertFalse("deepFreezeEnabled must default to false", repo.deepFreezeEnabled.first())
        assertFalse("hasSeenDeepFreezeNotice must default to false", repo.hasSeenDeepFreezeNotice.first())

        // Toggle deep freeze on
        repo.setDeepFreezeEnabled(true)
        assertTrue("deepFreezeEnabled flow must emit true", repo.deepFreezeEnabled.first())
        assertEquals(true, prefsData["gaming_deep_freeze_enabled"])

        // Mark notice seen
        repo.setHasSeenDeepFreezeNotice(true)
        assertTrue("hasSeenDeepFreezeNotice flow must emit true", repo.hasSeenDeepFreezeNotice.first())
        assertEquals(true, prefsData["has_seen_deep_freeze_notice"])

        // Verify Google app list integrity (Issue #78 preservation)
        assertTrue("GOOGLE_SAFE_TO_SUSPEND must contain youtube", GamingModeEngine.GOOGLE_SAFE_TO_SUSPEND.contains("com.google.android.youtube"))
        assertTrue("GOOGLE_SAFE_TO_SUSPEND must contain chrome", GamingModeEngine.GOOGLE_SAFE_TO_SUSPEND.contains("com.android.chrome"))
        assertTrue("GOOGLE_SAFE_TO_SUSPEND must contain gm", GamingModeEngine.GOOGLE_SAFE_TO_SUSPEND.contains("com.google.android.gm"))
    }

    @Test
    fun launcherGames_emptyByDefaultAndSynchronousRead() = runBlocking {
        val prefsData = mutableMapOf<String, Any?>()
        val prefs = createMockSharedPreferences(prefsData)
        val context = MockContext(prefs)
        val repo = SettingsRepository(context)

        // Default empty state
        assertTrue("Launcher games must be empty by default", repo.launcherGames.value.isEmpty())

        // Add a game
        repo.toggleLauncherGame("com.pubg.imobile")
        assertTrue("Launcher games must contain added game", repo.launcherGames.value.contains("com.pubg.imobile"))

        // Remove the game
        repo.toggleLauncherGame("com.pubg.imobile")
        assertTrue("Launcher games must be empty after removing", repo.launcherGames.value.isEmpty())
    }

    @Test
    fun disableThermalThrottling_defaultsToFalseAndPersistsState() = runBlocking {
        val prefsData = mutableMapOf<String, Any?>()
        val prefs = createMockSharedPreferences(prefsData)
        val context = MockContext(prefs)
        val repo = SettingsRepository(context)

        // Verify default-off state (Safe Mode: thermal safety preserved)
        assertFalse("disableThermalThrottling must default to false", repo.disableThermalThrottling.first())

        // Toggle disable thermal throttling on (Aggressive Mode)
        repo.setDisableThermalThrottling(true)
        assertTrue("disableThermalThrottling flow must emit true", repo.disableThermalThrottling.first())
        assertEquals(true, prefsData["gaming_disable_thermal_throttling"])

        // Toggle back to safe mode
        repo.setDisableThermalThrottling(false)
        assertFalse("disableThermalThrottling flow must emit false", repo.disableThermalThrottling.first())
        assertEquals(false, prefsData["gaming_disable_thermal_throttling"])
    }

    @Test
    fun granularExecutionSettings_defaultsAndPersistence() = runBlocking {
        val prefsData = mutableMapOf<String, Any?>()
        val prefs = createMockSharedPreferences(prefsData)
        val context = MockContext(prefs)
        val repo = SettingsRepository(context)

        // Common defaults
        assertTrue(repo.ramCachePreTrimEnabled.first())
        assertTrue(repo.disablePhantomProcKiller.first())
        assertTrue(repo.cpuPriorityLock.first())

        // Generic defaults (all false)
        assertFalse(repo.fixedPerformanceMode.first())
        assertFalse(repo.networkFirewall.first())
        assertFalse(repo.refreshRateLock.first())
        assertFalse(repo.touchBoost.first())

        // Vivo defaults (all false)
        assertFalse(repo.vivoMonsterMode.first())
        assertFalse(repo.vivoVipThread.first())
        assertFalse(repo.vivoGameHandshake.first())
        assertFalse(repo.vivoGyroPromotion.first())
        assertFalse(repo.vivoTouchOptimization.first())
        assertFalse(repo.vivo144FpsUnlock.first())
        assertFalse(repo.vivoMaintenancePulse.first())
        assertFalse(repo.vivoPulseGamePlusMode.first())
        assertFalse(repo.vivoPulseStandardPromotion.first())
        assertFalse(repo.vivoPulseSceneMoreFps.first())

        // Test toggling
        repo.setRamCachePreTrimEnabled(false)
        assertFalse(repo.ramCachePreTrimEnabled.first())

        repo.setDisablePhantomProcKiller(false)
        assertFalse(repo.disablePhantomProcKiller.first())

        repo.setFixedPerformanceMode(true)
        assertTrue(repo.fixedPerformanceMode.first())

        repo.setVivoMonsterMode(true)
        assertTrue(repo.vivoMonsterMode.first())

        repo.setVivo144FpsUnlock(true)
        assertTrue(repo.vivo144FpsUnlock.first())

        repo.setVivoMaintenancePulse(true)
        assertTrue(repo.vivoMaintenancePulse.first())

        repo.setVivoPulseGamePlusMode(true)
        assertTrue(repo.vivoPulseGamePlusMode.first())
    }
}


