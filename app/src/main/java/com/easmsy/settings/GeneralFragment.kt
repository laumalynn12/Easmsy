package com.easmsy.settings

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.SwitchPreference
import android.widget.Toast
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Ops
import com.easmsy.core.Prefs
import com.easmsy.core.Shortcuts

/** General settings (mainland-wide options). */
@Suppress("DEPRECATION")
class GeneralFragment : BasePreferenceFragment(R.xml.pref_general) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupShortcutPrefix()
        setupDynamicLabel()
        setupAdminMessage()
        setupPreserveAppOps()
    }

    private fun setupShortcutPrefix() {
        val preference = pref<EditTextPreference>(R.string.key_launch_shortcut_prefix) ?: return
        preference.summary = Prefs.launchShortcutPrefix(context)
        preference.setOnPreferenceChangeListener { _, value ->
            val text = value?.toString().orEmpty()
            if (text.isEmpty()) false else {
                preference.summary = text
                true
            }
        }
    }

    private fun setupDynamicLabel() {
        val preference = pref<SwitchPreference>(R.string.setting_dynamic_shortcut_label) ?: return
        preference.isChecked = Prefs.dynamicShortcutLabel(context)
        preference.setOnPreferenceChangeListener { _, value ->
            val enabled = value == true
            Prefs.prefs(context).edit().putBoolean(Prefs.KEY_DYNAMIC_SHORTCUT_LABEL, enabled).apply()
            Toast.makeText(context, R.string.prompt_updating_shortcuts, Toast.LENGTH_SHORT).show()
            Shortcuts.updateAllPinned(context)
            true
        }
    }

    private fun setupAdminMessage() {
        val preference = pref<SwitchPreference>(R.string.key_show_admin_message) ?: return
        val policies = DevicePolicies(context)
        if (!policies.isProfileOrDeviceOwnerOnCallingUser()) {
            remove(R.string.key_show_admin_message)
            return
        }
        preference.isChecked = Prefs.showAdminMessage(context)
        preference.setOnPreferenceChangeListener { _, value ->
            val enabled = value == true
            Prefs.prefs(context).edit().putBoolean(Prefs.KEY_SHOW_ADMIN_MESSAGE, enabled).apply()
            policies.setSupportMessages(
                if (enabled) context.getText(R.string.device_admin_support_message_short) else null,
                if (enabled) context.getText(R.string.device_admin_support_message_long) else null
            )
            true
        }
    }

    private fun setupPreserveAppOps() {
        val preference = pref<SwitchPreference>(R.string.key_preserve_app_ops) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            remove(R.string.key_preserve_app_ops)
            return
        }
        preference.isChecked = Prefs.preserveAppOps(context)
        preference.setOnPreferenceChangeListener { _, value ->
            val enabled = value == true
            if (enabled && !Ops.isAvailable(context)) {
                showAdbCommand()
                return@setOnPreferenceChangeListener false
            }
            Prefs.prefs(context).edit().putBoolean(Prefs.KEY_PRESERVE_APP_OPS, enabled).apply()
            true
        }
    }

    private fun showAdbCommand() {
        val command = "pm grant ${context.packageName} android.permission.GET_APP_OPS_STATS"
        AlertDialog.Builder(context)
            .setTitle(R.string.pref_preserve_app_ops)
            .setMessage(getString(R.string.prompt_adb_app_ops_command) + "\n\n" + command)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.action_copy) { _, _ ->
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("command", command))
            }
            .show()
    }
}
