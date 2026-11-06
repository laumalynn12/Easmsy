package com.easmsy.core

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import com.easmsy.R
import com.easmsy.shortcut.ShortcutLauncher
import com.easmsy.shortcut.ShortcutsUpdater

/**
 * Pinned "Launch in Island" shortcuts.  API 26+ uses ShortcutManager; the small legacy
 * broadcast path keeps Android 7.x working.
 */
object Shortcuts {

    const val EXTRA_USER = "com.easmsy.extra.USER"
    const val ACTION_LAUNCH_APP = "com.easmsy.action.LAUNCH_APP"
    private const val PREFIX = "launch:"

    fun shortcutId(packageName: String, user: Int): String = "$PREFIX$packageName@$user"

    fun requestPin(context: Context, entry: AppEntry, pm: PackageManager) {
        val intent = Intent(ACTION_LAUNCH_APP)
            .setComponent(ComponentName(context, ShortcutLauncher::class.java))
            .setData(Uri.parse("package:${entry.packageName}"))
            .putExtra(EXTRA_USER, entry.user)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(ShortcutManager::class.java)
            if (manager == null) {
                toastFailure(context)
                return
            }
            try {
                val shortcut = buildShortcut(context, manager, entry, pm, intent)
                try {
                    manager.addDynamicShortcuts(listOf(shortcut))
                } catch (_: RuntimeException) {
                    // Pinned shortcuts work even when the launcher has no dynamic slot left.
                }
                manager.requestPinShortcut(shortcut, successIntent(context, entry)?.intentSender)
            } catch (_: RuntimeException) {
                toastFailure(context)
            }
        } else {
            pinLegacy(context, entry, pm, intent)
        }
    }

    private fun successIntent(context: Context, entry: AppEntry): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            entry.packageName.hashCode(),
            Intent(context, ShortcutsUpdater::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

    private fun buildShortcut(
        context: Context,
        manager: ShortcutManager,
        entry: AppEntry,
        pm: PackageManager,
        intent: Intent
    ): ShortcutInfo {
        val builder = ShortcutInfo.Builder(context, shortcutId(entry.packageName, Users.id(entry.user)))
            .setIntent(intent)
            .setShortLabel(shortcutLabel(context, entry))
            .setLongLabel(entry.label)
        val bitmap = appIcon(entry, pm, manager.iconMaxWidth.coerceAtLeast(96), manager.iconMaxHeight.coerceAtLeast(96))
        if (bitmap != null) builder.setIcon(Icon.createWithBitmap(bitmap))
        return builder.build()
    }

    fun shortcutLabel(context: Context, entry: AppEntry): String {
        if (!Prefs.dynamicShortcutLabel(context)) return entry.label
        return if (entry.hidden) Prefs.launchShortcutPrefix(context) + entry.label else entry.label
    }

    private fun appIcon(entry: AppEntry, pm: PackageManager, width: Int, height: Int): Bitmap? {
        return try {
            entry.info.loadIcon(pm).toBitmap(width, height)
        } catch (_: RuntimeException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun pinLegacy(context: Context, entry: AppEntry, pm: PackageManager, intent: Intent) {
        val label = shortcutLabel(context, entry)
        val bitmap = appIcon(entry, pm, 192, 192)
        val shortcut = Intent("com.android.launcher.action.INSTALL_SHORTCUT")
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, label)
        if (bitmap != null) shortcut.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
        try {
            context.sendBroadcast(shortcut)
        } catch (_: RuntimeException) {
            toastFailure(context)
        }
    }

    fun updateAllPinned(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val pm = context.packageManager
        val updates = ArrayList<ShortcutInfo>()
        for (shortcut in manager.pinnedShortcuts) {
            if (!shortcut.id.startsWith(PREFIX)) continue
            val suffix = shortcut.id.substring(PREFIX.length)
            val at = suffix.lastIndexOf('@')
            if (at <= 0) continue
            val pkg = suffix.substring(0, at)
            val userId = suffix.substring(at + 1).toIntOrNull() ?: continue
            val user = Users.all(context).firstOrNull { Users.id(it) == userId } ?: continue
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val entry = AppEntry(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    info = info,
                    user = user,
                    system = false,
                    installed = true,
                    enabled = true,
                    hidden = false,
                    suspended = false,
                    launchable = true,
                    managed = false
                )
                val intent = Intent(ACTION_LAUNCH_APP)
                    .setComponent(ComponentName(context, ShortcutLauncher::class.java))
                    .setData(Uri.parse("package:$pkg"))
                    .putExtra(EXTRA_USER, user)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                updates += buildShortcut(context, manager, entry, pm, intent)
            } catch (_: RuntimeException) {
                // The app was removed; leave the launcher shortcut for the user to remove.
            }
        }
        if (updates.isNotEmpty()) {
            try {
                manager.updateShortcuts(updates)
            } catch (_: RuntimeException) {
                // Some launchers do not support pinned shortcut updates.
            }
        }
    }

    private fun toastFailure(context: Context) {
        Toast.makeText(context, R.string.toast_shortcut_failed, Toast.LENGTH_LONG).show()
    }
}
