package com.nodex.client.core.network.ssh

/**
 * Describes a remote command execution request.
 *
 * Passwords and other sensitive data should be passed via [stdin] rather than embedded into the
 * remote shell command string.
 */
data class SshCommandRequest(
    val command: String,
    val stdin: String? = null,
    val maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS
) {
    init {
        require(command.isNotBlank()) { "Command must not be blank" }
        require(maxOutputChars > 0) { "maxOutputChars must be greater than 0" }
    }

    companion object {
        const val DEFAULT_MAX_OUTPUT_CHARS: Int = 500_000

        fun plain(command: String, maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS): SshCommandRequest {
            return SshCommandRequest(
                command = command.trim(),
                maxOutputChars = maxOutputChars
            )
        }

        fun sudo(
            command: String,
            password: String,
            maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS
        ): SshCommandRequest {
            require(password.isNotBlank()) { "Sudo password required" }
            return SshCommandRequest(
                command = "sudo -S -p '' ${command.trim()}",
                stdin = password.ensureTrailingNewline(),
                maxOutputChars = maxOutputChars
            )
        }
    }
}

private fun String.ensureTrailingNewline(): String =
    if (endsWith("\n")) this else "$this\n"
