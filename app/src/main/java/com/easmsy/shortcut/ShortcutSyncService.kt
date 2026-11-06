package com.easmsy.shortcut

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import com.easmsy.core.Shortcuts

/**
 * Keeps pinned shortcut labels and icons current when packages change.  It is started only
 * by explicit client binding (or by the system on update) and has no polling loop.
 */
class ShortcutSyncService : Service() {

    private var receiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                try {
                    Shortcuts.updateAllPinned(context)
                } catch (_: RuntimeException) {
                    // The launcher may be unavailable while the user is locked.
                }
            }
        }
        registerReceiver(receiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onDestroy() {
        receiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: RuntimeException) {
                // Already unregistered.
            }
        }
        receiver = null
        super.onDestroy()
    }
}
