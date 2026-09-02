package com.kadaikutty.pos.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val threadLocalDateTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        }
    }

    private val threadLocalDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }

    private val threadLocalTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("hh:mm a", Locale.getDefault())
        }
    }

    fun formatDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return threadLocalDateTimeFormat.get()?.format(Date(epochMs)) ?: ""
    }

    fun formatDate(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return threadLocalDateFormat.get()?.format(Date(epochMs)) ?: ""
    }

    fun formatTime(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return threadLocalTimeFormat.get()?.format(Date(epochMs)) ?: ""
    }
}
