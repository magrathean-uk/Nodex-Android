package com.nodex.client.di

import android.content.Context
import androidx.room.Room
import com.nodex.client.core.data.local.NodexDatabase
import com.nodex.client.core.data.local.ServerDao
import com.nodex.client.core.data.local.Migration1To2
import com.nodex.client.core.data.local.Migration2To3
import com.nodex.client.core.data.local.Migration3To4
import com.nodex.client.core.data.local.Migration4To5
import com.nodex.client.core.security.CredentialVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        credentialsStore: CredentialVault
    ): NodexDatabase {
        return Room.databaseBuilder(
            context,
            NodexDatabase::class.java,
            "nodex_db"
        )
            .addMigrations(
                Migration1To2(),
                Migration2To3(credentialsStore),
                Migration3To4(),
                Migration4To5()
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideServerDao(db: NodexDatabase): ServerDao {
        return db.serverDao()
    }

    @Provides
    @Singleton
    fun provideHostKeyDao(db: NodexDatabase): com.nodex.client.core.data.local.HostKeyDao {
        return db.hostKeyDao()
    }

    @Provides
    @Singleton
    fun provideMetricRecordDao(db: NodexDatabase): com.nodex.client.core.data.local.MetricRecordDao {
        return db.metricRecordDao()
    }

    @Provides
    @Singleton
    fun provideSshKeyDao(db: NodexDatabase): com.nodex.client.core.data.local.SshKeyDao {
        return db.sshKeyDao()
    }

    @Provides
    @Singleton
    fun provideHostKeyAuditDao(db: NodexDatabase): com.nodex.client.core.data.local.HostKeyAuditDao {
        return db.hostKeyAuditDao()
    }
}
