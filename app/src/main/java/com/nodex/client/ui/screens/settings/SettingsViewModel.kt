package com.nodex.client.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodex.client.core.demo.DemoModeManager
import com.nodex.client.core.network.ssh.HostKeyAuditStore
import com.nodex.client.core.security.CredentialVault
import com.nodex.client.core.security.SshKeyStore
import com.nodex.client.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val demoModeManager: DemoModeManager,
    private val metricRecordDao: com.nodex.client.core.data.local.MetricRecordDao,
    private val serverRepository: com.nodex.client.domain.repository.ServerRepository,
    private val credentialsStore: CredentialVault,
    private val hostKeyDao: com.nodex.client.core.data.local.HostKeyDao,
    private val hostKeyAuditStore: HostKeyAuditStore,
    private val sshKeyStore: SshKeyStore
) : ViewModel() {

    val theme: Flow<UserPreferences.Theme> = userPreferences.theme
    val isDemoMode = demoModeManager.isDemoMode

    fun setTheme(theme: UserPreferences.Theme) {
        viewModelScope.launch {
            userPreferences.setTheme(theme)
        }
    }

    fun exitDemoMode() {
        viewModelScope.launch {
            demoModeManager.exitDemoMode()
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            val servers = serverRepository.getAllServersSync()
            servers.forEach { server ->
                metricRecordDao.deleteForServer(server.id)
                credentialsStore.clearSudoPassword(server.id)
                serverRepository.deleteServer(server)
            }
            hostKeyDao.deleteAllHostKeys()
            hostKeyAuditStore.clearAll()
            sshKeyStore.deleteAllKeys()
            userPreferences.setTheme(UserPreferences.Theme.SYSTEM)
            demoModeManager.exitDemoMode()
            userPreferences.setOnboardingCompleted(false)
        }
    }
}
