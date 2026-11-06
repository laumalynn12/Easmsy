package com.easmsy.provision

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Users

/**
 * Catches the broadcasts which the stock provisioning flow sends after Android has
 * created the profile.  This is also the recovery path for ROMs which drop the
 * provisioning-complete broadcast.
 */
class ManualProvisioningReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_USER_INITIALIZE &&
            action != "android.app.action.DEVICE_OWNER_CHANGED"
        ) return

        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                if (DevicePolicies(appContext).isProfileOrDeviceOwnerOnCallingUser()) {
                    Provisioning.complete(appContext)
                } else if (!Users.isOwner()) {
                    // A manual provisioning flow may have enabled the admin just before
                    // the profile broadcast arrived.  Retry once the receiver is invoked.
                    Provisioning.complete(appContext)
                }
            } finally {
                pending.finish()
            }
        }.apply { isDaemon = true }.start()
    }
}
