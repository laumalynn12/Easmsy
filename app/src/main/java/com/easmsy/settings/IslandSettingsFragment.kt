package com.easmsy.settings

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.SwitchPreference
import android.provider.Settings
import android.widget.Toast
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications
import com.easmsy.core.Ops
import com.easmsy.core.Prefs
import com.easmsy.core.Users
import com.easmsy.provision.Provisioning

/**
 * Scoped settings for one profile.  This is a plain framework PreferenceFragment: no
 * support-library fragments, view models or data binding are involved.
 */
@Suppress("DEPRECATION")
class IslandSettingsFragment : BasePreferenceFragment(R.xml.pref_island) {

    private val INTERACT_ACROSS_PROFILES = "android.permission.INTERACT_ACROSS_PROFILES"
    private val READ_PHONE_STATE = android.Manifest.permission.READ_PHONE_STATE
    private val READ_SMS = android.Manifest.permission.READ_SMS
    private val ACCESS_COARSE_LOCATION = android.Manifest.permission.ACCESS_COARSE_LOCATION
    private val READ_EXTERNAL_STORAGE = android.Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            preferenceManager.setStorageDeviceProtected()
        }
        activity ?: return
        setupDeviceOwner()
        setupPrivacy()
        setupCrossProfile()
        setupWatcher()
        setupMaintenance()
    }

    override fun onResume() {
        super.onResume()
        val activity = activity ?: return
        activity.title = Prefs.islandName(activity)
        syncWatcherSwitches()
    }

    private fun setupDeviceOwner() {
        val activity = activity ?: return
        val preference = pref<Preference>(R.string.key_device_owner_setup) ?: return
        val policies = DevicePolicies(activity)
        if (Users.isOwner() && !policies.isProfileOrDeviceOwnerOnCallingUser()) {
            preference.summary = getString(R.string.pref_device_owner_summary) +
                getString(R.string.pref_device_owner_features)
            preference.setOnPreferenceClickListener {
                openUrl("https://github.com/oasisfeng/island/wiki/Setup")
                true
            }
        } else {
            remove(R.string.key_device_owner_setup)
        }
    }

    private fun setupPrivacy() {
        if (Build.VERSION.SDK_INT !in Build.VERSION_CODES.P..Build.VERSION_CODES.Q) {
            removeAppOpsPreferences()
            return
        }
        setupOpsPreference(
            R.string.key_manage_read_phone_state,
            READ_PHONE_STATE,
            "read_phone_state",
            R.string.pref_privacy_read_phone_state_title
        )
        setupOpsPreference(
            R.string.key_manage_read_sms,
            READ_SMS,
            "read_sms",
            R.string.pref_privacy_read_sms_title
        )
        setupOpsPreference(
            R.string.key_manage_location,
            ACCESS_COARSE_LOCATION,
            "coarse_location",
            R.string.pref_privacy_location_title
        )
        if (isDevelopmentSettingsEnabled()) {
            setupOpsPreference(
                R.string.key_manage_storage,
                READ_EXTERNAL_STORAGE,
                "read_external_storage",
                R.string.pref_privacy_storage_title
            )
        } else {
            remove(R.string.key_manage_storage)
        }
    }

    private fun setupOpsPreference(key: Int, permission: String, opKey: String, title: Int) {
        val preference = pref<Preference>(key) ?: return
        preference.setOnPreferenceClickListener {
            showOpsDialog(permission, opKey, title)
            true
        }
    }

    private fun showOpsDialog(permission: String, opKey: String, title: Int) {
        val activity = activity ?: return
        if (!Ops.isAvailable(activity)) {
            showAdbCommand(activity.getString(title))
            return
        }
        val apps = Ops.appsWithPermission(activity, permission)
        if (apps.isEmpty()) {
            Toast.makeText(activity, R.string.prompt_appops_no_such_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.second }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(labels) { _, which ->
                val pkg = apps[which].first
                val ok = Ops.setMode(activity, "android:$opKey", pkg, android.app.AppOpsManager.MODE_IGNORED)
                Toast.makeText(
                    activity,
                    if (ok) R.string.action_done else R.string.prompt_operation_failure_due_to_incompatibility,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton(R.string.action_revoke_all) { _, _ -> revokeAll(permission, opKey) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun revokeAll(permission: String, opKey: String) {
        val activity = activity ?: return
        AlertDialog.Builder(activity)
            .setMessage(R.string.prompt_appops_revoke_for_all_users_apps)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_revoke_all) { _, _ ->
                val count = Ops.revokeForApps(activity, permission, opKey)
                Toast.makeText(
                    activity,
                    if (count >= 0) R.string.action_done else R.string.prompt_operation_failure_due_to_incompatibility,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun showAdbCommand(title: String) {
        val activity = activity ?: return
        val command = "pm grant ${activity.packageName} android.permission.GET_APP_OPS_STATS"
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(getString(R.string.prompt_adb_app_ops_command) + "\n\n" + command)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun removeAppOpsPreferences() {
        remove(R.string.key_privacy_appops)
        remove(R.string.key_manage_read_phone_state)
        remove(R.string.key_manage_read_sms)
        remove(R.string.key_manage_location)
        remove(R.string.key_manage_storage)
    }

    private fun setupCrossProfile() {
        val activity = activity ?: return
        val preference = pref<Preference>(R.string.key_cross_profile) ?: return
        val policies = DevicePolicies(activity)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            !policies.isProfileOrDeviceOwnerOnCallingUser()
        ) {
            remove(R.string.key_cross_profile)
            return
        }
        preference.setOnPreferenceClickListener {
            showCrossProfileDialog(policies)
            true
        }
    }

    private fun showCrossProfileDialog(policies: DevicePolicies) {
        val activity = activity ?: return
        val pm = activity.packageManager
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .filter { it.requestedPermissions?.contains(INTERACT_ACROSS_PROFILES) == true }
        } catch (_: RuntimeException) {
            emptyList()
        }
        if (packages.isEmpty()) {
            Toast.makeText(activity, R.string.prompt_manage_cross_profile_apps_footer, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = packages.map { it.applicationInfo.loadLabel(pm).toString() }.toTypedArray()
        val allowedPackages = policies.getCrossProfilePackages()
        val checked = BooleanArray(packages.size) { packages[it].packageName in allowedPackages }
        AlertDialog.Builder(activity)
            .setTitle(R.string.prompt_manage_cross_profile_apps)
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selected = packages.mapIndexedNotNull { index, info ->
                    if (checked[index]) info.packageName else null
                }.toSet()
                val ok = policies.setCrossProfilePackages(selected)
                Toast.makeText(
                    activity,
                    if (ok) R.string.action_done else R.string.prompt_operation_failure_due_to_incompatibility,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupWatcher() {
        val islandPreference = pref<SwitchPreference>(R.string.key_island_watcher)
        val appPreference = pref<SwitchPreference>(R.string.key_app_watcher)
        val activity = activity ?: return

        if (islandPreference != null) {
            if (!Users.hasProfile(activity) && Users.isOwner()) {
                remove(R.string.key_island_watcher)
            } else {
                islandPreference.setOnPreferenceChangeListener { _, value ->
                    val enabled = value == true
                    Prefs.prefs(activity).edit().putBoolean(Prefs.KEY_ISLAND_WATCHER, enabled).apply()
                    if (enabled) Notifications.showWatcher(activity) else Notifications.cancelWatcher(activity)
                    true
                }
            }
        }
        if (appPreference != null) {
            appPreference.setOnPreferenceChangeListener { _, value ->
                Prefs.prefs(activity).edit()
                    .putBoolean(Prefs.KEY_APP_WATCHER, value == true)
                    .apply()
                true
            }
        }
    }

    private fun syncWatcherSwitches() {
        val activity = activity ?: return
        pref<SwitchPreference>(R.string.key_island_watcher)?.isChecked = Prefs.islandWatcher(activity)
        pref<SwitchPreference>(R.string.key_app_watcher)?.isChecked = Prefs.appWatcher(activity)
    }

    private fun setupMaintenance() {
        val activity = activity ?: return
        val policies = DevicePolicies(activity)
        val owner = Users.isOwner()

        val reprovision = pref<Preference>(R.string.key_reprovision)
        if (reprovision != null) {
            if (!policies.isProfileOrDeviceOwnerOnCallingUser() && !owner) {
                remove(R.string.key_reprovision)
            } else {
                reprovision.setOnPreferenceClickListener {
                    Thread {
                        Provisioning.complete(activity.applicationContext)
                        activity.runOnUiThread {
                            Toast.makeText(activity, R.string.toast_reprovision_done, Toast.LENGTH_SHORT).show()
                        }
                    }.apply { isDaemon = true }.start()
                    true
                }
            }
        }

        val destroy = pref<Preference>(R.string.key_destroy)
        if (destroy != null) {
            if (owner) {
                if (!policies.isProfileOrDeviceOwnerOnCallingUser()) {
                    remove(R.string.key_destroy)
                } else {
                    destroy.title = getString(R.string.pref_rescind_title)
                    destroy.summary = getString(R.string.pref_rescind_summary) +
                        getString(R.string.pref_device_owner_features) + "\n"
                    destroy.setOnPreferenceClickListener {
                        confirmDeactivation()
                        true
                    }
                }
            } else {
                destroy.setOnPreferenceClickListener {
                    confirmDeactivation()
                    true
                }
            }
        }
    }

    private fun confirmDeactivation() {
        val activity = activity ?: return
        val owner = Users.isOwner()
        if (owner) {
            AlertDialog.Builder(activity)
                .setMessage(R.string.dialog_rescind_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_rescind) { _, _ ->
                    Provisioning.requestProfileRemoval(activity)
                }
                .show()
            return
        }
        val message = if (DevicePolicies(activity).isDeviceOwner()) {
            R.string.dialog_destroy_message_for_managed_user
        } else {
            R.string.dialog_destroy_message
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.action_destroy)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_destroy) { _, _ ->
                Provisioning.requestProfileRemoval(activity)
                activity.finish()
            }
            .show()
    }

    private fun isDevelopmentSettingsEnabled(): Boolean = try {
        Settings.Global.getInt(
            activity?.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    } catch (_: RuntimeException) {
        false
    }

    private fun openUrl(url: String) {
        val activity = activity ?: return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: RuntimeException) {
            Toast.makeText(activity, R.string.prompt_operation_failure_due_to_incompatibility, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        if (!Users.isOwner()) {
            inflater.inflate(R.menu.pref_island_actions, menu)
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.menu_rename) {
            (activity as? IslandSettingsActivity)?.requestRename()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("unused")
    private fun enableSettingsActivity() {
        val activity = activity ?: return
        activity.packageManager.setComponentEnabledSetting(
            ComponentName(activity, IslandSettingsActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
