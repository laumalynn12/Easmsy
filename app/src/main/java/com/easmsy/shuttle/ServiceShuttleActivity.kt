package com.easmsy.shuttle

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException

/**
 * Proxy activity which establishes a service binding in the profile where it runs and
 * relays the connection back to the caller through [IServiceConnection].  It is the same
 * design as the original Island shuttle, reduced to the one operation it needs.
 */
class ServiceShuttleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent) {
        val extras = intent.extras ?: return
        val remote = IServiceConnection.Stub.asInterface(extras.getBinder(EXTRA_CONNECTION)) ?: return
        if (intent.action != ACTION_BIND_SERVICE) return
        val service = intent.getParcelableExtra<Intent>(EXTRA_INTENT) ?: return
        val flags = intent.getIntExtra(EXTRA_FLAGS, 0)
        val connection = DelegateConnection(applicationContext, remote)
        try {
            if (!applicationContext.bindService(service, connection, flags)) {
                remote.onServiceFailed()
            }
        } catch (_: SecurityException) {
            try {
                remote.onServiceFailed()
            } catch (_: RemoteException) {
                // The caller is already gone.
            }
        } catch (_: RuntimeException) {
            try {
                remote.onServiceFailed()
            } catch (_: RemoteException) {
                // The caller is already gone.
            }
        }
    }

    private class DelegateConnection(
        private val context: Context,
        private val remote: IServiceConnection
    ) : ServiceConnection, IBinder.DeathRecipient {

        private var bound = true

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null || name == null) {
                onServiceFailed()
                return
            }
            try {
                remote.asBinder().linkToDeath(this, 0)
            } catch (_: RemoteException) {
                unbind()
                return
            }
            val unbinder = object : IUnbinder.Stub() {
                override fun unbind(): Boolean = this@DelegateConnection.unbind()
            }
            try {
                remote.onServiceConnected(name, service, unbinder)
            } catch (_: RemoteException) {
                unbind()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            try {
                remote.asBinder().unlinkToDeath(this, 0)
                remote.onServiceDisconnected(name)
            } catch (_: RemoteException) {
                // The caller is already gone.
            }
        }

        fun onServiceFailed() {
            try {
                remote.onServiceFailed()
            } catch (_: RemoteException) {
                // The caller is already gone.
            }
        }

        override fun binderDied() {
            unbind()
        }

        fun unbind(): Boolean {
            if (!bound) return false
            bound = false
            return try {
                context.unbindService(this)
                true
            } catch (_: RuntimeException) {
                false
            }
        }
    }

    companion object {
        const val ACTION_BIND_SERVICE = "com.easmsy.action.BIND_SERVICE"
        const val EXTRA_INTENT = "extra"
        const val EXTRA_CONNECTION = "svc_conn"
        const val EXTRA_FLAGS = "flags"
    }
}
