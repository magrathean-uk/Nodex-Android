package com.nodex.client.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodex.client.core.data.local.HostKeyAuditEventEntity
import com.nodex.client.core.data.local.HostKeyDao
import com.nodex.client.core.data.local.HostKeyEntity
import com.nodex.client.core.network.ssh.HostKeyAuditStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostKeysViewModel @Inject constructor(
    private val hostKeyDao: HostKeyDao,
    private val hostKeyAuditStore: HostKeyAuditStore
) : ViewModel() {

    val hostKeys: StateFlow<List<HostKeyEntity>> = hostKeyDao.observeHostKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentEvents: StateFlow<List<HostKeyAuditEventEntity>> =
        hostKeyAuditStore.observeRecentEvents()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteHostKey(hostnamePort: String) {
        viewModelScope.launch {
            hostKeyDao.getHostKey(hostnamePort)?.let { hostKeyAuditStore.recordForgotten(it) }
            hostKeyDao.deleteHostKey(hostnamePort)
        }
    }
}
