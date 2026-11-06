package com.easmsy.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.easmsy.MainActivity
import com.easmsy.R
import com.easmsy.settings.IslandSettingsActivity
import com.easmsy.watcher.IslandAppWatcher
import com.easmsy.watcher.IslandDeactivationService

/**
 * Notification plumbing.  Island's design deliberately avoids persistent background
 * services.  Notifications are therefore only used for user-visible, actionable quick
 * controls and for installation/provisioning result reports.
 */
object Notifications {

    const val CHANNEL_ONGOING = "ongoing_task"
    const val CHANNEL_IMPORTANT = "important"
    const val CHANNEL_INSTALL = "app_install"
    const val CHANNEL_ISLAND_WATCHER = "island_watcher"
    const val CHANNEL_APP_WATCHER = "app_watcher"
    const val CHANNEL_DEBUG = "debug"

    const val ID_PROVISIONING = 1
    const val ID_ISLAND_WATCHER = 2
    const val ID_APP_WATCHER = 3
    const val ID_INSTALL_RESULT = 4

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                context.getString(R.string.notification_channel_ongoing_task),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_IMPORTANT,
                context.getString(R.string.notification_channel_important),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INSTALL,
                context.getString(R.string.notification_channel_app_install),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ISLAND_WATCHER,
                context.getString(R.string.notification_channel_island_watcher),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_APP_WATCHER,
                context.getString(R.string.notification_channel_app_watcher),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DEBUG,
                context.getString(R.string.notification_channel_debug),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun pendingActivity(context: Context): PendingIntent {
        val target = if (Users.isOwner()) MainActivity::class.java else IslandSettingsActivity::class.java
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun channelSettings(context: Context, channel: String): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    fun buildWatcher(context: Context): Notification {
        val deactivate = PendingIntent.getService(
            context,
            1,
            Intent(context, IslandDeactivationService::class.java)
                .setAction(IslandDeactivationService.ACTION_DEACTIVATE),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val restart = PendingIntent.getService(
            context,
            2,
            Intent(context, IslandDeactivationService::class.java)
                .setAction(IslandDeactivationService.ACTION_RESTART),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ISLAND_WATCHER)
            .setSmallIcon(R.drawable.ic_island_black_24dp)
            .setContentTitle(context.getString(R.string.notification_island_watcher_title))
            .setContentText(context.getString(R.string.notification_island_watcher_text))
            .setContentIntent(pendingActivity(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.action_deactivate_island), deactivate)
            .addAction(0, context.getString(R.string.action_restart_island), restart)
        channelSettings(context, CHANNEL_ISLAND_WATCHER)?.let {
            builder.addAction(0, context.getString(R.string.action_settings), it)
        }
        return builder.build()
    }

    fun showWatcher(context: Context) {
        if (Users.isOwner() || !DevicePolicies(context).isProfileOwner()) {
            cancelWatcher(context)
            return
        }
        if (!Prefs.islandWatcher(context)) {
            cancelWatcher(context)
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(ID_ISLAND_WATCHER, buildWatcher(context))
    }

    fun cancelWatcher(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_ISLAND_WATCHER)
    }

    fun showProvisioning(context: Context, inIsland: Boolean): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_island_black_24dp)
            .setContentTitle(
                context.getString(
                    if (inIsland) R.string.notification_provisioning_island_title
                    else R.string.notification_provisioning_mainland_title
                )
            )
            .setContentText(context.getString(R.string.notification_provisioning_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun showInstallResult(context: Context, success: Boolean, message: CharSequence?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_INSTALL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notification_channel_app_install))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingActivity(context))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_INSTALL_RESULT, notification)
        if (!success) {
            // No persistent state is kept; the notification is the only report.
        }
    }

    fun showAppWatcher(context: Context, packageName: String, label: String) {
        if (!Prefs.appWatcher(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val data = Uri.fromParts("package", packageName, null)
        val refreeze = PendingIntent.getBroadcast(
            context,
            ID_APP_WATCHER,
            Intent(context, IslandAppWatcher::class.java)
                .setAction(IslandAppWatcher.ACTION_REFREEZE)
                .setData(data),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            ID_APP_WATCHER + 1,
            Intent(context, IslandAppWatcher::class.java)
                .setAction(IslandAppWatcher.ACTION_DISMISS)
                .setData(data),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_APP_WATCHER)
            .setSmallIcon(R.drawable.ic_island_black_24dp)
            .setContentTitle(context.getString(R.string.notification_app_watcher_title, label))
            .setContentText(context.getString(R.string.notification_app_watcher_text))
            .setContentIntent(refreeze)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.action_dismiss), dismiss)
        channelSettings(context, CHANNEL_APP_WATCHER)?.let {
            builder.addAction(0, context.getString(R.string.action_settings), it)
        }
        manager.notify(packageName.hashCode(), builder.build())
    }

    fun cancelAppWatcher(context: Context, packageName: String) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(packageName.hashCode())
    }
}
