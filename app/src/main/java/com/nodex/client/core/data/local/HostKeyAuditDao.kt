package com.nodex.client.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HostKeyAuditDao {
    @Query("SELECT * FROM host_key_audit_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 20): Flow<List<HostKeyAuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: HostKeyAuditEventEntity)

    @Query("DELETE FROM host_key_audit_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM host_key_audit_events WHERE id NOT IN (SELECT id FROM host_key_audit_events ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
