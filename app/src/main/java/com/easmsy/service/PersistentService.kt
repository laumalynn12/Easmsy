package com.easmsy.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Compatibility endpoint for clients which bind to Island's persistent service.
 * The rewrite intentionally has no long-running logic here; features are event-driven.
 */
class PersistentService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
