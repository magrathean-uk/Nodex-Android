package com.nodex.client.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodex.client.core.data.local.SshKeyEntity
import com.nodex.client.core.security.SshKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SshKeyLibraryViewModel @Inject constructor(
    private val sshKeyStore: SshKeyStore
) : ViewModel() {

    val keys: StateFlow<List<SshKeyEntity>> =
        sshKeyStore.keys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    fun importKey(
        name: String,
        keyText: String,
        passphrase: String?,
        onComplete: (Result<SshKeyEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    sshKeyStore.importKey(name, keyText, passphrase)
                }
            }
            _importError.value = result.exceptionOrNull()?.message
            onComplete(result)
        }
    }

    fun deleteKey(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sshKeyStore.deleteKey(id)
        }
    }

    fun clearImportError() {
        _importError.value = null
    }
}
