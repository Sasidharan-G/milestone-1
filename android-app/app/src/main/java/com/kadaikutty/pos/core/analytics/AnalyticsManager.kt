package com.kadaikutty.pos.core.analytics

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsManager @Inject constructor() {
    fun logEvent(eventName: String, params: Map<String, Any> = emptyMap()) {
        Log.d("AppAnalytics", "$eventName ${params.keys}")
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "app.event"
            message = eventName
            params.forEach { (key, value) -> setData(key, value.toString().take(200)) }
        })
    }

    fun setUserProperty(name: String, value: String) {
        Sentry.configureScope { scope -> scope.setTag("user.$name", value.take(200)) }
    }
}

