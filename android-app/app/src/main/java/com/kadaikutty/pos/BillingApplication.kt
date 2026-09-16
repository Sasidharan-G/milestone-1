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


