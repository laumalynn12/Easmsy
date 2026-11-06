package com.easmsy.settings

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Users

/**
 * Enables the profile-side settings activity after the profile has been created.
 * The activity is disabled in the manifest so that it is a reliable marker of a
 * successfully provisioned Island, not just an installed copy of the app.
 */
class IslandSettingsEnabler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_USER_INITIALIZE && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Users.isOwner()) return
        if (!DevicePolicies(context).isProfileOwner()) return
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, IslandSettingsActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: RuntimeException) {
            // Component changes can be blocked during very early boot; the next boot retries.
        }
    }
}
