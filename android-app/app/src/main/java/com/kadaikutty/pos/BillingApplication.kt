package com.kadaikutty.pos

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kadaikutty.pos.core.auth.SessionSecurityManager
import com.kadaikutty.pos.core.network.WebSocketManager
import com.kadaikutty.pos.core.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import javax.inject.Inject

@HiltAndroidApp
class BillingApplication : Application() {
    @Inject lateinit var webSocketManager: WebSocketManager
    @Inject lateinit var sessionSecurityManager: SessionSecurityManager
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        // Amounts, dates and checksums are formatted and parsed with the default locale. On a phone
        // set to a language with its own digits or a decimal comma, that writes "१२.५" or "12,5"
        // and the next parse fails. The app is English, so format the numbers the same everywhere.
        java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, java.util.Locale.forLanguageTag("en-IN"))
        // sqlcipher-android (16 KB page size compatible) loads its native library explicitly,
        // unlike the deprecated android-database-sqlcipher's SQLiteDatabase.loadLibs(context).
        System.loadLibrary("sqlcipher")
        com.kadaikutty.pos.core.common.TrustedClock.init(this)
        // With no DSN configured the SDK would start up only to discard everything.
        if (!BuildConfig.SENTRY_DSN.isNullOrBlank()) initSentry()
        watchForeground()
    }

    /**
     * The realtime channel and the session heartbeat are for a shop that is looking at the app. With
     * thousands of phones each keeping them open all day, the API and database bill and the battery
     * drain grow with the number of devices, not with the work they do. Both stop while every screen
     * is hidden; the periodic background sync keeps running, and coming back pulls what was missed.
     */
    private fun watchForeground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var everStarted = false
            override fun onStop(owner: LifecycleOwner) {
                webSocketManager.onAppBackgrounded()
                sessionSecurityManager.onAppBackgrounded()
            }
            override fun onStart(owner: LifecycleOwner) {
                webSocketManager.onAppForegrounded()
                sessionSecurityManager.onAppForegrounded()
                // Not on the first start: launch already syncs, and there may be no session yet.
                if (everStarted) syncScheduler.requestPull()
                everStarted = true
            }
        })
    }

    private fun initSentry() {
        // Sentry 8.x's platform-agnostic Sentry.init() refuses to run on Android at all
        // ("Please, use SentryAndroid.init") — the Android entry point needs the Context.
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN ?: ""
            options.isSendDefaultPii = false
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.1
            options.profilesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.1
            options.setBeforeSend { event, _ ->
                fun sanitize(str: String?): String? {
                    if (str == null) return null
                    var s = str
                    val sensitivePatterns = listOf(
                        "Bearer\\s+[A-Za-z0-9_\\-\\.]+",
                        "password[=:]\\s*[^,\\s]+",
                        "otp[=:]\\s*\\d+",
                        "token[=:]\\s*[^,\\s]+",
                        "verifier[=:]\\s*[^,\\s]+",
                        "salt[=:]\\s*[^,\\s]+"
                    )
                    sensitivePatterns.forEach { p ->
                        s = s?.replace(Regex(p, RegexOption.IGNORE_CASE), "[REDACTED]")
                    }
                    return s
                }

                event.message?.formatted?.let {
                    event.message = io.sentry.protocol.Message().apply { formatted = sanitize(it) }
                }

                event.exceptions?.forEach { ex ->
                    ex.value = sanitize(ex.value)
                }

                event.breadcrumbs?.forEach { b ->
                    b.message = sanitize(b.message)
                }

                event
            }
        }
    }
}


