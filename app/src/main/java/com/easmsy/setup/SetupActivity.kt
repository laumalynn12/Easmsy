package com.easmsy.setup

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.easmsy.MainActivity
import com.easmsy.R
import com.easmsy.core.Users
import com.easmsy.provision.AdminReceiver
import com.easmsy.provision.Provisioning

/**
 * Reconstructed setup wizard.  The visual layout matches the original (header, intro,
 * error area and bottom navigation); the setup-wizard support library is not needed.
 */
class SetupActivity : Activity() {

    private lateinit var next: Button
    private lateinit var back: Button
    private lateinit var extra: Button
    private lateinit var intro: TextView
    private lateinit var errorContainer: View
    private lateinit var errorText: TextView
    private lateinit var manualHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setup_wizard)

        next = findViewById(R.id.next_button)
        back = findViewById(R.id.back_button)
        extra = findViewById(R.id.extra_button)
        intro = findViewById(R.id.intro)
        errorContainer = findViewById(R.id.error_container)
        errorText = findViewById(R.id.error_text)
        manualHint = findViewById(R.id.manual_setup_hint)

        next.text = getText(R.string.setup_accept)
        next.setOnClickListener { beginProvisioning() }
        back.setOnClickListener { finish() }
        findViewById<View>(R.id.setup_scroll).scrollTo(0, 0)

        val existing = Users.profile(this)
        if (existing != null && Users.isProfileManagedByIsland(this, existing)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        if (existing != null) {
            val owner = Users.profileOwnerAsUser(this, existing)
            if (owner != null) {
                val label = try {
                    packageManager.getApplicationInfo(owner.packageName, 0).loadLabel(packageManager).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    owner.packageName
                }
                showError(getString(R.string.setup_error_managed_device, label))
                return
            }
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS).not()) {
            showError(getString(R.string.setup_error_managed_profile_not_supported))
        }
    }

    private fun beginProvisioning() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (dpm == null) {
            showError(getString(R.string.setup_error_missing_managed_provisioning))
            return
        }
        if (!dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            if (dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)) {
                Toast.makeText(this, R.string.setup_error_managed_device, Toast.LENGTH_LONG).show()
            } else {
                showError(getString(R.string.setup_error_missing_managed_provisioning))
            }
            return
        }

        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            .putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                ComponentName(this, AdminReceiver::class.java)
            )
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT, true)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_PROVISION)
        } catch (_: ActivityNotFoundException) {
            showError(getString(R.string.setup_error_missing_managed_provisioning))
        } catch (_: SecurityException) {
            showError(getString(R.string.setup_error_missing_managed_provisioning))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PROVISION) return
        if (resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.toast_setup_in_progress, Toast.LENGTH_LONG).show()
            finish()
        } else if (resultCode == RESULT_CANCELED) {
            showCancelled()
        }
    }

    private fun showCancelled() {
        intro.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        errorText.setText(R.string.setup_solution_for_cancelled_provision)
        manualHint.visibility = View.GONE
        extra.visibility = View.VISIBLE
        extra.setText(R.string.button_setup_troubleshooting)
        extra.setOnClickListener { openUrl("https://github.com/oasisfeng/island/wiki/Setup") }
        back.visibility = View.VISIBLE
        next.text = getString(R.string.button_have_checked)
        next.setOnClickListener {
            intro.visibility = View.VISIBLE
            errorContainer.visibility = View.GONE
            next.text = getString(R.string.setup_accept)
            next.setOnClickListener { beginProvisioning() }
        }
    }

    private fun showError(message: CharSequence) {
        intro.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        errorText.text = message
        manualHint.visibility = View.VISIBLE
        extra.visibility = View.VISIBLE
        extra.setText(R.string.button_instructions_online)
        extra.setOnClickListener { openUrl("https://github.com/oasisfeng/island/wiki/Setup") }
        back.visibility = View.VISIBLE
        next.text = getString(R.string.button_have_checked)
        next.setOnClickListener {
            intro.visibility = View.VISIBLE
            errorContainer.visibility = View.GONE
            next.text = getString(R.string.setup_accept)
            next.setOnClickListener { beginProvisioning() }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.prompt_operation_failure_due_to_incompatibility, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQUEST_PROVISION = 1
    }
}
