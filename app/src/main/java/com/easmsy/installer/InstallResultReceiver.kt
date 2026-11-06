package com.easmsy.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.easmsy.core.Notifications

/** Receives PackageInstaller session status and reports it through a notification. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_SUCCESS ->
                Notifications.showInstallResult(context, true, message ?: "Installed")
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (action != null) {
                    action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(action)
                    } catch (_: RuntimeException) {
                        Notifications.showInstallResult(context, false, message)
                    }
                }
            }
            else -> Notifications.showInstallResult(
                context,
                false,
                message ?: context.getString(com.easmsy.R.string.dialog_install_unknown_failure_message)
            )
        }
    }
}
