package com.easmsy.watcher

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications

/**
 * App watcher notifications: tapping "re-freeze" puts an unfrozen app back into the
 * frozen state.  Notifications are per-package and carry the package in their data URI,
 * so no global mutable state is needed.
 */
class IslandAppWatcher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.data ?: return
        val action = intent.action ?: return
        when (action) {
            ACTION_REFREEZE -> refreeze(context, data.schemeSpecificPart ?: return)
            ACTION_DISMISS -> Notifications.cancelAppWatcher(
                context,
                data.schemeSpecificPart ?: return
            )
            ACTION_REVOKE_PERMISSION -> revoke(context, data)
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> Notifications.cancelAppWatcher(
                context,
                data.schemeSpecificPart ?: return
            )
        }
    }

    private fun refreeze(context: Context, pkg: String) {
        val policies = DevicePolicies(context)
        if (!policies.isProfileOrDeviceOwnerOnCallingUser()) return
        val ok = policies.setApplicationHidden(pkg, true) || policies.isApplicationHidden(pkg)
        Notifications.cancelAppWatcher(context, pkg)
        if (!ok) Toast.makeText(context, R.string.toast_error_freeze_failure, Toast.LENGTH_SHORT).show()
    }

    private fun revoke(context: Context, data: Uri) {
        val pkg = data.scheme ?: return
        val permission = data.schemeSpecificPart ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val policies = DevicePolicies(context)
        val manager: DevicePolicyManager = policies.manager ?: return
        val hidden = policies.isApplicationHidden(pkg)
        try {
            if (hidden) policies.setApplicationHidden(pkg, false)
            manager.setPermissionGrantState(
                policies.admin, pkg, permission,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            )
            manager.setPermissionGrantState(
                policies.admin, pkg, permission,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            )
        } catch (_: RuntimeException) {
            Toast.makeText(context, R.string.toast_internal_error, Toast.LENGTH_SHORT).show()
        } finally {
            if (hidden) policies.setApplicationHidden(pkg, true)
        }
        Notifications.cancelAppWatcher(context, pkg)
    }

    companion object {
        const val ACTION_REFREEZE = "com.easmsy.action.REFREEZE"
        const val ACTION_DISMISS = "com.easmsy.action.DISMISS"
        const val ACTION_REVOKE_PERMISSION = "com.easmsy.action.REVOKE_PERMISSION"
        const val ACTION_APP_UNFROZEN = "com.easmsy.action.APP_UNFROZEN"
    }
}
