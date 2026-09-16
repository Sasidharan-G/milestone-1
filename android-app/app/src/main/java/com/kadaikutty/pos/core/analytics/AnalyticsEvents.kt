package com.kadaikutty.pos.core.analytics

object AnalyticsEvents {
    // Auth & Security Events
    const val EVENT_LOGIN_SUCCESS = "login_success"
    const val EVENT_LOGIN_FAILED = "login_failed"
    const val EVENT_OTP_REQUESTED = "otp_requested"
    const val EVENT_OTP_VERIFIED = "otp_verified"
    const val EVENT_OTP_FAILED = "otp_failed"
    const val EVENT_LICENSE_VERIFIED = "license_verified"
    const val EVENT_LICENSE_EXPIRED = "license_expired"

    // Business & Operational Events
    const val EVENT_SALE_COMPLETED = "sale_completed"
    const val PARAM_CART_SIZE = "cart_size"
    const val PARAM_TOTAL_AMOUNT = "total_amount"
    const val PARAM_PAYMENT_METHOD = "payment_method"

    // Sync & Reliability Events
    const val EVENT_SYNC_COMPLETED = "sync_completed"
    const val EVENT_SYNC_FAILED = "sync_failed"
    const val PARAM_RECORDS_PUSHED = "records_pushed"
    const val PARAM_SYNC_DURATION = "duration_ms"
    const val PARAM_RETRY_COUNT = "retry_count"

    // Hardware Events
    const val EVENT_PRINTER_ERROR = "printer_error"
    const val PARAM_PRINTER_TYPE = "printer_type"

    // General Audit Parameters (Privacy-safe: no passwords, tokens, or PII)
    const val PARAM_ROLE = "role"
    const val PARAM_LOGIN_MODE = "login_mode"
    const val PARAM_ERROR_TYPE = "error_type"
    const val PARAM_REASON = "reason"
}

