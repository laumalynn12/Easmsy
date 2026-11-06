package com.easmsy.installer

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.easmsy.R
import com.easmsy.core.DevicePolicies
import com.easmsy.core.Notifications
import com.easmsy.core.Users

/**
 * Small PackageInstaller front-end.
 *
 * The original implementation also parsed APKs to show a detailed confirmation and to
 * clone split APKs.  The rewrite keeps the important behaviour (install from a content or
 * file URI, clone an installed app into Island) and leaves the system installer to show
 * the normal APK confirmation UI.
 */
class InstallerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data == null) {
            finish()
            return
        }
        if (data.scheme == "package") {
            clonePackage(data.schemeSpecificPart)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: RuntimeException) {
                Toast.makeText(this, R.string.prompt_operation_failure_due_to_incompatibility, Toast.LENGTH_LONG).show()
            }
            finish()
            return
        }
        install(data)
    }

    private fun clonePackage(pkg: String?) {
        if (pkg == null) {
            finish()
            return
        }
        val target = if (Users.isOwner()) {
            val profile = Users.profile(this)
            if (profile != null) Users.contextAsUser(this, profile) ?: this else this
        } else this
        val policies = DevicePolicies(target)
        val ok = policies.installExistingPackage(pkg) || policies.enableSystemApp(pkg)
        Toast.makeText(
            this,
            if (ok) getString(R.string.toast_successfully_cloned, pkg)
            else getString(R.string.toast_cannot_clone, pkg),
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun install(data: Uri) {
        Toast.makeText(
            this,
            getString(
                R.string.progress_dialog_installing,
                getString(R.string.app_name),
                data.lastPathSegment ?: data.toString()
            ),
            Toast.LENGTH_SHORT
        ).show()
        Thread {
            var session: PackageInstaller.Session? = null
            try {
                val installer = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
                    }
                }
                val sessionId = installer.createSession(params)
                val active = installer.openSession(sessionId)
                session = active
                val input = contentResolver.openInputStream(data)
                    ?: throw java.io.IOException("Unable to open $data")
                input.use { stream ->
                    active.openWrite("base.apk", 0, -1).use { output ->
                        stream.copyTo(output, 64 * 1024)
                        active.fsync(output)
                    }
                }
                val callback = PendingIntent.getBroadcast(
                    this,
                    sessionId,
                    Intent(this, InstallResultReceiver::class.java)
                        .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId),
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                active.commit(callback.intentSender)
                active.close()
                session = null
            } catch (e: Exception) {
                Notifications.showInstallResult(this, false, e.message)
            } finally {
                try {
                    session?.abandon()
                    session?.close()
                } catch (_: RuntimeException) {
                    // Session already closed.
                }
            }
            runOnUiThread { finish() }
        }.apply { name = "apk-installer"; isDaemon = true }.start()
    }
}
