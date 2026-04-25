package com.nodex.client.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HostKeyDao {
    @Query("SELECT * FROM host_keys WHERE hostnamePort = :hostnamePort")
    suspend fun getHostKey(hostnamePort: String): HostKeyEntity?

    @Query("SELECT * FROM host_keys ORDER BY hostnamePort ASC")
    fun observeHostKeys(): Flow<List<HostKeyEntity>>

    @Query("DELETE FROM host_keys")
    suspend fun deleteAllHostKeys()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHostKey(hostKey: HostKeyEntity)

    @Query("DELETE FROM host_keys WHERE hostnamePort = :hostnamePort")
    suspend fun deleteHostKey(hostnamePort: String)
}
