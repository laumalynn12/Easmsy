package com.easmsy.file

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Hosts the platform ExternalStorageProvider inside this process and exposes it under a
 * private authority.  The system provider is ordinary app code; instantiating it here
 * avoids the signature-level MANAGE_DOCUMENTS permission, which a normal app cannot get.
 *
 * The implementation deliberately has no dependency on the original project: only the
 * public Android provider interfaces and the system provider's well-known authority are
 * used.  If a vendor ROM has removed or rewritten that provider the proxy simply reports
 * itself as unavailable instead of crashing the caller.
 */
class ExternalStorageProviderProxy : ContentProvider() {

    private var targetAuthority: String? = null
    private var delegate: ContentProvider? = null
    private var providerInfo: ProviderInfo? = null

    override fun attachInfo(context: Context, info: ProviderInfo) {
        providerInfo = info
        super.attachInfo(context, info)
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val target = findTargetProvider(context) ?: return false
        targetAuthority = target.authority
        return try {
            val targetContext = context.createPackageContext(
                target.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            val clazz = targetContext.classLoader.loadClass(target.name)
            val provider = clazz.getDeclaredConstructor().newInstance() as ContentProvider
            provider.attachInfo(targetContext, target)
            delegate = provider
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to host the system external-storage provider", e)
            delegate = EmptyProvider()
            false
        }
    }

    private fun findTargetProvider(context: Context): ProviderInfo? {
        val pm = context.packageManager
        try {
            pm.resolveContentProvider(TARGET_AUTHORITY, 0)?.let { return it }
        } catch (_: RuntimeException) {
            // Fall through to provider discovery.
        }
        return try {
            pm.queryIntentContentProviders(
                android.content.Intent(android.provider.DocumentsContract.PROVIDER_INTERFACE)
                    .setPackage(TARGET_PACKAGE),
                0
            ).firstOrNull()?.providerInfo
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun targetUri(uri: Uri): Uri =
        targetAuthority?.let { replaceAuthority(uri, it) } ?: uri

    private fun proxyUri(uri: Uri): Uri =
        providerInfo?.authority?.let { replaceAuthority(uri, it) } ?: uri

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = runCatching {
        delegate?.query(targetUri(uri), projection, selection, selectionArgs, sortOrder)
    }.getOrNull()

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: android.os.Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            delegate?.query(targetUri(uri), projection, queryArgs, cancellationSignal)
        } else {
            delegate?.query(targetUri(uri), projection, null, null, null)
        }
    }.getOrNull()

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
        cancellationSignal: CancellationSignal?
    ): Cursor? = runCatching {
        delegate?.query(targetUri(uri), projection, selection, selectionArgs, sortOrder, cancellationSignal)
    }.getOrNull()

    override fun getType(uri: Uri): String? =
        runCatching { delegate?.getType(targetUri(uri)) }.getOrNull()

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        runCatching { delegate?.insert(targetUri(uri), values)?.let(::proxyUri) }.getOrNull()

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>?): Int =
        runCatching { delegate?.bulkInsert(targetUri(uri), values) }.getOrDefault(0)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        runCatching { delegate?.delete(targetUri(uri), selection, selectionArgs) }.getOrDefault(0)

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = runCatching { delegate?.update(targetUri(uri), values, selection, selectionArgs) }
        .getOrDefault(0)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        runCatching { delegate?.openFile(targetUri(uri), mode) }.getOrNull()

    override fun openFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? =
        runCatching { delegate?.openFile(targetUri(uri), mode, signal) }.getOrNull()

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? =
        runCatching { delegate?.openAssetFile(targetUri(uri), mode) }.getOrNull()

    override fun openAssetFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?
    ): AssetFileDescriptor? =
        runCatching { delegate?.openAssetFile(targetUri(uri), mode, signal) }.getOrNull()

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: android.os.Bundle?
    ): AssetFileDescriptor? =
        runCatching { delegate?.openTypedAssetFile(targetUri(uri), mimeTypeFilter, opts) }.getOrNull()

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: android.os.Bundle?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? =
        runCatching { delegate?.openTypedAssetFile(targetUri(uri), mimeTypeFilter, opts, signal) }
            .getOrNull()

    override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
        val mapped = extras?.let {
            it.getParcelable<Uri>(EXTRA_URI)?.let { uri -> it.putParcelable(EXTRA_URI, targetUri(uri)) }
            it.getParcelable<Uri>(EXTRA_PARENT_URI)?.let { uri -> it.putParcelable(EXTRA_PARENT_URI, targetUri(uri)) }
            it
        }
        val result = runCatching { delegate?.call(method, arg, mapped) }.getOrNull() ?: return null
        result.getParcelable<Uri>(EXTRA_URI)?.let { result.putParcelable(EXTRA_URI, proxyUri(it)) }
        return result
    }

    override fun applyBatch(
        operations: ArrayList<ContentProviderOperation>
    ): Array<ContentProviderResult> = throw OperationApplicationException("Not supported")

    companion object {
        private const val TAG = "Easmsy.File"
        private const val TARGET_AUTHORITY = "com.android.externalstorage.documents"
        private const val TARGET_PACKAGE = "com.android.externalstorage"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_PARENT_URI = "parentUri"

        fun replaceAuthority(uri: Uri, authority: String): Uri {
            val builder = Uri.Builder()
                .scheme(uri.scheme ?: "content")
                .authority(authority)
            uri.encodedPath?.let { builder.encodedPath(it) }
            uri.encodedQuery?.let { builder.encodedQuery(it) }
            uri.encodedFragment?.let { builder.encodedFragment(it) }
            return builder.build()
        }
    }
}
