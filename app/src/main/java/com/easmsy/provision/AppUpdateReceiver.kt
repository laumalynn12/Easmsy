package com.easmsy.provision

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications
import com.easmsy.core.Shortcuts
import com.easmsy.core.Users

/** Refresh lightweight state after an app update, without starting a background service. */
class AppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            Shortcuts.updateAllPinned(context)
        } catch (_: RuntimeException) {
            // Launcher may not be available while the user is locked.
        }
        try {
            if (DevicePolicies(context).isProfileOrDeviceOwnerOnCallingUser()) {
                Provisioning.complete(context)
            }
            if (Users.hasProfile(context)) Notifications.showWatcher(context)
        } catch (_: RuntimeException) {
            // Never crash on boot or update.
        }
    }
}
