package com.easmsy.provision

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.UserManager
import android.provider.Settings
import com.easmsy.MainActivity
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications
import com.easmsy.core.Prefs
import com.easmsy.core.Users
import com.easmsy.settings.IslandSettingsActivity

/**
 * Post-provisioning policy setup.  The stock ManagedProvisioning flow has already created
 * the profile and set this app as profile owner by the time these methods run.
 */
object Provisioning {

    private const val ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE"
    private const val ACTION_UNFREEZE = "com.oasisfeng.island.action.UNFREEZE"
    private const val ACTION_LAUNCH = "com.oasisfeng.island.action.LAUNCH"
    private const val ACTION_SUSPEND = "com.oasisfeng.island.action.SUSPEND"
    private const val ACTION_UNSUSPEND = "com.oasisfeng.island.action.UNSUSPEND"
    private const val ACTION_BIND_SERVICE = "com.oasisfeng.island.action.BIND_SERVICE"

    fun complete(context: Context) {
        val policies = DevicePolicies(context)
        if (!policies.isProfileOrDeviceOwnerOnCallingUser()) return

        policies.setAffiliationIds(setOf(context.packageName))
        policies.setSupportMessages(
            context.getText(R.string.device_admin_support_message_short),
            context.getText(R.string.device_admin_support_message_long)
        )

        if (!Users.isOwner()) {
            val profile = Users.current()
            policies.setProfileEnabled()
            policies.setProfileName(" ${Prefs.islandName(context, profile)} ")
            applyProfileOwnerPolicies(context, policies)
            enableProfileSettings(context)
        } else {
            // Mainland is managed as a device owner.  Nothing else needs to run at boot.
            try {
                policies.clearUserRestrictionIfNeeded(context, UserManager.DISALLOW_ADD_MANAGED_PROFILE)
            } catch (_: RuntimeException) {
                // The restriction may not exist on older ROMs.
            }
        }
        Prefs.setSetupComplete(context, true)
        Notifications.cancelWatcher(context)
    }

    private fun applyProfileOwnerPolicies(context: Context, policies: DevicePolicies) {
        // Allow apps in the profile to open links in the owner user.
        policies.setUserRestriction("allow_parent_profile_app_linking", true)
        policies.clearCrossProfileIntentFilters()

        // Island's public API and service shuttle need to be reachable from both sides.
        addApiFilter(policies, ACTION_FREEZE, "package", "packages")
        addApiFilter(policies, ACTION_UNFREEZE, "package", "packages")
        addApiFilter(policies, ACTION_LAUNCH, "package", "intent", "packages")
        addApiFilter(policies, ACTION_SUSPEND, "package", "packages")
        addApiFilter(policies, ACTION_UNSUSPEND, "package", "packages")

        val settingsFilter = IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        settingsFilter.addDataScheme("package")
        policies.addCrossProfileIntentFilter(settingsFilter, DevicePolicies.FLAG_MANAGED_CAN_ACCESS_PARENT)

        val shuttle = IntentFilter(ACTION_BIND_SERVICE)
        shuttle.addCategory(Intent.CATEGORY_DEFAULT)
        policies.addCrossProfileIntentFilter(shuttle, DevicePolicies.FLAG_MANAGED_CAN_ACCESS_PARENT)

        // Greenify uses the stock app-details screen for cross-profile hibernation.
        val greenify = IntentFilter("com.oasisfeng.greenify.action.GREENIFY")
        greenify.addDataScheme("package")
        policies.addCrossProfileIntentFilter(greenify, DevicePolicies.FLAG_MANAGED_CAN_ACCESS_PARENT)
    }

    private fun addApiFilter(policies: DevicePolicies, action: String, vararg schemes: String) {
        val filter = IntentFilter(action)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        for (scheme in schemes) filter.addDataScheme(scheme)
        policies.addCrossProfileIntentFilter(filter, DevicePolicies.FLAG_MANAGED_CAN_ACCESS_PARENT)
    }

    private fun enableProfileSettings(context: Context) {
        val pm = context.packageManager
        setComponent(pm, ComponentName(context, IslandSettingsActivity::class.java), true)
        setComponent(pm, ComponentName(context, MainActivity::class.java), false)
        // The profile is now fully set up; the one-time enabler must not keep waking up.
        setComponent(pm, ComponentName(context, com.easmsy.settings.IslandSettingsEnabler::class.java), false)
    }

    private fun setComponent(pm: PackageManager, component: ComponentName, enabled: Boolean) {
        try {
            pm.setComponentEnabledSetting(
                component,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: RuntimeException) {
            // Component state changes are best-effort across vendor ROMs.
        }
    }

    fun requestProfileRemoval(context: Context) {
        val policies = DevicePolicies(context)
        if (Users.isOwner()) {
            // Only a device owner or profile owner of the current user can do this.  When
            // the profile is owned by us and Mainland is not, the system settings flow is
            // the safe public route.
            if (!policies.isDeviceOwner()) {
                openManagedProfileSettings(context)
                return
            }
            policies.clearDeviceOwner(context.packageName)
        } else {
            if (!policies.clearProfileOwner()) {
                policies.wipeProfileData()
            }
        }
    }

    private fun openManagedProfileSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: RuntimeException) {
            // No settings activity available.
        }
    }
}
