package com.nodex.client.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SshKeyDao {
    @Query("SELECT * FROM ssh_keys ORDER BY createdAt DESC")
    suspend fun getAllKeys(): List<SshKeyEntity>

    @Query("SELECT * FROM ssh_keys ORDER BY createdAt DESC")
    fun observeKeys(): Flow<List<SshKeyEntity>>

    @Query("SELECT * FROM ssh_keys WHERE id = :id")
    suspend fun getKey(id: String): SshKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: SshKeyEntity)

    @Query("DELETE FROM ssh_keys WHERE id = :id")
    suspend fun deleteKey(id: String)

    @Query("DELETE FROM ssh_keys")
    suspend fun deleteAllKeys()
}
