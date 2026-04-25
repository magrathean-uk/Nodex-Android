package com.nodex.client.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialsStore @Inject constructor(
    @ApplicationContext context: Context
) : CredentialVault {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_credentials",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun savePassword(serverId: String, password: String) {
        prefs.edit().putString(passwordKey(serverId), password).apply()
    }

    override fun getPassword(serverId: String): String? = prefs.getString(passwordKey(serverId), null)

    override fun saveSudoPassword(serverId: String, password: String) {
        prefs.edit().putString(sudoKey(serverId), password).apply()
    }

    override fun getSudoPassword(serverId: String): String? = prefs.getString(sudoKey(serverId), null)

    override fun clearSudoPassword(serverId: String) {
        prefs.edit().remove(sudoKey(serverId)).apply()
    }

    override fun clearPassword(serverId: String) {
        prefs.edit().remove(passwordKey(serverId)).apply()
    }

    override fun savePrivateKey(keyId: String, keyText: String) {
        prefs.edit().putString(privateKeyKey(keyId), keyText).apply()
    }

    override fun getPrivateKey(keyId: String): String? = prefs.getString(privateKeyKey(keyId), null)

    override fun clearPrivateKey(keyId: String) {
        prefs.edit()
            .remove(privateKeyKey(keyId))
            .remove(privateKeyPassphraseKey(keyId))
            .apply()
    }

    override fun savePrivateKeyPassphrase(keyId: String, passphrase: String?) {
        if (passphrase.isNullOrBlank()) {
            prefs.edit().remove(privateKeyPassphraseKey(keyId)).apply()
        } else {
            prefs.edit().putString(privateKeyPassphraseKey(keyId), passphrase).apply()
        }
    }

    override fun getPrivateKeyPassphrase(keyId: String): String? =
        prefs.getString(privateKeyPassphraseKey(keyId), null)

    private fun passwordKey(serverId: String): String = "pw_${serverId}"
    private fun sudoKey(serverId: String): String = "sudo_${serverId}"
    private fun privateKeyKey(keyId: String): String = "ssh_key_${keyId}"
    private fun privateKeyPassphraseKey(keyId: String): String = "ssh_key_passphrase_${keyId}"
}
