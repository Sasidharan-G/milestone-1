package com.company.billing.core.database

import androidx.room.TypeConverter
import com.company.billing.core.sync.SyncStatus

class SyncStatusConverter {
    @TypeConverter fun encode(value: SyncStatus): String = value.name
    @TypeConverter fun decode(value: String): SyncStatus = SyncStatus.valueOf(value)
}
