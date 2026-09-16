package com.kadaikutty.pos.core.logging

import android.util.Log

interface AppLogger {
    fun info(event: String)
    fun warn(event: String)
    fun error(event: String, throwable: Throwable? = null)
}

class AndroidLogger : AppLogger {
    override fun info(event: String) {
        Log.i("KadaikuttyPOS", event)
        try { io.sentry.Sentry.addBreadcrumb(event) } catch (_: Exception) {}
    }

    override fun warn(event: String) {
        Log.w("KadaikuttyPOS", event)
        try { io.sentry.Sentry.addBreadcrumb(event) } catch (_: Exception) {}
    }

    override fun error(event: String, throwable: Throwable?) {
        Log.e("KadaikuttyPOS", event, throwable)
        try {
            if (throwable != null) {
                io.sentry.Sentry.captureException(throwable)
            } else {
                io.sentry.Sentry.captureMessage(event)
            }
        } catch (_: Exception) {}
    }
}
