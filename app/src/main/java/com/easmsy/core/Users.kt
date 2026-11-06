package com.easmsy.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * Small, dependency-free replacement for the original Users helper.
 *
 * Android exposes very little public API for cross-user inspection.  The calls below use
 * public APIs where possible and fall back to reflection for the two hidden-but-stable
 * methods needed by a profile owner (getProfileOwnerAsUser and createPackageContextAsUser).
 */
object Users {

    /** UserHandle.hashCode() is specified by AOSP to be the user id. */
    fun id(user: UserHandle): Int = user.hashCode()

    fun myUserId(): Int = id(Process.myUserHandle())

    fun owner(): UserHandle = UserHandle.getUserHandleForUid(0)

    fun current(): UserHandle = Process.myUserHandle()

    fun isOwner(user: UserHandle = current()): Boolean = id(user) == 0

    fun isCurrent(user: UserHandle): Boolean = id(user) == myUserId()

    fun all(context: Context): List<UserHandle> {
        val result = LinkedHashMap<Int, UserHandle>()
        result[0] = owner()
        result[myUserId()] = current()
        try {
            context.getSystemService(UserManager::class.java)?.userProfiles?.forEach {
                result[id(it)] = it
            }
        } catch (_: RuntimeException) {
            // A profile owner cannot always enumerate users; the two values above remain valid.
        }
        return result.values.sortedBy(::id)
    }

    /** All profiles except the owner / mainland user. */
    fun profiles(context: Context): List<UserHandle> =
        all(context).filter { !isOwner(it) }

    /**
     * The first managed / secondary profile.  Devices can have more than one profile, but
     * the Island UI (like the original) treats the first one as the Island.
     */
    fun profile(context: Context): UserHandle? = profiles(context).firstOrNull()

    fun hasProfile(context: Context): Boolean = profile(context) != null

    @Suppress("UNCHECKED_CAST")
    fun profileOwnerAsUser(context: Context, user: UserHandle): ComponentName? {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager? ?: return null
            DevicePolicyManager::class.java
                .getMethod("getProfileOwnerAsUser", UserHandle::class.java)
                .invoke(dpm, user) as? ComponentName
        } catch (_: Exception) {
            null
        }
    }

    fun isProfileManagedByIsland(context: Context, user: UserHandle = current()): Boolean {
        if (isOwner(user)) {
            // In the owner user a managed profile is owned by the same package.
            val profile = profile(context) ?: return false
            return profileOwnerAsUser(context, profile)?.packageName == context.packageName
        }
        if (isCurrent(user)) {
            return try {
                val dpm = context.getSystemService(DevicePolicyManager::class.java)
                dpm.isProfileOwnerApp(context.packageName)
            } catch (_: RuntimeException) {
                false
            }
        }
        return profileOwnerAsUser(context, user)?.packageName == context.packageName
    }

    /**
     * Ordinary Context is per-user.  This helper creates a context for another profile,
     * which is how the profile's package manager and file paths are reached.
     */
    fun contextAsUser(context: Context, user: UserHandle): Context? {
        if (isCurrent(user)) return context
        return try {
            Context::class.java
                .getMethod(
                    "createPackageContextAsUser",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    UserHandle::class.java
                )
                .invoke(context, context.packageName, 0, user) as? Context
        } catch (_: Exception) {
            null
        }
    }

    fun serial(context: Context, user: UserHandle): Long = try {
        context.getSystemService(UserManager::class.java)
            ?.getSerialNumberForUser(user) ?: -1L
    } catch (_: RuntimeException) {
        -1L
    }
}
