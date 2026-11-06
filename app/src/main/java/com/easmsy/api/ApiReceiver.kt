package com.easmsy.api

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Broadcast API entry point; returns its result through the ordered-broadcast result. */
class ApiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val verification = ApiDispatcher.verifyCaller(context, intent, null, -1)
        if (verification != null) {
            setResult(ApiDispatcher.RESULT_UNVERIFIED_IDENTITY)
            return
        }
        val error = ApiDispatcher.dispatch(context, intent)
        setResult(if (error == null) Activity.RESULT_OK else Activity.RESULT_CANCELED)
    }

    private fun setResult(code: Int) {
        if (isOrderedBroadcast) setResult(code, null, null)
    }
}
