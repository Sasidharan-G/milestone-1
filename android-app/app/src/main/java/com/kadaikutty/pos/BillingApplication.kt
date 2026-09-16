package com.kadaikutty.pos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

@HiltAndroidApp
class BillingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // sqlcipher-android (16 KB page size compatible) loads its native library explicitly,
        // unlike the deprecated android-database-sqlcipher's SQLiteDatabase.loadLibs(context).
        System.loadLibrary("sqlcipher")
        initSentry()
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


