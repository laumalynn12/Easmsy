package com.easmsy.settings

import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.core.app.NavUtils
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Users

/**
 * Settings entry.  Uses the framework preference activity so the standard header/list
 * (and therefore the original visual hierarchy) is preserved without extra dependencies.
 */
@Suppress("DEPRECATION")
class SettingsActivity : android.preference.PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onIsMultiPane(): Boolean {
        val size = resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return size >= Configuration.SCREENLAYOUT_SIZE_XLARGE
    }

    override fun onBuildHeaders(target: MutableList<Header>) {
        loadHeadersFromResource(R.xml.pref_headers, target)
    }

    override fun isValidFragment(fragmentName: String): Boolean =
        fragmentName.startsWith(packageName)

    override fun onHeaderClick(header: Header, position: Int) {
        if (header.fragment != IslandSettingsFragment::class.java.name) {
            return super.onHeaderClick(header, position)
        }
        if (Users.isOwner() && Users.hasProfile(this)) {
            if (launchProfileSettings()) return
        }
        super.onHeaderClick(header, position)
    }

    private fun launchProfileSettings(): Boolean {
        val profile = Users.profile(this) ?: return false
        if (!DevicePolicies(this).isDeviceOwner() && !Users.isProfileManagedByIsland(this, profile)) return false
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return false
        val activity = ComponentName(this, IslandSettingsActivity::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!launcherApps.isActivityEnabled(activity, profile)) return false
            }
            launcherApps.startMainActivity(activity, profile, null, null)
            true
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.prompt_island_not_yet_setup, Toast.LENGTH_LONG).show()
            false
        }
    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (!super.onMenuItemSelected(featureId, item)) NavUtils.navigateUpFromSameTask(this)
            return true
        }
        return super.onMenuItemSelected(featureId, item)
    }
}
