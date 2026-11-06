package com.easmsy.adb

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Users

/**
 * Keeps the profile's debugging restriction in sync with Mainland after boot.  This is a
 * small event-driven task; there is no resident process and no polling.
 */
class ProfileRestrictionsSync : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED && action != Intent.ACTION_BOOT_COMPLETED) return
        val policies = DevicePolicies(context)
        if (Users.isOwner() || !policies.isProfileOwner()) {
            try {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context, ProfileRestrictionsSync::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: RuntimeException) {
                // Best effort.
            }
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val um = context.getSystemService(UserManager::class.java) ?: return
        val ownerRestrictions = try {
            um.getUserRestrictions(Users.owner())
        } catch (_: RuntimeException) {
            return
        }
        val profileRestrictions = try {
            policies.manager?.getUserRestrictions(policies.admin) ?: return
        } catch (_: RuntimeException) {
            return
        }
        val restriction = UserManager.DISALLOW_DEBUGGING_FEATURES
        val enabledInOwner = ownerRestrictions.getBoolean(restriction)
        if (profileRestrictions.getBoolean(restriction) != enabledInOwner) {
            policies.setUserRestriction(restriction, enabledInOwner)
        }
    }
}
