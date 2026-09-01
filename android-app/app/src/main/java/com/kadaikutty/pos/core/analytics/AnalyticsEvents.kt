package com.kadaikutty.pos.core.analytics

object AnalyticsEvents {
    const val EVENT_LOGIN_SUCCESS = "login_success"
    const val EVENT_LOGIN_FAILED = "login_failed"
    
    const val EVENT_SALE_COMPLETED = "sale_completed"
    const val PARAM_CART_SIZE = "cart_size"
    const val PARAM_TOTAL_AMOUNT = "total_amount"
    const val PARAM_PAYMENT_METHOD = "payment_method"

    const val EVENT_SYNC_COMPLETED = "sync_completed"
    const val PARAM_RECORDS_PUSHED = "records_pushed"
    const val PARAM_SYNC_DURATION = "duration_ms"
}
