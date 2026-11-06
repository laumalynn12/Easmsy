package com.easmsy.watcher

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications
import com.easmsy.core.Prefs
import com.easmsy.core.Users

/**
 * Installs the "Island space is active" quick-control notification in the profile.
 * Only the profile copy of the receiver is useful, so it disables itself in Mainland.
 */
class IslandWatcher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED &&
            action != NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED
        ) return

        if (Users.isOwner()) {
            disable(context)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            disable(context)
            return
        }
        if (!DevicePolicies(context).isProfileOwner()) return
        Notifications.ensureChannels(context)
        if (Prefs.islandWatcher(context)) Notifications.showWatcher(context)
        else Notifications.cancelWatcher(context)
    }

    private fun disable(context: Context) {
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, IslandWatcher::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: RuntimeException) {
            // The component is already disabled, or the package manager is busy.
        }
    }
}
