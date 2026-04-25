package com.nodex.client.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nodex.client.core.network.ssh.HostKeyMismatchAlert

@Composable
fun HostKeyMismatchDialog(
    mismatch: HostKeyMismatchAlert?,
    onDismiss: () -> Unit,
    onOpenTrustedHostKeys: () -> Unit
) {
    mismatch ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Host key changed") },
        text = {
            Column {
                Text("The saved fingerprint no longer matches this server.")
                Text("Host: ${mismatch.hostname}:${mismatch.port}")
                Text("Old: ${mismatch.oldFingerprint}")
                Text("New: ${mismatch.newFingerprint}")
                Text(
                    "Verify the server out of band, then remove the old entry in Trusted Host Keys before reconnecting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenTrustedHostKeys) {
                Text("Open Trusted Host Keys")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
