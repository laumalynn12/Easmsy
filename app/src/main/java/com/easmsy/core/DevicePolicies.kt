package com.easmsy.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import com.easmsy.provision.AdminReceiver

/**
 * Thin, null-safe facade over DevicePolicyManager.
 *
 * The original shared module wrapped every DPM call in a generic reflection proxy.  This
 * version uses the public DPM API for all normal operations; reflection is retained only
 * for two values which the public SDK does not expose to a profile owner.
 */
class DevicePolicies(val context: Context) {

    val admin: ComponentName = ComponentName(context, AdminReceiver::class.java)

    val manager: DevicePolicyManager?
        get() = context.getSystemService(DevicePolicyManager::class.java)

    fun isProfileOwner(): Boolean = try {
        manager?.isProfileOwnerApp(context.packageName) == true
    } catch (_: RuntimeException) {
        false
    }

    fun isDeviceOwner(): Boolean = try {
        manager?.isDeviceOwnerApp(context.packageName) == true
    } catch (_: RuntimeException) {
        false
    }

    fun isProfileOrDeviceOwnerOnCallingUser(): Boolean = isProfileOwner() || isDeviceOwner()

    fun isAdminActive(): Boolean = try {
        manager?.isAdminActive(admin) == true
    } catch (_: RuntimeException) {
        false
    }

    fun setApplicationHidden(packageName: String, hidden: Boolean): Boolean = try {
        manager?.setApplicationHidden(admin, packageName, hidden) == true
    } catch (_: RuntimeException) {
        false
    }

    fun isApplicationHidden(packageName: String): Boolean = try {
        manager?.isApplicationHidden(admin, packageName) == true
    } catch (_: RuntimeException) {
        false
    }

    fun setPackagesSuspended(packages: Array<String>, suspended: Boolean): Array<String> = try {
        manager?.setPackagesSuspended(admin, packages, suspended) ?: emptyArray()
    } catch (_: RuntimeException) {
        packages
    }

    fun isPackageSuspended(packageName: String): Boolean = try {
        manager?.isPackageSuspended(admin, packageName) == true
    } catch (_: RuntimeException) {
        false
    }

    fun enableSystemApp(packageName: String): Boolean = try {
        manager?.enableSystemApp(admin, packageName)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun installExistingPackage(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager?.installExistingPackage(admin, packageName) == true
        } else false
    } catch (_: RuntimeException) {
        false
    }

    fun setUserRestriction(restriction: String, enabled: Boolean): Boolean = try {
        if (enabled) manager?.addUserRestriction(admin, restriction)
        else manager?.clearUserRestriction(admin, restriction)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun addUserRestrictionIfNeeded(context: Context, restriction: String): Boolean {
        val current = try {
            manager?.getUserRestrictions(admin)?.getBoolean(restriction) == true
        } catch (_: RuntimeException) {
            false
        }
        return current || setUserRestriction(restriction, true)
    }

    fun clearUserRestrictionIfNeeded(context: Context, restriction: String): Boolean {
        val current = try {
            manager?.getUserRestrictions(admin)?.getBoolean(restriction) == true
        } catch (_: RuntimeException) {
            false
        }
        return !current || setUserRestriction(restriction, false)
    }

    fun setSupportMessages(short: CharSequence?, long: CharSequence?): Boolean = try {
        manager?.setShortSupportMessage(admin, short)
        manager?.setLongSupportMessage(admin, long)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun isProvisioningAllowed(action: String): Boolean = try {
        manager?.isProvisioningAllowed(action) == true
    } catch (_: RuntimeException) {
        false
    }

    fun addCrossProfileIntentFilter(filter: IntentFilter, flags: Int): Boolean = try {
        manager?.addCrossProfileIntentFilter(admin, filter, flags)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun clearCrossProfileIntentFilters(): Boolean = try {
        manager?.clearCrossProfileIntentFilters(admin)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun setProfileEnabled(): Boolean = try {
        manager?.setProfileEnabled(admin)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun setAffiliationIds(ids: Set<String>): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.setAffiliationIds(admin, ids)
            true
        } else false
    } catch (_: RuntimeException) {
        false
    }

    fun setProfileName(name: String): Boolean = try {
        manager?.setProfileName(admin, name)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun clearProfileOwner(): Boolean = try {
        manager?.clearProfileOwner(admin)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun clearDeviceOwner(packageName: String): Boolean = try {
        val m = DevicePolicyManager::class.java.getMethod("clearDeviceOwnerApp", String::class.java)
        m.invoke(manager, packageName)
        true
    } catch (_: Exception) {
        false
    }

    fun wipeProfileData(): Boolean = try {
        manager?.wipeData(0)
        true
    } catch (_: RuntimeException) {
        false
    }

    fun isProfileOwnerOn(user: UserHandle): Boolean =
        Users.profileOwnerAsUser(context, user)?.packageName == context.packageName

    fun profileOwnerComponent(user: UserHandle): ComponentName? = Users.profileOwnerAsUser(context, user)

    fun setCrossProfilePackages(packages: Set<String>): Boolean = try {
        val method = DevicePolicyManager::class.java.getMethod(
            "setCrossProfilePackages", ComponentName::class.java, Set::class.java
        )
        method.invoke(manager, admin, packages)
        true
    } catch (_: Exception) {
        false
    }

    @Suppress("UNCHECKED_CAST")
    fun getCrossProfilePackages(): Set<String> = try {
        val method = DevicePolicyManager::class.java.getMethod("getCrossProfilePackages")
        (method.invoke(manager) as? Set<String>) ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    companion object {
        const val FLAG_MANAGED_CAN_ACCESS_PARENT = 0x00000002
        const val FLAG_PARENT_CAN_ACCESS_MANAGED = 0x00000001

        val DISALLOW_DEBUGGING_FEATURES: String = UserManager.DISALLOW_DEBUGGING_FEATURES
    }
}
