package com.easmsy.provision

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.easmsy.core.Notifications
import com.easmsy.core.Users

/**
 * Receives reprovisioning requests and the platform's legacy provisioning actions.
 * It performs the required policy calls and exits immediately; no state is kept.
 */
class ProvisioningService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.ID_PROVISIONING, Notifications.showProvisioning(this, !Users.isOwner()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE ||
            action == android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE ||
            action == null
        ) {
            Thread {
                Provisioning.complete(applicationContext)
                stopForeground(true)
                stopSelf(startId)
            }.apply { isDaemon = true }.start()
        } else {
            Provisioning.complete(applicationContext)
            stopForeground(true)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
