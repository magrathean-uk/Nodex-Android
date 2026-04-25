package com.nodex.client.ui.screens.server

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.ui.components.NodexDetailScaffold
import com.nodex.client.ui.serveredit.ServerEditorForm
import com.nodex.client.ui.serveredit.ServerEditorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    viewModel: AddServerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    var state by rememberSaveable(stateSaver = ServerEditorState.Saver) {
        mutableStateOf(ServerEditorState())
    }
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val savedKeys by viewModel.savedKeys.collectAsStateWithLifecycle()
    val keyImportError by viewModel.keyImportError.collectAsStateWithLifecycle()

    NodexDetailScaffold(title = "Add Server", onBack = onBack) { innerPadding ->
        ServerEditorForm(
            state = state,
            onStateChange = { state = it },
            savedKeys = savedKeys,
            keyImportError = keyImportError,
            testState = testState,
            primaryButtonText = "Save Server",
            onTestConnection = {
                viewModel.testConnection(
                    server = state.toServerConfig(),
                    password = state.password.takeIf { state.authType == com.nodex.client.domain.model.AuthType.PASSWORD && it.isNotBlank() }
                )
            },
            onSave = {
                viewModel.addServer(
                    server = state.toServerConfig(),
                    password = state.password.takeIf { state.authType == com.nodex.client.domain.model.AuthType.PASSWORD && it.isNotBlank() }
                )
                onBack()
            },
            onImportKey = viewModel::importKey,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}
