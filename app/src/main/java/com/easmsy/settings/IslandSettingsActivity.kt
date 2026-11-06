package com.easmsy.settings

import android.app.Activity
import android.app.Fragment
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Prefs
import com.easmsy.core.Users

/** Per-profile settings, also the launcher activity shown inside the managed profile. */
@Suppress("DEPRECATION")
class IslandSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Users.isOwner()) {
            title = Prefs.islandName(this)
        } else {
            title = getString(R.string.pref_scoped_settings_header)
        }
        fragmentManager.beginTransaction()
            .replace(android.R.id.content, IslandSettingsFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun rename(name: String) {
        Prefs.setName(this, Users.current(), name)
        DevicePolicies(this).setProfileName(" $name ")
        title = name
        Toast.makeText(this, R.string.action_rename, Toast.LENGTH_SHORT).show()
    }

    fun requestRename() {
        val edit = EditText(this).apply {
            setText(Prefs.islandName(this@IslandSettingsActivity))
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_rename)
            .setView(edit)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = edit.text.toString().trim()
                if (value.isEmpty() || value.any { it.code < 32 }) {
                    Toast.makeText(this, R.string.prompt_invalid_input, Toast.LENGTH_SHORT).show()
                } else rename(value)
            }
            .show()
    }
}
