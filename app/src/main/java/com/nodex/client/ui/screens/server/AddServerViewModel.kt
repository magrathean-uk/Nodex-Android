package com.nodex.client.ui.screens.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodex.client.core.demo.DemoModeManager
import com.nodex.client.core.network.ssh.HostKeyAuditStore
import com.nodex.client.core.network.ssh.HostKeyInfo
import com.nodex.client.core.network.ssh.HostKeyPromptManager
import com.nodex.client.core.network.ssh.HostKeyVerificationRequiredException
import com.nodex.client.core.network.ssh.SSHClientWrapper
import com.nodex.client.core.network.ssh.SSHCommands
import com.nodex.client.core.network.ssh.formatSshError
import com.nodex.client.core.security.CredentialVault
import com.nodex.client.core.security.SshKeyStore
import com.nodex.client.domain.model.ServerConfig
import com.nodex.client.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionTestState(
    val isTesting: Boolean = false,
    val isSuccess: Boolean = false,
    val message: String? = null,
    val hostKeyInfo: HostKeyInfo? = null
)

@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val sshClientWrapper: SSHClientWrapper,
    private val demoModeManager: DemoModeManager,
    private val credentialsStore: CredentialVault,
    private val sshKeyStore: SshKeyStore,
    private val hostKeyAuditStore: HostKeyAuditStore,
    private val hostKeyPromptManager: HostKeyPromptManager
) : ViewModel() {

    private val _testState = MutableStateFlow(ConnectionTestState())
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()
    private val _keyImportError = MutableStateFlow<String?>(null)
    val keyImportError: StateFlow<String?> = _keyImportError.asStateFlow()
    val savedKeys = sshKeyStore.keys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var pendingServer: ServerConfig? = null
    private var pendingPassword: String? = null

    fun addServer(server: ServerConfig, password: String?) {
        viewModelScope.launch {
            repository.addServer(server)
            if (server.authType == com.nodex.client.domain.model.AuthType.PASSWORD && !password.isNullOrBlank()) {
                credentialsStore.savePassword(server.id, password)
            }
        }
    }

    fun testConnection(server: ServerConfig, password: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _testState.value = ConnectionTestState(isTesting = true)
            if (demoModeManager.isDemoMode.first()) {
                _testState.value = ConnectionTestState(
                    isTesting = false,
                    isSuccess = true,
                    message = "Demo mode: connection simulated."
                )
                return@launch
            }

            pendingServer = server
            pendingPassword = password

            val result = sshClientWrapper.execute(server, password) { client ->
                sshClientWrapper.runCommand(client, SSHCommands.detectDistro())
            }

            result.onSuccess { output ->
                val (name, version) = parseOsRelease(output)
                val suffix = if (name.isNotBlank()) " ($name $version)" else ""
                _testState.value = ConnectionTestState(
                    isTesting = false,
                    isSuccess = true,
                    message = "Connection successful$suffix"
                )
            }.onFailure { error ->
                if (error is HostKeyVerificationRequiredException) {
                    hostKeyPromptManager.requestTrust(
                        info = error.hostKeyInfo,
                        onTrust = {
                            sshClientWrapper.trustHostKey(error.hostKeyInfo)
                            val retryServer = pendingServer
                            val retryPassword = pendingPassword
                            if (retryServer != null) {
                                testConnection(retryServer, retryPassword)
                            }
                        },
                        onReject = {
                            _testState.value = ConnectionTestState(
                                isTesting = false,
                                isSuccess = false,
                                message = "Host key rejected."
                            )
                        }
                    )
                    _testState.value = ConnectionTestState(
                        isTesting = false,
                        isSuccess = false,
                        message = "Review the host key prompt to continue."
                    )
                } else {
                    val formatted = formatSshError(error)
                    _testState.value = ConnectionTestState(
                        isTesting = false,
                        isSuccess = false,
                        message = if (formatted.contains("Host key mismatch", ignoreCase = true)) {
                            "Host key changed. Review Trusted Host Keys in Settings, remove the old entry, then try again."
                        } else {
                            formatted
                        }
                    )
                }
            }
        }
    }

    fun confirmHostKey(info: HostKeyInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            sshClientWrapper.trustHostKey(info)
            val server = pendingServer
            val password = pendingPassword
            if (server != null) {
                testConnection(server, password)
            } else {
                _testState.value = ConnectionTestState(
                    isTesting = false,
                    isSuccess = true,
                    message = "Host key trusted."
                )
            }
        }
    }

    fun dismissHostKeyPrompt() {
        _testState.value = _testState.value.copy(hostKeyInfo = null)
    }

    fun rejectHostKey(info: HostKeyInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            hostKeyAuditStore.recordRejected(info)
            _testState.value = ConnectionTestState(
                isTesting = false,
                isSuccess = false,
                message = "Host key rejected."
            )
        }
    }

    fun importKey(
        name: String,
        keyText: String,
        passphrase: String?,
        onComplete: (Result<com.nodex.client.core.data.local.SshKeyEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    sshKeyStore.importKey(name, keyText, passphrase)
                }
            }
            _keyImportError.value = result.exceptionOrNull()?.message
            onComplete(result)
        }
    }

    fun clearKeyImportError() {
        _keyImportError.value = null
    }

    private fun parseOsRelease(output: String): Pair<String, String> {
        val lines = output.lines()
        val name = lines.firstOrNull { it.startsWith("NAME=") }
            ?.substringAfter("=")
            ?.trim()
            ?.trim('"')
            ?: ""
        val version = lines.firstOrNull { it.startsWith("VERSION_ID=") }
            ?.substringAfter("=")
            ?.trim()
            ?.trim('"')
            ?: ""
        return name to version
    }
}
