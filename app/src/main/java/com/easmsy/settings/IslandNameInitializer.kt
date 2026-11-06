package com.easmsy.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Prefs
import com.easmsy.core.Users

/** Applies the default, human-readable name to a newly created Island profile. */
class IslandNameInitializer : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_INITIALIZE) return
        if (Users.isOwner()) return
        val policies = DevicePolicies(context)
        if (!policies.isProfileOwner()) return
        val name = Prefs.defaultIslandName(context)
        Prefs.setName(context, Users.current(), name)
        policies.setProfileName(" $name ")
    }
}
