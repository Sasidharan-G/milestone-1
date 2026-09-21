package com.kadaikutty.pos.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class DateRangePreset(val label: String) {
    ALL("All"), TODAY("Today"), YESTERDAY("Yesterday"), LAST_7_DAYS("Last 7 days"), THIS_MONTH("This month"), CUSTOM("Custom")
}

/**
 * What a history list is narrowed to: free text plus a date range. The query runs in the
 * database (see SaleDao.searchSalesPaged), so it stays fast on a shop with years of bills.
 */
data class HistoryFilter(
    val query: String = "",
    val preset: DateRangePreset = DateRangePreset.ALL,
    /** Only for CUSTOM: local midnight of the first day and of the last day, both included. */
    val customFromEpochMs: Long? = null,
    val customToEpochMs: Long? = null
) {
    /** Start (inclusive) and end (exclusive) in epoch milliseconds, in the phone's time zone. */
    fun bounds(nowEpochMs: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        fun startOfDay(epochMs: Long, plusDays: Int = 0): Long = Calendar.getInstance(zone).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, plusDays)
        }.timeInMillis
        val today = startOfDay(nowEpochMs)
        return when (preset) {
            DateRangePreset.ALL -> 0L to Long.MAX_VALUE
            DateRangePreset.TODAY -> today to startOfDay(nowEpochMs, 1)
            DateRangePreset.YESTERDAY -> startOfDay(nowEpochMs, -1) to today
            DateRangePreset.LAST_7_DAYS -> startOfDay(nowEpochMs, -6) to startOfDay(nowEpochMs, 1)
            DateRangePreset.THIS_MONTH -> Calendar.getInstance(zone).apply {
                timeInMillis = today; set(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis to startOfDay(nowEpochMs, 1)
            DateRangePreset.CUSTOM -> {
                val from = customFromEpochMs ?: return 0L to Long.MAX_VALUE
                startOfDay(from) to startOfDay(customToEpochMs ?: from, 1)
            }
        }
    }

    val isActive: Boolean get() = query.isNotBlank() || preset != DateRangePreset.ALL
}

/** Search box and date chips shared by Sales History and Purchase History. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryFilterBar(
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    searchHint: String,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onFilterChange(filter.copy(query = it.take(60))) },
            placeholder = { Text(searchHint, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onFilterChange(filter.copy(query = "")) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(DateRangePreset.entries.toList()) { preset ->
                val label = if (preset == DateRangePreset.CUSTOM && filter.preset == DateRangePreset.CUSTOM) customLabel(filter) else preset.label
                FilterChip(
                    selected = filter.preset == preset,
                    onClick = {
                        if (preset == DateRangePreset.CUSTOM) showPicker = true
                        else onFilterChange(filter.copy(preset = preset, customFromEpochMs = null, customToEpochMs = null))
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    leadingIcon = if (preset == DateRangePreset.CUSTOM) {
                        { Icon(Icons.Default.DateRange, contentDescription = null) }
                    } else null
                )
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filter.customFromEpochMs?.let(::localToUtcMidnight),
            initialSelectedEndDateMillis = filter.customToEpochMs?.let(::localToUtcMidnight)
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        if (start != null) {
                            onFilterChange(filter.copy(
                                preset = DateRangePreset.CUSTOM,
                                customFromEpochMs = utcToLocalMidnight(start),
                                customToEpochMs = utcToLocalMidnight(pickerState.selectedEndDateMillis ?: start)
                            ))
                        }
                        showPicker = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = pickerState, title = { Text("Select dates", modifier = Modifier.fillMaxWidth()) })
        }
    }
}

private fun customLabel(filter: HistoryFilter): String {
    val format = SimpleDateFormat("dd MMM", Locale.getDefault())
    val from = filter.customFromEpochMs ?: return DateRangePreset.CUSTOM.label
    val to = filter.customToEpochMs ?: from
    return if (from == to) format.format(Date(from)) else "${format.format(Date(from))} - ${format.format(Date(to))}"
}

// The picker works in UTC midnights; the filter keeps local midnights so a day means the shop's day.
private fun utcToLocalMidnight(utcMidnight: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    return Calendar.getInstance().apply {
        clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun localToUtcMidnight(localMidnight: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMidnight }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
