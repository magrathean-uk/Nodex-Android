package com.nodex.client.ui.serveredit

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.nodex.client.domain.model.AuthType
import com.nodex.client.domain.model.ServerConfig
import java.util.UUID

data class ServerEditorValidation(
    val hostError: String? = null,
    val portError: String? = null,
    val usernameError: String? = null,
    val authError: String? = null
) {
    val canSave: Boolean
        get() = hostError == null && portError == null && usernameError == null && authError == null
}

data class ServerEditorState(
    val id: String? = null,
    val name: String = "",
    val hostname: String = "",
    val portText: String = "22",
    val username: String = "root",
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val hasSavedPassword: Boolean = false,
    val selectedKeyId: String? = null,
    val selectedKeyLabel: String = "",
    val keyPassphrase: String = "",
    val distro: String = "Unknown",
    val pollIntervalSeconds: Int = 2,
    val alertLookbackMinutes: Int = 60,
    val publicIPEnabled: Boolean = true,
    val isFavorite: Boolean = false,
    val autoConnect: Boolean = false
) {
    val normalizedName: String?
        get() = name.trim().ifBlank { null }

    val normalizedHostname: String
        get() = hostname.trim()

    val normalizedUsername: String
        get() = username.trim()

    val normalizedKeyPassphrase: String?
        get() = keyPassphrase.trim().ifBlank { null }

    fun validation(): ServerEditorValidation {
        val port = portText.trim().toIntOrNull()
        return ServerEditorValidation(
            hostError = if (normalizedHostname.isBlank()) "Host is required." else null,
            portError = if (port == null || port !in 1..65535) "Port must be between 1 and 65535." else null,
            usernameError = if (normalizedUsername.isBlank()) "Username is required." else null,
            authError = when (authType) {
                AuthType.PASSWORD -> if (password.isBlank() && !hasSavedPassword) "Password is required." else null
                AuthType.KEY_DATA -> if (selectedKeyId.isNullOrBlank()) "Select or import an SSH key." else null
                AuthType.KEY_FILE -> "File-based keys are not supported here."
                AuthType.NONE -> null
            }
        )
    }

    fun toServerConfig(id: String = this.id ?: UUID.randomUUID().toString()): ServerConfig {
        val port = portText.trim().toIntOrNull() ?: 22
        return ServerConfig(
            id = id,
            name = normalizedName ?: normalizedHostname,
            hostname = normalizedHostname,
            port = port,
            username = normalizedUsername,
            authType = authType,
            keyDataId = selectedKeyId,
            distro = distro.trim().ifBlank { "Unknown" },
            pollIntervalSeconds = pollIntervalSeconds,
            alertLookbackMinutes = alertLookbackMinutes,
            publicIPEnabled = publicIPEnabled,
            isFavorite = isFavorite,
            autoConnect = autoConnect
        )
    }

    companion object {
        fun fromServerConfig(
            server: ServerConfig,
            hasSavedPassword: Boolean = false,
            selectedKeyLabel: String = ""
        ): ServerEditorState {
            return ServerEditorState(
                id = server.id,
                name = server.name,
                hostname = server.hostname,
                portText = server.port.toString(),
                username = server.username,
                authType = server.authType,
                hasSavedPassword = hasSavedPassword,
                selectedKeyId = server.keyDataId,
                selectedKeyLabel = selectedKeyLabel,
                distro = server.distro,
                pollIntervalSeconds = server.pollIntervalSeconds,
                alertLookbackMinutes = server.alertLookbackMinutes,
                publicIPEnabled = server.publicIPEnabled,
                isFavorite = server.isFavorite,
                autoConnect = server.autoConnect
            )
        }

        val Saver: Saver<ServerEditorState, Any> = listSaver(
            save = {
                listOf(
                    it.id,
                    it.name,
                    it.hostname,
                    it.portText,
                    it.username,
                    it.authType.name,
                    it.password,
                    it.hasSavedPassword,
                    it.selectedKeyId,
                    it.selectedKeyLabel,
                    it.keyPassphrase,
                    it.distro,
                    it.pollIntervalSeconds,
                    it.alertLookbackMinutes,
                    it.publicIPEnabled,
                    it.isFavorite,
                    it.autoConnect
                )
            },
            restore = { saved ->
                ServerEditorState(
                    id = saved[0] as String?,
                    name = saved[1] as String,
                    hostname = saved[2] as String,
                    portText = saved[3] as String,
                    username = saved[4] as String,
                    authType = AuthType.valueOf(saved[5] as String),
                    password = saved[6] as String,
                    hasSavedPassword = saved[7] as Boolean,
                    selectedKeyId = saved[8] as String?,
                    selectedKeyLabel = saved[9] as String,
                    keyPassphrase = saved[10] as String,
                    distro = saved[11] as String,
                    pollIntervalSeconds = saved[12] as Int,
                    alertLookbackMinutes = saved[13] as Int,
                    publicIPEnabled = saved[14] as Boolean,
                    isFavorite = saved[15] as Boolean,
                    autoConnect = saved[16] as Boolean
                )
            }
        )
    }
}
