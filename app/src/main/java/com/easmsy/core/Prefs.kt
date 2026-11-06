package com.easmsy.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.os.UserHandle
import androidx.annotation.StringRes
import com.easmsy.R

/**
 * Preference storage used by both the framework settings screens and the lightweight
 * helper classes.  Island names are kept in device-protected storage so they are
 * available before the user unlocks the device.
 */
object Prefs {

    const val KEY_DYNAMIC_SHORTCUT_LABEL = "dynamic_shortcut_label"
    const val KEY_LAUNCH_SHORTCUT_PREFIX = "launch_shortcut_prefix"
    const val KEY_SHOW_ADMIN_MESSAGE = "show_admin_message"
    const val KEY_PRESERVE_APP_OPS = "preserve_app_ops"
    const val KEY_ISLAND_NAME = "island_name"
    const val KEY_DEVICE_OWNER_SETUP = "device_owner_setup"
    const val KEY_WATCHER = "watcher"
    const val KEY_ISLAND_WATCHER = "island_watcher"
    const val KEY_APP_WATCHER = "app_watcher"
    const val KEY_ADB_SECURE = "adb_secure"
    const val KEY_FILE_SHUTTLE = "file_shuttle"
    const val KEY_SETUP_COMPLETE = "setup_complete"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    fun devicePrefs(context: Context): SharedPreferences {
        val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            context.createDeviceProtectedStorageContext() else context
        return target.getSharedPreferences("${context.packageName}_device", Context.MODE_PRIVATE)
    }

    fun dynamicShortcutLabel(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYNAMIC_SHORTCUT_LABEL, false)

    fun launchShortcutPrefix(context: Context): String =
        prefs(context).getString(
            KEY_LAUNCH_SHORTCUT_PREFIX,
            context.getString(R.string.default_launch_shortcut_prefix)
        ) ?: context.getString(R.string.default_launch_shortcut_prefix)

    fun showAdminMessage(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_ADMIN_MESSAGE, false)

    fun preserveAppOps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRESERVE_APP_OPS, false)

    fun islandWatcher(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ISLAND_WATCHER, true)

    fun appWatcher(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_WATCHER, true)

    fun adbSecure(context: Context): Boolean =
        devicePrefs(context).getBoolean(KEY_ADB_SECURE, false)

    fun fileShuttle(context: Context): Boolean =
        devicePrefs(context).getBoolean(KEY_FILE_SHUTTLE, false)

    fun setupComplete(context: Context): Boolean =
        devicePrefs(context).getBoolean(KEY_SETUP_COMPLETE, false)

    fun setSetupComplete(context: Context, complete: Boolean) {
        devicePrefs(context).edit().putBoolean(KEY_SETUP_COMPLETE, complete).apply()
    }

    fun islandName(context: Context, user: UserHandle = Process.myUserHandle()): String {
        val stored = devicePrefs(context).getString(nameKey(context, user), null)
        return stored ?: defaultIslandName(context, user)
    }

    fun setName(context: Context, user: UserHandle, name: String) {
        devicePrefs(context).edit().putString(nameKey(context, user), name).apply()
    }

    fun defaultIslandName(context: Context, user: UserHandle = Process.myUserHandle()): String {
        val id = Users.id(user)
        return when (id) {
            0 -> context.getString(R.string.tab_mainland)
            10 -> context.getString(R.string.default_island0_name)
            11 -> context.getString(R.string.default_island1_name)
            12 -> context.getString(R.string.default_island2_name)
            13 -> context.getString(R.string.default_island3_name)
            else -> if (Users.profiles(context).size <= 1)
                context.getString(R.string.default_island_name)
            else context.getString(R.string.default_islandN_name, id)
        }
    }

    private fun nameKey(context: Context, user: UserHandle): String =
        "${context.getString(R.string.key_island_name)}.${Users.id(user)}"
}
