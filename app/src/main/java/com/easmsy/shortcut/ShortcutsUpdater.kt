package com.easmsy.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.easmsy.core.Shortcuts

/** Refreshes pinned shortcut metadata after updates and boots. */
class ShortcutsUpdater : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_MY_PACKAGE_REPLACED && action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            Shortcuts.updateAllPinned(context)
        } catch (_: RuntimeException) {
            // User may be locked; the next boot/update retries.
        }
    }
}
