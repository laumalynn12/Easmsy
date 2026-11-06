package com.easmsy.provision

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.easmsy.R
import com.easmsy.core.Notifications
import com.easmsy.core.Prefs

/** Device administrator / profile owner receiver. */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Notifications.ensureChannels(context)
        Provisioning.complete(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Provisioning.complete(context)
        Toast.makeText(context, R.string.toast_setup_complete, Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Prefs.setSetupComplete(context, false)
        Notifications.cancelWatcher(context)
    }
}
