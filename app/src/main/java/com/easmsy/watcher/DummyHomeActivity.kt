package com.easmsy.watcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.easmsy.core.Users

/**
 * Temporary home activity used by the deactivation flow on ROMs which require a home
 * handler to be present before quiet mode can be requested.  It immediately forwards
 * to the real launcher and is disabled again on the next entry.
 */
class DummyHomeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Users.isOwner()) {
            try {
                packageManager.setComponentEnabledSetting(
                    ComponentName(this, DummyHomeActivity::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: RuntimeException) {
                // Best effort.
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        } catch (_: RuntimeException) {
            // No home activity at all; just finish.
        }
        finish()
    }
}
