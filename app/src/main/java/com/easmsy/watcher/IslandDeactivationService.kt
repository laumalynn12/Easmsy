package com.easmsy.watcher

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications
import com.easmsy.core.Users

/**
 * Deactivates (quiet mode) the profile, or reboots the device for a clean restart.
 * The operation is short-lived and is executed as a foreground service because
 * UserManager requires a foreground context on modern Android releases.
 */
class IslandDeactivationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        val action = intent?.action
        if (action == ACTION_RESTART) {
            restart()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(Intent.EXTRA_USER)
        }
        val profile = requested ?: if (!Users.isOwner()) Users.current() else Users.profile(this)
        if (profile == null) {
            openSyncSettings()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        deactivate(profile)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun ensureForeground() {
        try {
            startForeground(Notifications.ID_PROVISIONING, Notifications.showProvisioning(this, true))
        } catch (_: RuntimeException) {
            // Android can reject a background start; the actual work is still attempted.
        }
    }

    private fun deactivate(profile: UserHandle) {
        val manager = getSystemService(UserManager::class.java)
        if (manager == null) {
            openSyncSettings()
            return
        }
        try {
            manager.requestQuietModeEnabled(true, profile)
        } catch (_: SecurityException) {
            openSyncSettings()
        } catch (_: RuntimeException) {
            openSyncSettings()
        }
    }

    private fun restart() {
        val policies = DevicePolicies(this)
        try {
            policies.manager?.lockNow(DevicePolicyManager.FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY)
        } catch (_: RuntimeException) {
            // Device policy may have been revoked; ask the user to reboot manually.
            Toast.makeText(this, R.string.toast_manual_quiet_mode, Toast.LENGTH_LONG).show()
        }
    }

    private fun openSyncSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_SYNC_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: RuntimeException) {
            // No settings activity on this ROM.
        }
        Toast.makeText(this, R.string.toast_manual_quiet_mode, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_DEACTIVATE = "com.easmsy.action.DEACTIVATE_ISLAND"
        const val ACTION_RESTART = "com.easmsy.action.RESTART_ISLAND"
    }
}
