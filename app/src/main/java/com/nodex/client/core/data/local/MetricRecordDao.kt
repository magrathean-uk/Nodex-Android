package com.nodex.client.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MetricRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<MetricRecordEntity>)

    @Query("SELECT * FROM metric_records WHERE serverId = :serverId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecordsForServer(serverId: String, limit: Int = 2880): Flow<List<MetricRecordEntity>>

    @Query("SELECT * FROM metric_records WHERE serverId = :serverId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecordsForServerSync(serverId: String, limit: Int = 2880): List<MetricRecordEntity>

    @Query("DELETE FROM metric_records WHERE serverId = :serverId AND id NOT IN (SELECT id FROM metric_records WHERE serverId = :serverId ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun trimRecords(serverId: String, keepCount: Int = 2880)

    @Query("DELETE FROM metric_records WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)
}
