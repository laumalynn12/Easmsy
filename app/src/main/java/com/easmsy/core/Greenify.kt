package com.easmsy.core

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.os.UserManager

/** Greenify's documented cross-app action, retained for feature compatibility. */
object Greenify {

    const val PACKAGE = "com.oasisfeng.greenify"
    private const val ACTION_GREENIFY = "com.oasisfeng.greenify.action.GREENIFY"
    private const val EXTRA_CALLER_ID = "caller"
    private const val MIN_VERSION = 306

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun isSupported(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0).versionCode >= MIN_VERSION
    } catch (_: Exception) {
        false
    }

    fun greenify(context: Context, packageName: String, user: UserHandle): Boolean {
        val serial = Users.serial(context, user)
        if (serial < 0) return false
        val intent = Intent(ACTION_GREENIFY)
            .setPackage(PACKAGE)
            .setData(Uri.parse("package:$packageName#usn=$serial"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val caller = PendingIntent.getBroadcast(
            context,
            0,
            Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        intent.putExtra(EXTRA_CALLER_ID, caller)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    fun openInMarket(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: RuntimeException) {
                // No store activity available.
            }
        }
    }
}
