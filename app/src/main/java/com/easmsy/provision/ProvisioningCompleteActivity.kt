package com.easmsy.provision

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import com.easmsy.MainActivity
import com.easmsy.core.Users

/**
 * Completion activity started in the managed profile by the system after provisioning.
 * It finishes post-provisioning and then brings Mainland's main UI forward.
 */
class ProvisioningCompleteActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Provisioning.complete(applicationContext)
        launchMainland()
        finish()
    }

    private fun launchMainland() {
        try {
            val launcherApps = getSystemService(LauncherApps::class.java) ?: return
            val component = ComponentName(packageName, MainActivity::class.java.name)
            val enabled = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                launcherApps.isActivityEnabled(component, Users.owner())
            } else true
            if (enabled) {
                launcherApps.startMainActivity(component, Users.owner(), null, null)
            } else {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (_: RuntimeException) {
            // The owner activity will be opened by the user.
        }
    }
}
