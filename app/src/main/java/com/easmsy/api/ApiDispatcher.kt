package com.easmsy.api

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.widget.Toast
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications
import com.easmsy.core.Users

/**
 * Protocol implementation for the public Island API.  The actions and extra keys are the
 * original public contract, so existing integrations (Greenify, Ice Box, tasker-style
 * automation) keep working.  New code is written around the small, local DevicePolicies
 * facade instead of the old reflection proxy.
 */
object ApiDispatcher {

    const val RESULT_UNVERIFIED_IDENTITY: Int = Activity.RESULT_FIRST_USER
    const val EXTRA_CALLER_ID = "caller"
    const val LAUNCH_ACTION = "com.easmsy.action.LAUNCH"

    private const val ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE"
    private const val ACTION_UNFREEZE = "com.oasisfeng.island.action.UNFREEZE"
    private const val ACTION_LAUNCH = "com.oasisfeng.island.action.LAUNCH"
    private const val ACTION_SUSPEND = "com.oasisfeng.island.action.SUSPEND"
    private const val ACTION_UNSUSPEND = "com.oasisfeng.island.action.UNSUSPEND"

    private const val OUR_FREEZE = "com.easmsy.action.FREEZE"
    private const val OUR_UNFREEZE = "com.easmsy.action.UNFREEZE"
    private const val OUR_LAUNCH = "com.easmsy.action.LAUNCH"
    private const val OUR_SUSPEND = "com.easmsy.action.SUSPEND"
    private const val OUR_UNSUSPEND = "com.easmsy.action.UNSUSPEND"

    private const val PERMISSION_FREEZE =
        "com.easmsy.permission.FREEZE_PACKAGE"
    private const val PERMISSION_LAUNCH =
        "com.easmsy.permission.LAUNCH_PACKAGE"
    private const val PERMISSION_SUSPEND =
        "com.easmsy.permission.SUSPEND_PACKAGE"

    private const val LEGACY_PERMISSION_FREEZE =
        "com.oasisfeng.island.permission.FREEZE_PACKAGE"
    private const val LEGACY_PERMISSION_LAUNCH =
        "com.oasisfeng.island.permission.LAUNCH_PACKAGE"
    private const val LEGACY_PERMISSION_SUSPEND =
        "com.oasisfeng.island.permission.SUSPEND_PACKAGE"

    private val legacyCallerSignatures = mapOf(
        "com.oasisfeng.greenify" to -373128424,
        "com.catchingnow.icebox" to -502198281
    )

