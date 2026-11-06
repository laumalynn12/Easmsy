package com.easmsy.installer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import com.easmsy.core.Users

/** Forwards an app-details request to the correct user, or applies a user extra. */
class AppInfoForwarderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = packageFrom(intent)
        val user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_USER)
        }
        if (pkg == null) {
            finish()
            return
        }
        if (user != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val launcherApps = getSystemService(android.content.pm.LauncherApps::class.java)
            if (launcherApps != null) {
                try {
                    launcherApps.startAppDetailsActivity(
                        android.content.ComponentName(pkg, ""),
                        user,
                        null,
                        null
                    )
                    finish()
                    return
                } catch (_: RuntimeException) {
                    // Fall through to the settings intent.
                }
            }
        }
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (user != null) details.putExtra(Intent.EXTRA_USER, user)
        try {
            startActivity(details)
        } catch (_: RuntimeException) {
            // No settings activity for this package.
        }
        finish()
    }
    private fun packageFrom(intent: Intent): String? {
        val extra = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (!extra.isNullOrEmpty()) return extra
        val data = intent.data ?: return null
        return if (data.scheme == "package") data.schemeSpecificPart else null
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME"
    }
}
