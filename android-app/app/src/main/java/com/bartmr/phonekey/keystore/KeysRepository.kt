package com.bartmr.phonekey.keystore

import android.content.Context
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.crypto.KeyGenerator
import kotlinx.serialization.json.Json

class KeysRepository(context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
    private val keysMetadata = context.getSharedPreferences("keys", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllStoredKeys(): List<StoredKey> =
        keysMetadata.all.keys.map { getStoredKey(it) }

    fun keyExists(alias: String): Boolean =
        keysMetadata.contains(alias)

    fun getPrivateKeyEntry(alias: String): KeyStore.PrivateKeyEntry? =
        keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry

    fun getPublicKeyDer(alias: String): ByteArray? =
        getPrivateKeyEntry(alias)?.certificate?.publicKey?.encoded

    fun getStoredKey(alias: String): StoredKey {
        val raw = keysMetadata.getString(alias, null)
            ?: throw IllegalArgumentException("Key '$alias' not found")
        return json.decodeFromString(StoredKey.serializer(), raw)
    }

    fun deleteKey(alias: String) {
        keyStore.deleteEntry(alias)
        keysMetadata.edit().remove(alias).apply()
    }

    fun generateKey(newKeySpec: NewKeySpec) {
        val isSymmetric = newKeySpec.algorithm == KeyProperties.KEY_ALGORITHM_AES ||
                newKeySpec.algorithm == KeyProperties.KEY_ALGORITHM_HMAC_SHA256

        if (isSymmetric) {
            val keyGenerator = KeyGenerator.getInstance(newKeySpec.algorithm, "AndroidKeyStore")
            keyGenerator.init(newKeySpec.toAlgorithmParameterSpec())
            keyGenerator.generateKey()
        } else {
            val keyPairGenerator = KeyPairGenerator.getInstance(newKeySpec.algorithm, "AndroidKeyStore")
            keyPairGenerator.initialize(newKeySpec.toAlgorithmParameterSpec())
            keyPairGenerator.generateKeyPair()
        }

        val storedKey = StoredKey(
            alias = newKeySpec.alias,
            algorithm = newKeySpec.algorithm,
            keySize = newKeySpec.keySize,
            purposes = newKeySpec.purposes,
            digests = newKeySpec.digests,
            encryptionPaddings = newKeySpec.encryptionPaddings,
            signaturePaddings = newKeySpec.signaturePaddings,
            blockModes = newKeySpec.blockModes,
            userAuthenticationRequired = newKeySpec.userAuthenticationRequired,
            userAuthenticationValidityDurationSeconds = newKeySpec.userAuthenticationValidityDurationSeconds,
        )
        keysMetadata.edit().putString(storedKey.alias, json.encodeToString(StoredKey.serializer(), storedKey)).apply()
    }

}
