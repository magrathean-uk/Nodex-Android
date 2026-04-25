package com.nodex.client.core.network.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class HostKeyTrustPrompt(
    val id: String = UUID.randomUUID().toString(),
    val info: HostKeyInfo
)

data class HostKeyMismatchAlert(
    val hostname: String,
    val port: Int,
    val oldFingerprint: String,
    val newFingerprint: String
)

@Singleton
class HostKeyPromptManager @Inject constructor(
    private val hostKeyAuditStore: HostKeyAuditStore
) {
    private data class PendingPrompt(
        val prompt: HostKeyTrustPrompt,
        val onTrust: suspend () -> Unit,
        val onReject: suspend () -> Unit
    )

    private val _activePrompt = MutableStateFlow<HostKeyTrustPrompt?>(null)
    val activePrompt: StateFlow<HostKeyTrustPrompt?> = _activePrompt.asStateFlow()

    private val _activeMismatch = MutableStateFlow<HostKeyMismatchAlert?>(null)
    val activeMismatch: StateFlow<HostKeyMismatchAlert?> = _activeMismatch.asStateFlow()

    private var pendingPrompt: PendingPrompt? = null

    suspend fun requestTrust(
        info: HostKeyInfo,
        onTrust: suspend () -> Unit,
        onReject: suspend () -> Unit = {}
    ) {
        val prompt = HostKeyTrustPrompt(info = info)
        pendingPrompt = PendingPrompt(
            prompt = prompt,
            onTrust = onTrust,
            onReject = onReject
        )
        _activePrompt.value = prompt
    }

    suspend fun trustActivePrompt() {
        val current = pendingPrompt ?: return
        pendingPrompt = null
        _activePrompt.value = null
        current.onTrust()
    }

    suspend fun rejectActivePrompt() {
        val current = pendingPrompt ?: return
        pendingPrompt = null
        _activePrompt.value = null
        runCatching {
            hostKeyAuditStore.recordRejected(current.prompt.info)
        }
        current.onReject()
    }

    fun clearPrompt() {
        pendingPrompt = null
        _activePrompt.value = null
    }

    fun showMismatch(
        hostname: String,
        port: Int,
        oldFingerprint: String,
        newFingerprint: String
    ) {
        _activeMismatch.value = HostKeyMismatchAlert(
            hostname = hostname,
            port = port,
            oldFingerprint = oldFingerprint,
            newFingerprint = newFingerprint
        )
    }

    fun clearMismatch() {
        _activeMismatch.value = null
    }
}
