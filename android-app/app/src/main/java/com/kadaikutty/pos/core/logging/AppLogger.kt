package com.kadaikutty.pos.core.logging

import android.util.Log

interface AppLogger { fun info(event: String); fun error(event: String, throwable: Throwable? = null) }
class AndroidLogger : AppLogger {
    override fun info(event: String): Unit { Log.i("ClientBilling", event) }
    override fun error(event: String, throwable: Throwable?): Unit { Log.e("ClientBilling", event, throwable) }
}
