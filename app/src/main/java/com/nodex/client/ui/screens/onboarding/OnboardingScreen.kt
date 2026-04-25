package com.nodex.client.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.R
import com.nodex.client.core.data.local.SshKeyEntity
import com.nodex.client.ui.screens.server.AddServerViewModel
import com.nodex.client.ui.screens.server.ConnectionTestState
import com.nodex.client.ui.serveredit.ServerEditorForm
import com.nodex.client.ui.serveredit.ServerEditorState
import com.nodex.client.ui.theme.NodexAccentBlue
import com.nodex.client.ui.theme.NodexBrandDarkEnd
import com.nodex.client.ui.theme.NodexBrandDarkMid
import com.nodex.client.ui.theme.NodexBrandDarkStart

private val GradientStart = NodexBrandDarkStart
private val GradientMid = NodexBrandDarkMid
private val GradientEnd = NodexBrandDarkEnd
private val AccentBlue = NodexAccentBlue

@Composable
fun OnboardingScreen(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    addServerViewModel: AddServerViewModel = hiltViewModel(),
    onOnboardingFinished: () -> Unit
) {
    var state by rememberSaveable(stateSaver = ServerEditorState.Saver) {
        mutableStateOf(ServerEditorState(autoConnect = true))
    }
    val testState by addServerViewModel.testState.collectAsStateWithLifecycle()
    val savedKeys by addServerViewModel.savedKeys.collectAsStateWithLifecycle()
    val keyImportError by addServerViewModel.keyImportError.collectAsStateWithLifecycle()

    OnboardingContent(
        state = state,
        onStateChange = { state = it },
        savedKeys = savedKeys,
        keyImportError = keyImportError,
        testState = testState,
        onTestConnection = {
            addServerViewModel.testConnection(
                state.toServerConfig(),
                state.password.takeIf {
                    state.authType == com.nodex.client.domain.model.AuthType.PASSWORD && it.isNotBlank()
                }
            )
        },
        onImportKey = addServerViewModel::importKey,
        onComplete = {
            addServerViewModel.addServer(
                state.toServerConfig(),
                state.password.takeIf {
                    state.authType == com.nodex.client.domain.model.AuthType.PASSWORD && it.isNotBlank()
                }
            )
            onboardingViewModel.completeOnboarding()
            onOnboardingFinished()
        },
        onDemo = {
            onboardingViewModel.enterDemoMode()
            onOnboardingFinished()
        }
    )
}

@Composable
fun OnboardingContent(
    onComplete: () -> Unit,
    onDemo: () -> Unit
) {
    var state by rememberSaveable(stateSaver = ServerEditorState.Saver) {
        mutableStateOf(ServerEditorState(autoConnect = true))
    }

    OnboardingContent(
        state = state,
        onStateChange = { state = it },
        savedKeys = emptyList(),
        keyImportError = null,
        testState = ConnectionTestState(),
        onTestConnection = {},
        onImportKey = { _, _, _, _ -> },
        onComplete = onComplete,
        onDemo = onDemo
    )
}

@Composable
fun OnboardingContent(
    state: ServerEditorState,
    onStateChange: (ServerEditorState) -> Unit,
    savedKeys: List<SshKeyEntity>,
    keyImportError: String?,
    testState: ConnectionTestState,
    onTestConnection: () -> Unit,
    onImportKey: (
        name: String,
        keyText: String,
        passphrase: String?,
        onComplete: (Result<SshKeyEntity>) -> Unit
    ) -> Unit,
    onComplete: () -> Unit,
    onDemo: () -> Unit
) {
    var logoTapCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientMid, GradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AccentBlue.copy(alpha = 0.18f))
                    .clickable {
                        logoTapCount += 1
                        if (logoTapCount >= 5) {
                            logoTapCount = 0
                            onDemo()
                        }
                    }
                    .semantics { testTag = "onboarding.logo" },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_onboarding_logo),
                    contentDescription = "Nodex",
                    modifier = Modifier.size(58.dp)
                )
            }

            Text(
                "Set up your first server",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Add a Linux host now. You can test SSH before saving, import a key, or enter demo mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                lineHeight = 22.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ServerEditorForm(
                    state = state,
                    onStateChange = onStateChange,
                    savedKeys = savedKeys,
                    keyImportError = keyImportError,
                    testState = testState,
                    primaryButtonText = "Continue",
                    onTestConnection = onTestConnection,
                    onSave = onComplete,
                    onImportKey = onImportKey,
                    footer = {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
                            Text("Enter Demo Mode")
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-connect after save", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Keep this enabled for the normal Nodex flow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
                Switch(
                    checked = state.autoConnect,
                    onCheckedChange = { onStateChange(state.copy(autoConnect = it)) }
                )
            }
        }
    }
}
