package com.nodex.client.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodex.client.core.network.ssh.HostKeyPromptManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostKeyPromptViewModel @Inject constructor(
    private val promptManager: HostKeyPromptManager
) : ViewModel() {
    val activePrompt = promptManager.activePrompt

    fun trustActivePrompt() {
        viewModelScope.launch {
            promptManager.trustActivePrompt()
        }
    }

    fun rejectActivePrompt() {
        viewModelScope.launch {
            promptManager.rejectActivePrompt()
        }
    }
}
