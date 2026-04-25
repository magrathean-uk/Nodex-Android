package com.nodex.client.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nodex.client.core.network.ssh.HostKeyTrustPrompt

@Composable
fun HostKeyTrustDialog(
    prompt: HostKeyTrustPrompt?,
    onTrust: () -> Unit,
    onReject: () -> Unit
) {
    prompt ?: return

    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Verify host key") },
        text = {
            Column {
                Text("Confirm the server fingerprint before trusting this host.")
                Text("Host: ${prompt.info.hostname}:${prompt.info.port}")
                Text("Fingerprint:")
                Text(prompt.info.fingerprint, style = MaterialTheme.typography.bodyMedium)
                Text("Key type: ${prompt.info.keyType}")
                Text(
                    "Decisions are recorded in Settings > Trusted Host Keys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) {
                Text("Trust")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Reject")
            }
        }
    )
}
