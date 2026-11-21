package com.framex.app.gaming

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import android.provider.Telephony
import androidx.compose.runtime.Immutable
import com.framex.app.permissions.ShizukuIntents
import com.framex.app.utils.FrameXLog
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class AppInfo(
    val packageName: String,
    val label: String
)

/**
 * Resolves OEM-specific system packages, bloatware lists, and installed user/Google apps
 * for [GamingModeEngine] (Plan 4.4).
 */
@Singleton
class OemPackageResolver @Inject constructor() {

    fun getInstalledUserApps(context: Context): List<AppInfo> {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val selfPkg = context.packageName
            installed.mapNotNull { appInfo ->
                val pkg = appInfo.packageName
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (pkg == selfPkg || pkg in CRITICAL_SYSTEM_PACKAGES) {
                    null
                } else if (!isSystemApp && !isUpdatedSystemApp) {
                    val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(pkg.substringAfterLast('.'))
                    AppInfo(packageName = pkg, label = label)
                } else {
                    null
                }
            }.sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            FrameXLog.e("getInstalledUserApps failed", e, tag = TAG)
            emptyList()
        }
    }

    fun getGoogleAppsForWhitelist(context: Context): List<AppInfo> =
        installedAppInfos(context, GOOGLE_SAFE_TO_SUSPEND)

    /** OEM packages present on this device that the user may protect from suspension. */
    fun getOemAppsForWhitelist(context: Context): List<AppInfo> =
        installedAppInfos(context, (OEM_BLOATED_PACKAGES + OEM_DEEP_FREEZE_PACKAGES) - CRITICAL_SYSTEM_PACKAGES)

    private fun installedAppInfos(context: Context, packages: Collection<String>): List<AppInfo> {
        return try {
            val pm = context.packageManager
            packages.mapNotNull { pkg ->
                try {
                    @Suppress("DEPRECATION")
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(pkg.substringAfterLast('.'))
                    AppInfo(packageName = pkg, label = label)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }.sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            FrameXLog.e("installedAppInfos failed", e, tag = TAG)
            emptyList()
        }
    }

    fun resolvePackagesToSuspend(
        context: Context,
        whitelist: Set<String>,
        launcherGames: Set<String>,
        deepFreezeEnabled: Boolean,
        frameXSuspended: Set<String> = emptySet()
    ): List<String> {
        // Apps the user already disabled or suspended must stay exactly as they are: FrameX
        // would otherwise track them and "restore" them on deactivation. Packages FrameX itself
        // suspended in an earlier, unfinished session are not the user's choice, so they stay
        // eligible and keep being tracked.
        val userDisabled = queryUserDisabledPackages(context) - frameXSuspended
        val protectedSet = buildProtectedSet(
            context = context,
            baseCriticalPackages = CRITICAL_SYSTEM_PACKAGES + context.packageName,
            whitelist = whitelist + launcherGames
        ) + userDisabled
        val userPkgs = getInstalledUserApps(context).map { it.packageName }
        val bloatPkgs = queryBloatwareInstalled(context, protectedSet)
        val googleCandidates = if (deepFreezeEnabled) {
            (GOOGLE_SAFE_TO_SUSPEND + OEM_DEEP_FREEZE_PACKAGES).filter { it !in whitelist }
        } else {
            emptyList()
        }
        return (userPkgs + bloatPkgs + googleCandidates)
            .distinct()
            .filter { it !in protectedSet }
    }

    /** Packages that are disabled or suspended right now, read from one package-manager pass. */
    private fun queryUserDisabledPackages(context: Context): Set<String> = try {
        @Suppress("DEPRECATION")
        context.packageManager.getInstalledApplications(0)
            .filter { !it.enabled || (it.flags and ApplicationInfo.FLAG_SUSPENDED) != 0 }
            .map { it.packageName }
            .toSet()
    } catch (e: Exception) {
        FrameXLog.e("queryUserDisabledPackages failed", e, tag = TAG)
        emptySet()
    }

    fun queryBloatwareInstalled(context: Context, protectedPackages: Set<String>): List<String> {
        val candidates = (OEM_BLOATED_PACKAGES + THIRD_PARTY_BACKGROUND_PACKAGES).toSet()
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }
                .toSet()
            candidates.filter { pkg ->
                pkg in installed && pkg !in protectedPackages
            }
        } catch (e: Exception) {
            FrameXLog.e("queryBloatwareInstalled failed", e, tag = TAG)
            emptyList()
        }
    }

    fun buildProtectedSet(
        context: Context,
        baseCriticalPackages: Set<String>,
        whitelist: Set<String>
    ): Set<String> {
        val dynamic = mutableSetOf<String>()
        dynamic.addAll(baseCriticalPackages)
        dynamic.addAll(whitelist)
        dynamic.addAll(queryLaunchers(context))
        dynamic.addAll(queryEnabledInputMethods(context))
        dynamic.addAll(queryEnabledAccessibilityServices(context))
        dynamic.addAll(queryDefaultDialers(context))
        dynamic.addAll(queryDefaultSmsApps(context))
        dynamic.addAll(queryDefaultBrowsers(context))
        dynamic.addAll(queryDefaultAssistants(context))
        dynamic.addAll(queryActiveAutofillServices(context))
        dynamic.addAll(queryVpnApps(context))
        return dynamic
    }

    private fun queryDefaultSmsApps(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { if (it.isNotEmpty()) result.add(it) }
        } catch (e: Exception) {
            FrameXLog.w("Default SMS package lookup failed", e, tag = TAG)
        }
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .forEach { result.add(it) }
        } catch (e: Exception) {
            FrameXLog.w("SMS intent resolution failed", e, tag = TAG)
        }
        return result
    }

    private fun queryDefaultAssistants(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val flat = Settings.Secure.getString(context.contentResolver, "assistant") ?: ""
            ComponentName.unflattenFromString(flat)?.packageName?.let { result.add(it) }
        } catch (e: Exception) {
            FrameXLog.w("Assistant component lookup failed", e, tag = TAG)
        }
        try {
            val flat = Settings.Secure.getString(context.contentResolver, "voice_interaction_service") ?: ""
            ComponentName.unflattenFromString(flat)?.packageName?.let { result.add(it) }
        } catch (e: Exception) {
            FrameXLog.w("Voice interaction service lookup failed", e, tag = TAG)
        }
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_ASSIST)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .forEach { result.add(it) }
        } catch (e: Exception) {
            FrameXLog.w("Assist intent resolution failed", e, tag = TAG)
        }
        return result
    }

    /**
     * Only the device's actual default browser is protected. [PackageManager.queryIntentActivities]
     * previously also ran here and added every browser-capable app, not just the default one — on
     * a device with multiple browsers installed (e.g. Brave alongside Chrome) that protected every
     * one of them from suspension regardless of which was actually set as default.
     */
    private fun queryDefaultBrowsers(context: Context): Set<String> {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            @Suppress("DEPRECATION")
            val default = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" }
            if (default != null) setOf(default) else emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun queryActiveAutofillServices(context: Context): Set<String> {
        return try {
            val flat = Settings.Secure.getString(context.contentResolver, "autofill_service") ?: return emptySet()
            val pkg = ComponentName.unflattenFromString(flat)?.packageName
            if (!pkg.isNullOrEmpty()) setOf(pkg) else emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * [Settings.Secure.ENABLED_INPUT_METHODS] throws [SecurityException] for any app whose
     * targetSdkVersion is above 33 (this app targets 34) — the OS blocks the read unconditionally,
     * not conditionally, so there is no "try it and see" here: skip the call outright rather than
     * throw on every single activation.
     */
    private fun queryEnabledInputMethods(context: Context): Set<String> {
        if (android.os.Build.VERSION.SDK_INT > 33) return emptySet()
        return try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: return emptySet()
            flat.split(":")
                .mapNotNull { entry ->
                    val componentStr = entry.substringBefore(";")
                    ComponentName.unflattenFromString(componentStr)?.packageName
                }
                .toSet()
        } catch (e: Exception) {
            FrameXLog.e("queryEnabledInputMethods failed", e, tag = TAG)
            emptySet()
        }
    }

    private fun queryEnabledAccessibilityServices(context: Context): Set<String> {
        return try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return emptySet()
            flat.split(":")
                .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
                .toSet()
        } catch (e: Exception) {
            FrameXLog.e("queryEnabledAccessibilityServices failed", e, tag = TAG)
            emptySet()
        }
    }

    private fun queryLaunchers(context: Context): Set<String> {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (e: Exception) {
            FrameXLog.e("queryLaunchers failed", e, tag = TAG)
            emptySet()
        }
    }

    private fun queryDefaultDialers(context: Context): Set<String> {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_DIAL)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (e: Exception) {
            FrameXLog.e("queryDefaultDialers failed", e, tag = TAG)
            emptySet()
        }
    }

    private fun queryVpnApps(context: Context): Set<String> {
        return try {
            val pm = context.packageManager
            val intent = Intent(VpnService.SERVICE_INTERFACE)
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
                .mapNotNull { it.serviceInfo?.packageName }
                .toSet()
        } catch (e: Exception) {
            FrameXLog.e("queryVpnApps failed", e, tag = TAG)
            emptySet()
        }
    }

    companion object {
        private const val TAG = "OemPackageResolver"

        /**
         * Everyday OEM apps that are only suspended when Deep Freeze is on, like the Google set,
         * so nobody loses their camera or dialer without opting in. Each can be protected
         * individually from the Deep Freeze list; the default dialer is always protected anyway.
         */
        val OEM_DEEP_FREEZE_PACKAGES = setOf(
            "com.samsung.android.dialer", "com.samsung.android.calendar",
            "com.sec.android.app.camera", "com.sec.android.app.clockpackage",
            "com.sec.android.gallery3d", "com.sec.android.app.myfiles",
            "com.sec.android.app.vepreload"
        )

        val GOOGLE_SAFE_TO_SUSPEND = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.android.chrome",
            "com.google.android.gm",
            "com.google.android.apps.photos",
            "com.google.android.apps.docs",
            "com.google.android.apps.maps",
            "com.google.android.apps.tachyon",
            "com.google.android.apps.magazines",
            "com.google.android.videos",
            "com.google.android.music",
            "com.google.android.keep",
            "com.google.android.apps.podcasts",
            "com.google.android.apps.nbu.files",
            "com.google.android.apps.fitness",
            "com.google.android.apps.books",
            "com.google.android.apps.translate"
        )

        val CRITICAL_SYSTEM_PACKAGES = setOf(
            "com.framex.app",
            ShizukuIntents.PACKAGE,
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.google.android.dialer",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.android.providers.telephony",
            "com.android.providers.contacts",
            "com.android.providers.media",
            "com.android.providers.downloads",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
            "com.whatsapp",
            "com.whatsapp.w4b"
        )

        val OEM_BLOATED_PACKAGES = setOf(
            "com.vivo.browser", "com.vivo.appstore", "com.vivo.game",
            "com.vivo.weather", "com.vivo.weather.provider",
            "com.vivo.email", "com.vivo.Tips", "com.vivo.feedback",
            "com.vivo.hiboard", "com.vivo.website", "com.vivo.magazine",
            "com.vivo.DoubleApp", "com.vivo.childrenmode", "com.vivo.easyshare",
            "com.vivo.share", "com.vivo.Gallery", "com.vivo.video.floating",
            "com.vivo.translator", "com.vivo.vhome", "com.vivo.globalsearch",
            "com.vivo.health", "com.vivo.space", "com.vivo.card",
            "com.vivo.wallet", "com.vivo.compass", "com.vivo.Notes",
            "com.vivo.soundrecorder", "com.vivo.calculator",
            "com.bbk.account", "com.bbk.cloud", "com.bbk.theme",
            "com.bbk.appstore", "com.bbk.iqoo.logsystem",
            "com.iqoo.website",
            "com.miui.analytics", "com.miui.msa.global", "com.miui.SystemAdSolution",
            "com.miui.bugreport", "com.miui.VideoPlayer", "com.miui.player",
            "com.miui.notes", "com.miui.compass", "com.miui.calculator",
            "com.miui.weather2", "com.miui.yellowpage", "com.miui.Gallery",
            "com.miui.fm", "com.miui.cleanmaster", "com.miui.hybrid",
            "com.miui.cloudservice", "com.miui.cloudbackup", "com.miui.micloudsync",
            "com.xiaomi.mipicks", "com.xiaomi.midrop", "com.xiaomi.scanner",
            "com.xiaomi.payment", "com.xiaomi.glgm", "com.xiaomi.mirecycle",
            "com.samsung.android.bixby.agent", "com.samsung.android.visionintelligence",
            "com.samsung.android.app.spage", "com.samsung.android.game.gamehome",
            "com.samsung.android.game.gametools", "com.samsung.android.app.tips",
            "com.samsung.android.aremoji", "com.samsung.android.arzone",
            "com.samsung.android.kidsinstaller", "com.samsung.android.scloud",
            "com.samsung.android.spay", "com.samsung.android.voc",
            "com.samsung.android.mateagent", "com.samsung.android.dynamiclock",
            "com.samsung.android.themestore", "com.samsung.android.fmm",
            "com.sec.android.app.sbrowser", "com.sec.android.app.samsungapps",
            "com.heytap.browser", "com.heytap.market", "com.heytap.cloud",
            "com.heytap.themestore", "com.heytap.pictorial", "com.heytap.music",
            "com.heytap.yoli", "com.heytap.habit.analysis",
            "com.coloros.weather2", "com.coloros.compass2", "com.coloros.calculator",
            "com.coloros.note", "com.coloros.soundrecorder", "com.coloros.video",
            "com.coloros.gallery3d", "com.coloros.BackupRestore", "com.coloros.floatassistant",
            "com.oplus.games", "com.finshell.fin", "com.nearme.atlas",
            "com.nearme.statistics.rom"
        )

        val THIRD_PARTY_BACKGROUND_PACKAGES = listOf(
            "com.facebook.katana", "com.facebook.orca", "com.facebook.lite",
            "com.facebook.services", "com.facebook.system", "com.facebook.appmanager",
            "com.instagram.android", "com.instagram.barcelona",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.snapchat.android", "com.twitter.android", "com.linkedin.android",
            "com.pinterest", "com.reddit.frontpage", "com.tumblr",
            "org.telegram.messenger", "com.discord", "com.viber.voip",
            "jp.naver.line.android", "com.tencent.mm", "com.Slack",
            "com.skype.raider", "tv.twitch.android.app", "com.spotify.music",
            "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
            "in.startv.hotstar", "com.jio.media.jiobeats", "com.gaana",
            "com.bsbportal.music",
            "com.amazon.mShop.android.shopping", "com.flipkart.android",
            "com.myntra.android", "com.meesho.supply", "in.swiggy.android",
            "com.application.zomato", "com.ubercab", "com.olacabs.customer",
            "com.phonepe.app", "net.one97.paytm",
            "com.google.android.apps.nbu.paisa.user",
            "com.truecaller", "com.microsoft.office.outlook",
            "com.microsoft.teams", "us.zoom.videomeetings",
            "com.opera.browser", "com.opera.mini.native",
            "com.UCMobile.intl", "com.brave.browser", "org.mozilla.firefox",
            "com.duolingo", "com.canva.editor", "com.shazam.android"
        )
    }
}