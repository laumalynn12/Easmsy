package com.easmsy.api

import android.app.Activity
import android.os.Bundle

/**
 * Activity-based API entry point.  Prefer this over the broadcast receiver when a caller
 * needs a synchronous result.  The activity is transparent and finishes immediately.
 */
class ApiActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: android.content.Intent) {
        if (intent.action == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val caller = callingActivity
        val callerPackage: String?
        val callerUid: Int
        if (caller != null) {
            callerPackage = caller.packageName
            callerUid = try {
                packageManager.getActivityInfo(caller, 0).applicationInfo.uid
            } catch (_: Exception) {
                -1
            }
        } else {
            callerPackage = callingPackage
            callerUid = if (callerPackage != null) {
                try {
                    packageManager.getPackageUid(callerPackage, 0)
                } catch (_: Exception) {
                    -1
                }
            } else -1
        }

        val verification = ApiDispatcher.verifyCaller(this, intent, callerPackage, callerUid)
        if (verification != null) {
            setResult(ApiDispatcher.RESULT_UNVERIFIED_IDENTITY)
            finish()
            return
        }
        val error = ApiDispatcher.dispatch(this, intent)
        if (error == null) {
            setResult(Activity.RESULT_OK)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}
