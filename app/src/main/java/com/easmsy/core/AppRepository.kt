package com.easmsy.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.widget.Toast
import com.easmsy.R

/**
 * Read-only application inventory plus the handful of privileged actions used by the UI.
 * All work is synchronous and on demand; there is no process-wide index or package monitor.
 */
class AppRepository(private val context: Context) {

    private val devicePolicies = DevicePolicies(context)

    fun load(user: UserHandle, includeSystem: Boolean): List<AppEntry> {
        val target = if (Users.isCurrent(user)) context else Users.contextAsUser(context, user)
            ?: return loadFromLauncher(user)
        val pm = target.packageManager
        val launchable = launchablePackages(pm)
        val dpm = DevicePolicies(target)
        val managed = dpm.isProfileOrDeviceOwnerOnCallingUser()

        @Suppress("DEPRECATION")
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
            PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.GET_META_DATA
        val apps = try {
            pm.getInstalledApplications(flags)
        } catch (_: RuntimeException) {
            emptyList()
        }

        val entries = ArrayList<AppEntry>(apps.size)
        for (info in apps) {
            val pkg = info.packageName
            if (pkg == context.packageName) continue
            val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val installed = (info.flags and ApplicationInfo.FLAG_INSTALLED) != 0 || !system
            if (!installed && system && !includeSystem) continue
            if (!includeSystem && system && !launchable.contains(pkg)) continue

            val enabled = isEnabled(pm, pkg, info)
            val hidden = if (installed && managed) runCatching { dpm.isApplicationHidden(pkg) }.getOrDefault(false) else false
            val suspended = if (installed && managed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                runCatching { dpm.isPackageSuspended(pkg) }.getOrDefault(false) else false
            val label = try {
                info.loadLabel(pm).toString()
            } catch (_: RuntimeException) {
                pkg
            }
            entries += AppEntry(
                packageName = pkg,
                label = label,
                info = info,
                user = user,
                system = system,
                installed = installed,
                enabled = enabled,
                hidden = hidden,
                suspended = suspended,
                launchable = launchable.contains(pkg),
                managed = managed
            )
        }
        return entries.sortedWith(
            compareBy<AppEntry>(
                { it.state.ordinal },
                { if (it.system) 1 else 0 },
                { it.label.lowercase() }
            )
        )
    }

    private fun loadFromLauncher(user: UserHandle): List<AppEntry> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return emptyList()
        val activities = try {
            launcherApps.getActivityList(null, user)
        } catch (_: RuntimeException) {
            emptyList()
        }
        return activities
            .map { it.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                val label = runCatching { info.loadLabel(context.packageManager).toString() }
                    .getOrDefault(info.packageName)
                AppEntry(
                    packageName = info.packageName,
                    label = label,
                    info = info,
                    user = user,
                    system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    installed = true,
                    enabled = info.enabled,
                    hidden = false,
                    suspended = false,
                    launchable = true,
                    managed = false
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun launchablePackages(pm: PackageManager): Set<String> {
        val result = HashSet<String>()
        val filters = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        )
        for (filter in filters) {
            try {
                pm.queryIntentActivities(filter, PackageManager.MATCH_DISABLED_COMPONENTS)
                    .forEach { result += it.activityInfo.packageName }
            } catch (_: RuntimeException) {
                // Some ROMs throw when a user is locked; the remaining checks still work.
            }
        }
        return result
    }

    private fun isEnabled(pm: PackageManager, pkg: String, info: ApplicationInfo): Boolean {
        return try {
            val setting = pm.getApplicationEnabledSetting(pkg)
            setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED && info.enabled
        } catch (_: RuntimeException) {
            info.enabled
        }
    }

    fun packageManagerFor(user: UserHandle): PackageManager =
        (if (Users.isCurrent(user)) context else Users.contextAsUser(context, user) ?: context).packageManager

    fun setHidden(entry: AppEntry, hidden: Boolean): Boolean {
        val targetContext = contextFor(entry.user)
        val policies = DevicePolicies(targetContext)
        if (policies.isApplicationHidden(entry.packageName) == hidden) return true
        if (hidden) Ops.preserveBeforeFreeze(targetContext, entry.packageName)
        else Ops.restorePreservedAfterUnfreeze(targetContext, entry.packageName)
        val result = policies.setApplicationHidden(entry.packageName, hidden)
        if (result && !hidden) {
            Notifications.showAppWatcher(targetContext, entry.packageName, entry.label)
        }
        return result || policies.isApplicationHidden(entry.packageName) == hidden
    }

    fun launch(entry: AppEntry): Boolean {
        if (entry.hidden) setHidden(entry, false)
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            try {
                val activities = launcherApps.getActivityList(entry.packageName, entry.user)
                if (activities.isNotEmpty()) {
                    launcherApps.startMainActivity(activities[0].componentName, entry.user, null, null)
                    return true
                }
            } catch (_: RuntimeException) {
                // Fall through to the package manager path below.
            }
        }
        val target = contextFor(entry.user)
        val intent = target.packageManager.getLaunchIntentForPackage(entry.packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            target.startActivity(intent)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun openAppSettings(entry: AppEntry): Boolean {
        if (entry.hidden) setHidden(entry, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            if (launcherApps != null) {
                try {
                    launcherApps.startAppDetailsActivity(
                        ComponentName(entry.packageName, ""),
                        entry.user,
                        null as Rect?,
                        null
                    )
                    return true
                } catch (_: RuntimeException) {
                    // Fall back below.
                }
            }
        }
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${entry.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            contextFor(entry.user).startActivity(intent)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun requestUninstall(entry: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE)
            .setData(Uri.parse("package:${entry.packageName}"))
            .putExtra(Intent.EXTRA_USER, entry.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun reinstallSystemApp(entry: AppEntry): Boolean =
        DevicePolicies(contextFor(entry.user)).enableSystemApp(entry.packageName)

    fun cloneToProfile(packageName: String): Boolean {
        val profile = Users.profile(context) ?: return false
        val target = Users.contextAsUser(context, profile) ?: return false
        val policies = DevicePolicies(target)
        if (policies.installExistingPackage(packageName)) return true
        return policies.enableSystemApp(packageName)
    }

    fun isInstalled(packageName: String, user: UserHandle): Boolean {
        val pm = packageManagerFor(user)
        return try {
            val info = pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            (info.flags and ApplicationInfo.FLAG_INSTALLED) != 0 || (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isHidden(packageName: String, user: UserHandle): Boolean =
        runCatching { DevicePolicies(contextFor(user)).isApplicationHidden(packageName) }.getOrDefault(false)

    fun setSuspended(entry: AppEntry, suspended: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val failed = DevicePolicies(contextFor(entry.user))
            .setPackagesSuspended(arrayOf(entry.packageName), suspended)
        return failed.isEmpty()
    }

    fun greenify(entry: AppEntry): Boolean = Greenify.greenify(context, entry.packageName, entry.user)

    fun contextFor(user: UserHandle): Context =
        if (Users.isCurrent(user)) context else Users.contextAsUser(context, user) ?: context

    fun requestPinShortcut(entry: AppEntry) {
        Shortcuts.requestPin(context, entry, packageManagerFor(entry.user))
    }

    fun ensureUnfrozen(entry: AppEntry): Boolean =
        !entry.hidden || setHidden(entry, false)

    fun installFromMarket(pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: RuntimeException) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: RuntimeException) {
                Toast.makeText(context, R.string.toast_app_launch_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun selfPackage(): String = context.packageName

    fun uid(): Int = Process.myUid()
}
