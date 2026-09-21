package com.kadaikutty.pos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One sync conflict and how it was settled. Every conflict is resolved automatically by a fixed
 * rule (see ConflictPolicy), so nothing is left stuck — but a shop owner still has to be able to
 * see what the losing side was, so it is written down here instead of disappearing.
 */
@Entity(tableName = "sync_conflicts", indices = [Index("companyId"), Index(value = ["companyId", "createdAtEpochMs"])])
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val entityType: String,
    val entityId: String,
    /** A ConflictPolicy.Resolution name, e.g. MERGED or SERVER_WINS. */
    val resolution: String,
    /** One plain sentence for the Sync Diagnostics list. */
    val summary: String,
    /** JSON with the local and server sides, and any field-level clashes. */
    val detail: String,
    val createdAtEpochMs: Long,
)

@Dao
interface SyncConflictDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun recent(companyId: String, limit: Int): Flow<List<SyncConflictEntity>>

    @Query("DELETE FROM sync_conflicts WHERE companyId = :companyId")
    suspend fun clear(companyId: String)
}
