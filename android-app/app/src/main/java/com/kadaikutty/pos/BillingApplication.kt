package com.kadaikutty.pos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.Sentry

@HiltAndroidApp
class BillingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        initSentry()
    }

    private fun initSentry() {
        Sentry.init { options ->
            options.dsn = BuildConfig.SENTRY_DSN ?: ""
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.1
            options.profilesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.1
        }
    }
}

