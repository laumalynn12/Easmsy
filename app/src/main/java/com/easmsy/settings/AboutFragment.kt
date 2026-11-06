package com.easmsy.settings

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.easmsy.R

/** About screen.  The version line is filled from the package manager at runtime. */
@Suppress("DEPRECATION")
class AboutFragment : BasePreferenceFragment(R.xml.pref_about) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = activity ?: return
        try {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            val summary = buildString {
                append(info.versionName ?: "1.0")
                append(" (")
                append(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
                    else info.versionCode.toLong()
                )
                append(')')
            }
            setSummary(R.string.key_version, summary)
        } catch (_: PackageManager.NameNotFoundException) {
            // The package always exists, but a vendor ROM may return an odd error.
        }
    }
}
