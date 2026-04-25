package com.nodex.client.core.security

interface CredentialVault {
    fun savePassword(serverId: String, password: String)
    fun getPassword(serverId: String): String?
    fun saveSudoPassword(serverId: String, password: String)
    fun getSudoPassword(serverId: String): String?
    fun clearSudoPassword(serverId: String)
    fun clearPassword(serverId: String)
    fun savePrivateKey(keyId: String, keyText: String)
    fun getPrivateKey(keyId: String): String?
    fun clearPrivateKey(keyId: String)
    fun savePrivateKeyPassphrase(keyId: String, passphrase: String?)
    fun getPrivateKeyPassphrase(keyId: String): String?
}
