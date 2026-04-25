package com.nodex.client.core.network.ssh

import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun formatSshError(error: Throwable): String {
    return when (error) {
        is HostKeyVerificationRequiredException -> {
            "Host key verification required. Please confirm the server fingerprint."
        }
        is UnknownHostException -> {
            "Unable to resolve hostname. Check the address or your DNS settings."
        }
        is NoRouteToHostException -> {
            "No route to host. The server may be down or unreachable from this network."
        }
        is ConnectException -> {
            val msg = error.message?.lowercase() ?: ""
            when {
                msg.contains("refused") -> "Connection refused. Check the SSH port or if the SSH service is running."
                msg.contains("timed out") || msg.contains("timeout") -> "Connection timed out. The server may be behind a firewall."
                else -> "Unable to connect. Check the host, port, and network connection."
            }
        }
        is SocketTimeoutException -> {
            "Connection timed out. The server didn't respond in time."
        }
        is SocketException -> {
            val msg = error.message?.lowercase() ?: ""
            when {
                msg.contains("reset") -> "Connection reset by the server. It may have closed the connection."
                msg.contains("broken pipe") -> "Connection lost. The server closed the connection unexpectedly."
                else -> "Network error: ${error.message}"
            }
        }
        is UserAuthException -> {
            "Authentication failed. Check your username and password or key file."
        }
        is SecurityException -> {
            val msg = error.message.orEmpty()
            when {
                msg.contains("Host key mismatch", ignoreCase = true) ->
                    "Host key mismatch. Verify the server and remove the saved fingerprint in Settings > Trusted Host Keys if it has changed."
                msg.isNotBlank() -> msg
                else -> "Host key verification failed."
            }
        }
        is IOException -> {
            val msg = error.message?.lowercase() ?: ""
            when {
                msg.contains("timeout") -> "Operation timed out. The server may be overloaded."
                msg.contains("disconnect") -> "Disconnected from server."
                msg.contains("backoff") || msg.contains("backing off") ->
                    "Server temporarily unreachable. Will retry automatically."
                msg.contains("end of stream") || msg.contains("eof") ->
                    "Connection closed unexpectedly. The server may have restarted."
                else -> error.message ?: "Connection failed."
            }
        }
        else -> error.message ?: "An unexpected error occurred."
    }
}
