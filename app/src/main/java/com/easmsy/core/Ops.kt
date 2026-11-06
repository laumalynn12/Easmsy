package com.easmsy.core

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Minimal App Ops access.  These methods require the signature-level GET_APP_OPS_STATS
 * permission, which cannot be requested by a normal app.  When the permission has been
 * granted once through ADB (as documented in the original project), the calls work.
 */
object Ops {

    private const val GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS"
    private const val MODE_IGNORED = AppOpsManager.MODE_IGNORED
    private const val MODE_ALLOWED = AppOpsManager.MODE_ALLOWED
    private const val PREFS_PREFIX = "preserved_app_ops."

    private val opNames = mapOf(
        "read_phone_state" to "android:read_phone_state",
        "read_sms" to "android:read_sms",
        "coarse_location" to "android:coarse_location",
        "fine_location" to "android:fine_location",
        "read_external_storage" to "android:read_external_storage"
    )

    fun isAvailable(context: Context): Boolean =
        context.checkPermission(GET_APP_OPS_STATS, android.os.Process.myPid(), android.os.Process.myUid()) ==
            PackageManager.PERMISSION_GRANTED

    fun hasPermission(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun appsWithPermission(context: Context, permission: String): List<Pair<String, String>> {
        val pm = context.packageManager
        val result = ArrayList<Pair<String, String>>()
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        try {
            for (pkg in pm.getInstalledPackages(flags)) {
                if (pkg.requestedPermissions?.contains(permission) != true) continue
                if (pkg.packageName == context.packageName) continue
                if ((pkg.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue
                result += pkg.packageName to (runCatching {
                    pkg.applicationInfo.loadLabel(pm).toString()
                }.getOrDefault(pkg.packageName))
            }
        } catch (_: RuntimeException) {
            // Package manager can be unavailable while the user is locked.
        }
        return result.sortedBy { it.second.lowercase() }
    }

    fun revokeForApps(context: Context, permission: String, opKey: String): Int {
        if (!isAvailable(context)) return -1
        val op = opNames[opKey] ?: return -1
        var count = 0
        for ((pkg, _) in appsWithPermission(context, permission)) {
            if (setMode(context, op, pkg, MODE_IGNORED)) count++
        }
        return count
    }

    fun setMode(context: Context, op: String, pkg: String, mode: Int): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val uid = try {
            context.packageManager.getApplicationInfo(pkg, 0).uid
        } catch (_: Exception) {
            return false
        }
        // API 29 introduced the string-based overload.  Older versions accept an int op id.
        val stringMethod = Reflection.method(
            AppOpsManager::class.java,
            "setMode",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            Int::class.javaPrimitiveType!!
        )
        if (stringMethod != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching { stringMethod.invoke(appOps, op, uid, pkg, mode) }.isSuccess
        }
        val intOp = opId(op) ?: return false
        val intMethod = Reflection.method(
            AppOpsManager::class.java,
            "setMode",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            Int::class.javaPrimitiveType!!
        )
        return try {
            intMethod?.invoke(appOps, intOp, uid, pkg, mode)
            intMethod != null
        } catch (_: Exception) {
            false
        }
    }

    fun currentMode(context: Context, op: String, pkg: String): Int? {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return null
        val uid = try {
            context.packageManager.getApplicationInfo(pkg, 0).uid
        } catch (_: Exception) {
            return null
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val method = Reflection.method(
                    AppOpsManager::class.java,
                    "unsafeCheckOpNoThrow",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java
                )
                Reflection.invoke<Int>(appOps, method, op, uid, pkg)
            } else {
                val id = opId(op) ?: return null
                val method = Reflection.method(
                    AppOpsManager::class.java,
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    String::class.java
                )
                Reflection.invoke<Int>(appOps, method, id, uid, pkg)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun preserveBeforeFreeze(context: Context, pkg: String): Map<String, Int> {
        if (!Prefs.preserveAppOps(context) || !isAvailable(context)) return emptyMap()
        val result = LinkedHashMap<String, Int>()
        for ((_, op) in opNames) {
            currentMode(context, op, pkg)?.let { result[op] = it }
        }
        if (result.isNotEmpty()) {
            val encoded = result.entries.joinToString(",") { "${it.key}=${it.value}" }
            Prefs.prefs(context).edit().putString(PREFS_PREFIX + pkg, encoded).apply()
        }
        return result
    }

    fun restorePreservedAfterUnfreeze(context: Context, pkg: String) {
        if (!Prefs.preserveAppOps(context) || !isAvailable(context)) return
        val encoded = Prefs.prefs(context).getString(PREFS_PREFIX + pkg, null) ?: return
        val modes = LinkedHashMap<String, Int>()
        for (entry in encoded.split(',')) {
            val separator = entry.lastIndexOf('=')
            if (separator <= 0) continue
            val op = entry.substring(0, separator)
            val mode = entry.substring(separator + 1).toIntOrNull() ?: continue
            modes[op] = mode
        }
        restoreAfterUnfreeze(context, pkg, modes)
        Prefs.prefs(context).edit().remove(PREFS_PREFIX + pkg).apply()
    }

    fun restoreAfterUnfreeze(context: Context, pkg: String, modes: Map<String, Int>) {
        if (modes.isEmpty() || !isAvailable(context)) return
        for ((op, mode) in modes) setMode(context, op, pkg, mode)
    }

    private fun opId(name: String): Int? = when (name) {
        "android:coarse_location" -> 0
        "android:fine_location" -> 1
        "android:read_sms" -> 14
        "android:read_phone_state" -> 51
        "android:read_external_storage" -> 59
        else -> null
    }
}