    fun verifyCaller(context: Context, intent: Intent, pkg: String?, uid: Int): String? {
        var packageName = pkg
        var callerUid = uid
        if (packageName == null) {
            val caller = intent.getParcelableExtra<PendingIntent>(EXTRA_CALLER_ID)
                ?: return "Missing required PendingIntent extra: $EXTRA_CALLER_ID"
            packageName = caller.creatorPackage
            callerUid = caller.creatorUid
            if (packageName == null) return "No creator information in PendingIntent"
        }
        if (callerUid > 0 && callerUid == Process.myUid()) return null

        val permissions = permissionsForAction(intent.action)
        if (callerUid >= 0 && permissions.any {
                context.checkPermission(it, Process.myPid(), callerUid) ==
                    PackageManager.PERMISSION_GRANTED
            }
        ) {
            return null
        }

        val expected = legacyCallerSignatures[packageName] ?: return "Unauthorized client: $packageName"
        val signatures = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        } catch (_: PackageManager.NameNotFoundException) {
            return "Client package not found: $packageName"
        }
        if (signatures?.any { it.hashCode() == expected } == true) return null
        return "Package signature mismatch: $packageName"
    }

    /** @return null on success, otherwise a diagnostic string. */
    fun dispatch(context: Context, intent: Intent): String? {
        return when (intent.action) {
            ACTION_FREEZE, OUR_FREEZE -> changeHidden(context, intent, true)
            ACTION_UNFREEZE, OUR_UNFREEZE -> changeHidden(context, intent, false)
            ACTION_LAUNCH, OUR_LAUNCH -> launch(context, intent)
            ACTION_SUSPEND, OUR_SUSPEND -> changeSuspended(context, intent, true)
            ACTION_UNSUSPEND, OUR_UNSUSPEND -> changeSuspended(context, intent, false)
            null -> "No action"
            else -> "Unsupported action: ${intent.action}"
        }
    }

    private fun permissionsForAction(action: String?): List<String> = when (action) {
        ACTION_FREEZE, OUR_FREEZE, ACTION_UNFREEZE, OUR_UNFREEZE ->
            listOf(PERMISSION_FREEZE, LEGACY_PERMISSION_FREEZE)
        ACTION_LAUNCH, OUR_LAUNCH ->
            listOf(PERMISSION_LAUNCH, LEGACY_PERMISSION_LAUNCH)
        ACTION_SUSPEND, OUR_SUSPEND, ACTION_UNSUSPEND, OUR_UNSUSPEND ->
            listOf(PERMISSION_SUSPEND, LEGACY_PERMISSION_SUSPEND)
        else -> emptyList()
    }

    private fun changeHidden(context: Context, intent: Intent, hidden: Boolean): String? {
        val packages = packagesFrom(intent) ?: return "Invalid data in Intent: $intent"
        val policies = DevicePolicies(context)
        if (!policies.isProfileOrDeviceOwnerOnCallingUser()) {
            return "Not a profile owner or device owner"
        }
        val failed = ArrayList<String>()
        for (pkg in packages) {
            if (policies.isApplicationHidden(pkg) == hidden) continue
            if (!policies.setApplicationHidden(pkg, hidden) &&
                policies.isApplicationHidden(pkg) != hidden
            ) {
                failed += pkg
                continue
            }
            if (!hidden) notifyUnfrozen(context, pkg)
        }
        return if (failed.isEmpty()) null else "Failed: $failed"
    }

    private fun changeSuspended(context: Context, intent: Intent, suspended: Boolean): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return "Suspension requires Android 7.0"
        val packages = packagesFrom(intent) ?: return "Invalid data in Intent: $intent"
        val policies = DevicePolicies(context)
        if (!policies.isProfileOrDeviceOwnerOnCallingUser()) {
            return "Not a profile owner or device owner"
        }
        val failed = policies.setPackagesSuspended(packages.toTypedArray(), suspended)
        return if (failed.isEmpty()) null else "Failed packages: ${failed.contentToString()}"
    }

    private fun launch(context: Context, intent: Intent): String? {
        val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)
        val targetContext = if (user != null && !Users.isCurrent(user)) {
            Users.contextAsUser(context, user) ?: context
        } else context

        val data = intent.data ?: return "No data in Intent: $intent"
        when (data.scheme) {
            "package" -> {
                val pkg = data.schemeSpecificPart ?: return "Invalid package URI"
                return launchPackage(targetContext, user ?: Users.current(), pkg)
            }
            "packages" -> {
                val pkgs = data.schemeSpecificPart?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: return "Invalid packages URI"
                for (pkg in pkgs) {
                    val error = launchPackage(targetContext, user ?: Users.current(), pkg)
                    if (error != null) return error
                }
                return null
            }
            "intent", "android-app", "app" -> {
                val target = try {
                    Intent.parseUri(data.toString(), Intent.URI_INTENT_SCHEME)
                } catch (_: Exception) {
                    return "Invalid intent URI: $data"
                }
                target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    targetContext.startActivity(target)
                    null
                } catch (_: ActivityNotFoundException) {
                    "Activity not found for $target"
                } catch (e: RuntimeException) {
                    "Internal exception: $e"
                }
            }
            else -> return "Unsupported intent data scheme: ${data.scheme}"
        }
    }

    private fun launchPackage(context: Context, user: UserHandle, pkg: String): String? {
        val policies = DevicePolicies(context)
        if (policies.isProfileOrDeviceOwnerOnCallingUser() && policies.isApplicationHidden(pkg)) {
            if (!policies.setApplicationHidden(pkg, false)) return "Failed to unfreeze: $pkg"
            notifyUnfrozen(context, pkg)
        }
        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
        if (launcherApps != null) {
            try {
                val activities = launcherApps.getActivityList(pkg, user)
                if (activities.isNotEmpty()) {
                    launcherApps.startMainActivity(activities[0].componentName, user, null, null)
                    return null
                }
            } catch (_: RuntimeException) {
                // Fall through to the package-manager path.
            }
        }
        val target = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(target)
            null
        } catch (_: RuntimeException) {
            "no_launcher_activity"
        }
    }

    private fun packagesFrom(intent: Intent): List<String>? {
        val uri = intent.data ?: return null
        val ssp = uri.schemeSpecificPart ?: return null
        return when (uri.scheme) {
            "package" -> listOf(ssp)
            "packages" -> ssp.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            else -> null
        }
    }

    private fun notifyUnfrozen(context: Context, pkg: String) {
        val label = try {
            context.packageManager.getApplicationInfo(pkg, 0)
                .loadLabel(context.packageManager)
                .toString()
        } catch (_: Exception) {
            null
        }
        if (label != null) Notifications.showAppWatcher(context, pkg, label)
        else Toast.makeText(context, R.string.notification_app_watcher_text, Toast.LENGTH_SHORT).show()
    }
}
