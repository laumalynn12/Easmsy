package com.easmsy.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Local application-list endpoint.  The original used a multiprocess provider as an
 * in-process cache; the rewrite keeps the authority for compatibility but only answers
 * explicit package/metadata queries.  Normal UI work goes through AppRepository instead.
 */
class AppListProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val context = context ?: return MatrixCursor(COLUMNS)
        val requested = uri.lastPathSegment?.takeIf { it.isNotEmpty() }
        val pm = context.packageManager
        val cursor = MatrixCursor(COLUMNS)
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
            PackageManager.MATCH_DISABLED_COMPONENTS
        try {
            if (requested != null) {
                val info = pm.getApplicationInfo(requested, flags)
                add(cursor, pm, info)
            } else {
                for (info in pm.getInstalledApplications(flags)) add(cursor, pm, info)
            }
        } catch (_: Exception) {
            // Return an empty cursor; callers handle a missing package normally.
        }
        return cursor
    }

    private fun add(cursor: MatrixCursor, pm: PackageManager, info: android.content.pm.ApplicationInfo) {
        val label = try {
            info.loadLabel(pm).toString()
        } catch (_: RuntimeException) {
            info.packageName
        }
        cursor.addRow(
            arrayOf(
                info.packageName,
                label,
                if ((info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) 1 else 0,
                if ((info.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED) != 0) 1 else 0
            )
        )
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.com.easmsy.apps"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private val COLUMNS = arrayOf("package", "label", "system", "installed")
    }
}
