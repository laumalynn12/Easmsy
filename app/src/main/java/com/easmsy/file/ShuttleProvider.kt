package com.easmsy.file

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Process
import com.easmsy.core.Users

/**
 * Cross-profile documents provider.  It forwards every DocumentsContract operation to the
 * private [ExternalStorageProviderProxy] running in the other user (Mainland -> Island, or
 * Island -> Mainland).  The target authority uses Android's well-known "userId@authority"
 * form; the same application ID in both users is what makes the private provider reachable.
 */
class ShuttleProvider : ContentProvider() {

    private var shuttleAuthority: String? = null
    private var targetAuthority: String? = null
    private var resolver: android.content.ContentResolver? = null

    override fun attachInfo(context: Context, info: ProviderInfo) {
        shuttleAuthority = info.authority
        super.attachInfo(context, info)
    }

    override fun onCreate(): Boolean {
        resolver = context?.contentResolver
        val current = Process.myUserHandle()
        val targetUser = if (Users.isOwner(current)) {
            Users.profile(context ?: return false) ?: Users.owner()
        } else {
            Users.owner()
        }
        val host = shuttleAuthority?.removeSuffix(".shuttle") ?: return false
        targetAuthority = "${Users.id(targetUser)}@$host"
        return true
    }

    private fun targetUri(uri: Uri): Uri {
        val authority = targetAuthority ?: return uri
        return ExternalStorageProviderProxy.replaceAuthority(uri, authority)
    }

    private fun proxyUri(uri: Uri): Uri {
        val authority = shuttleAuthority ?: return uri
        return ExternalStorageProviderProxy.replaceAuthority(uri, authority)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = forward {
        resolver?.query(targetUri(uri), projection, selection, selectionArgs, sortOrder)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor? = forward {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            resolver?.query(targetUri(uri), projection, queryArgs, cancellationSignal)
        } else {
            resolver?.query(targetUri(uri), projection, null, null, null)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
        cancellationSignal: CancellationSignal?
    ): Cursor? = forward {
        resolver?.query(targetUri(uri), projection, selection, selectionArgs, sortOrder, cancellationSignal)
    }

    override fun getType(uri: Uri): String? = forward {
        resolver?.getType(targetUri(uri))
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = forward {
        resolver?.insert(targetUri(uri), values)?.let(::proxyUri)
    }

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int = forward {
        resolver?.bulkInsert(targetUri(uri), values) ?: 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = forward {
        resolver?.delete(targetUri(uri), selection, selectionArgs) ?: 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = forward {
        resolver?.update(targetUri(uri), values, selection, selectionArgs) ?: 0
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = forwardFd {
        resolver?.openFileDescriptor(targetUri(uri), mode)
    }

    override fun openFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? = forwardFd {
        resolver?.openFileDescriptor(targetUri(uri), mode, signal)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? = forwardFd {
        resolver?.openAssetFileDescriptor(targetUri(uri), mode)
    }

    override fun openAssetFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?
    ): AssetFileDescriptor? = forwardFd {
        resolver?.openAssetFileDescriptor(targetUri(uri), mode, signal)
    }

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?
    ): AssetFileDescriptor? = forwardFd {
        resolver?.openTypedAssetFileDescriptor(targetUri(uri), mimeTypeFilter, opts)
    }

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? = forwardFd {
        resolver?.openTypedAssetFileDescriptor(targetUri(uri), mimeTypeFilter, opts, signal)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = forward {
        val base = Uri.Builder().scheme("content").authority(targetAuthority).build()
        val result = resolver?.call(base, method, arg, extras)
        result?.getParcelable<Uri>(EXTRA_URI)?.let { result.putParcelable(EXTRA_URI, proxyUri(it)) }
        result
    }

    private inline fun <T> forward(block: () -> T?): T? {
        val identity = Binder.clearCallingIdentity()
        return try {
            block()
        } catch (_: RuntimeException) {
            null
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private inline fun <T> forwardFd(block: () -> T?): T? {
        val identity = Binder.clearCallingIdentity()
        return try {
            block()
        } catch (_: java.io.FileNotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    companion object {
        private const val EXTRA_URI = "uri"
    }
}
