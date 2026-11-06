package com.easmsy

import android.app.Application
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications
import com.easmsy.core.Users

/**
 * Minimal application object.
 *
 * The original Island app kept important state in a heavyweight application class and
 * started analytics/crash-reporting infrastructure during startup.  This rewrite keeps the
 * application class deliberately small: only notification channels (which are required for
 * the watcher features) are created here.  Everything else is loaded on demand.
 */
class EasmsyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.ensureChannels(this)

        // Do not start services or enumerate packages at boot.  Both operations are done
        // lazily by the UI, or by the small receivers which actually need them.
        if (Users.isOwner() && DevicePolicies(this).isProfileOrDeviceOwnerOnCallingUser()) {
            // Support text is intentionally not set here; it is applied after provisioning.
        }
    }

    companion object {
        @Volatile
        lateinit var instance: EasmsyApplication
            private set
    }
}
