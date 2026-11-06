package com.easmsy.shortcut

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.widget.Toast
import com.easmsy.R
import com.easmsy.api.ApiDispatcher
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Shortcuts
import com.easmsy.core.Users

/**
 * Target of pinned "launch in Island" shortcuts.  It resolves the package and profile,
 * unfreezes the app when possible and starts its launcher activity.  A transparent
 * activity (rather than a receiver) is used so the launcher can keep its normal animation
 * and the operation is allowed to start an activity from the foreground.
 */
class ShortcutLauncher : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent) {
        val action = intent.action
        if (action != Shortcuts.ACTION_LAUNCH_APP &&
            action != "com.oasisfeng.island.action.LAUNCH_APP" &&
            action != "com.easmsy.action.LAUNCH_CLONE"
        ) {
            invalid()
            return
        }
        val data = intent.data ?: return invalid()
        val user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Shortcuts.EXTRA_USER, UserHandle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Shortcuts.EXTRA_USER)
        } ?: Process.myUserHandle()

        when (data.scheme) {
            "package" -> {
                val pkg = data.schemeSpecificPart ?: return invalid()
                launchPackage(pkg, user)
            }
            "android-app", "app" -> launchIntent(data, user)
            else -> invalid()
        }
    }

    private fun launchIntent(uri: Uri, user: UserHandle) {
        val parsed = try {
            Intent.parseUri(
                uri.buildUpon().scheme("android-app").build().toString(),
                Intent.URI_ANDROID_APP_SCHEME
            )
        } catch (_: Exception) {
            invalid()
            return
        }
        val pkg = parsed.`package` ?: uri.host?.substringAfterLast('@')
        if (pkg == null) {
            invalid()
            return
        }
        if (!Users.isCurrent(user)) {
            forwardToProfile(pkg, uri, user)
            return
        }
        parsed.setPackage(pkg)
        parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(parsed)
        } catch (_: RuntimeException) {
            launchPackage(pkg, user)
        }
    }

    private fun launchPackage(pkg: String, user: UserHandle) {
        if (!Users.isCurrent(user)) {
            forwardToProfile(pkg, Uri.parse("package:$pkg"), user)
            return
        }
        if (DevicePolicies(this).isProfileOrDeviceOwnerOnCallingUser()) {
            DevicePolicies(this).setApplicationHidden(pkg, false)
        }
        val launcherApps = getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            try {
                val activities = launcherApps.getActivityList(pkg, user)
                if (activities.isNotEmpty()) {
                    launcherApps.startMainActivity(activities[0].componentName, user, null, null)
                    return
                }
            } catch (_: RuntimeException) {
                // Fall through.
            }
        }
        try {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        } catch (_: RuntimeException) {
            // Report below.
        }
        Toast.makeText(
            this,
            getString(R.string.toast_app_launch_failure, pkg),
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * The API activity is started in the target profile; it performs DevicePolicyManager
     * calls with that profile's privileges and then launches the app there.
     */
    private fun forwardToProfile(pkg: String, uri: Uri, user: UserHandle) {
        val target = Users.contextAsUser(this, user)
        if (target == null) {
            Toast.makeText(this, R.string.prompt_invalid_shortcut, Toast.LENGTH_LONG).show()
            return
        }
        val caller = PendingIntent.getBroadcast(
            this,
            0,
            Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val api = Intent(this, com.easmsy.api.ApiActivity::class.java)
            .setAction(ApiDispatcher.LAUNCH_ACTION)
            .setData(uri)
            .putExtra(ApiDispatcher.EXTRA_CALLER_ID, caller)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            target.startActivity(api)
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.prompt_invalid_shortcut, Toast.LENGTH_LONG).show()
        }
    }

    private fun invalid() {
        Toast.makeText(this, R.string.prompt_invalid_shortcut, Toast.LENGTH_LONG).show()
    }
}
