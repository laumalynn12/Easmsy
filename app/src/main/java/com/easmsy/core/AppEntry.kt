package com.easmsy.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.UserHandle
import com.easmsy.R

/**
 * Immutable view of one launcher/application entry.  Drawables are intentionally not
 * retained here; the adapter loads and recycles them, which keeps idle memory low.
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val info: ApplicationInfo,
    val user: UserHandle,
    val system: Boolean,
    val installed: Boolean,
    val enabled: Boolean,
    val hidden: Boolean,
    val suspended: Boolean,
    val launchable: Boolean,
    val managed: Boolean
) {

    val alive: Boolean get() = installed && enabled && !hidden

    enum class State { PENDING, ALIVE, FROZEN, DISABLED, UNINSTALLED }

    val state: State
        get() = when {
            !installed -> State.UNINSTALLED
            !enabled -> State.DISABLED
            hidden -> State.FROZEN
            else -> State.ALIVE
        }

    fun stateText(context: Context): String {
        val base = when (state) {
            State.ALIVE -> context.getString(R.string.status_alive)
            State.FROZEN -> context.getString(R.string.status_frozen)
            State.DISABLED -> context.getString(R.string.status_disabled)
            State.UNINSTALLED -> context.getString(R.string.status_uninstalled)
            State.PENDING -> context.getString(R.string.status_pending)
        }
        return base
    }

    fun summary(context: Context): String {
        val pieces = mutableListOf(stateText(context))
        if (system) pieces += context.getString(R.string.status_appendix_system)
        if (suspended) pieces += "suspended"
        return pieces.joinToString(", ")
    }
}
