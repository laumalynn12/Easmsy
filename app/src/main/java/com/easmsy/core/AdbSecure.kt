package com.easmsy.core

import android.content.Context
import android.os.UserHandle
import android.os.UserManager

/**
 * "ADB Secure" is just a device-policy user restriction.  The original project exposed it
 * both as a discovery card and as a hidden setting.  Restricting the owner user requires
 * device-owner privilege; with only a profile owner the restriction applies to Island.
 */
object AdbSecure {

    private val restriction: String = UserManager.DISALLOW_DEBUGGING_FEATURES

    fun targetContext(context: Context): Context {
        val policies = DevicePolicies(context)
        if (policies.isDeviceOwner() || policies.isProfileOwner()) return context
        val profile = Users.profile(context) ?: return context
        return Users.contextAsUser(context, profile) ?: context
    }

    fun isEnabled(context: Context): Boolean {
        val target = targetContext(context)
        return try {
            val dpm = DevicePolicies(target)
            dpm.manager?.getUserRestrictions(dpm.admin)?.getBoolean(restriction) == true
        } catch (_: RuntimeException) {
            Prefs.adbSecure(target)
        }
    }

    fun targetUser(context: Context): UserHandle {
        val target = targetContext(context)
        return if (Users.isCurrent(Users.current()) && target === context) Users.current() else Users.current()
    }

    fun toggle(context: Context, enabled: Boolean): Boolean {
        val target = targetContext(context)
        val policies = DevicePolicies(target)
        if (!policies.isProfileOrDeviceOwnerOnCallingUser()) return false
        val ok = policies.setUserRestriction(restriction, enabled)
        if (ok) Prefs.devicePrefs(target).edit().putBoolean(Prefs.KEY_ADB_SECURE, enabled).apply()
        return ok
    }
}
