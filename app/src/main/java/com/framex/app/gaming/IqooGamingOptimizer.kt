package com.framex.app.gaming

import android.content.Context
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.catalog.Platform
import com.framex.app.gaming.catalog.VendorCommandExecutor
import com.framex.app.gaming.catalog.VendorCommandRestorer
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * iQOO suite. Its commands are the iQOO entries of the catalog.
 * The class is open so tests can replace [revertOptimizations].
 */
@Singleton
open class IqooGamingOptimizer @Inject constructor(
    @ApplicationContext context: Context,
    privilegeManager: PrivilegeManager,
    settingsRepository: SettingsRepository,
    executor: VendorCommandExecutor,
    restorer: VendorCommandRestorer,
    auditLogRepository: SystemAuditLogRepository,
    deviceDiagnosticManager: DeviceDiagnosticManager
) : VendorGamingOptimizer(
    platform = Platform.IQOO,
    displayName = "iQOO",
    showPulseToast = false,
    context = context,
    privilegeManager = privilegeManager,
    settingsRepository = settingsRepository,
    executor = executor,
    restorer = restorer,
    auditLogRepository = auditLogRepository,
    deviceDiagnosticManager = deviceDiagnosticManager
)
