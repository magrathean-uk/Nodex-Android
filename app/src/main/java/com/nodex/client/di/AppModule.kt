package com.nodex.client.di

import android.content.Context
import com.nodex.client.core.network.ssh.HostKeyAuditStore
import com.nodex.client.core.network.ssh.HostKeyPromptManager
import com.nodex.client.core.network.ssh.SSHClientWrapper
import com.nodex.client.core.network.ssh.SSHConnectionPool
import com.nodex.client.core.security.CredentialVault
import com.nodex.client.core.security.SecureCredentialsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCredentialVault(
        secureCredentialsStore: SecureCredentialsStore
    ): CredentialVault = secureCredentialsStore

    @Provides
    @Singleton
    fun provideSSHClientWrapper(
        @ApplicationContext context: Context,
        hostKeyDao: com.nodex.client.core.data.local.HostKeyDao,
        credentialsStore: CredentialVault,
        hostKeyAuditStore: HostKeyAuditStore,
        hostKeyPromptManager: HostKeyPromptManager
    ): SSHClientWrapper {
        return SSHClientWrapper(
            context,
            hostKeyDao,
            credentialsStore,
            hostKeyAuditStore,
            hostKeyPromptManager
        )
    }

    @Provides
    @Singleton
    fun provideSSHConnectionPool(
        @ApplicationContext context: Context,
        hostKeyDao: com.nodex.client.core.data.local.HostKeyDao,
        credentialsStore: CredentialVault,
        hostKeyAuditStore: HostKeyAuditStore,
        hostKeyPromptManager: HostKeyPromptManager
    ): SSHConnectionPool {
        return SSHConnectionPool(
            context,
            hostKeyDao,
            credentialsStore,
            hostKeyAuditStore,
            hostKeyPromptManager
        )
    }
}
