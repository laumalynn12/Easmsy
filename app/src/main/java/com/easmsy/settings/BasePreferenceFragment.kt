package com.easmsy.settings

import android.os.Bundle
import androidx.annotation.XmlRes
import com.easmsy.R

@Suppress("DEPRECATION")
abstract class BasePreferenceFragment(@XmlRes private val xml: Int) : android.preference.PreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(xml)
    }

    protected inline fun <reified T : android.preference.Preference> pref(key: Int): T? =
        findPreference(getString(key)) as? T

    protected fun remove(key: Int) {
        val preference = findPreference(getString(key)) ?: return
        preferenceScreen.removePreference(preference)
    }

    protected fun setSummary(key: Int, value: CharSequence) {
        findPreference(getString(key))?.summary = value
    }
}
