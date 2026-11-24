package com.bartmr.phonekey.keys

import android.content.Context
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlinx.serialization.json.Json

class KeysRepository(
    context: Context,
    private val keyStore: KeyStore,
) {
    private val keysMetadata = context.getSharedPreferences("keys", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllStoredKeys(): List<StoredKey> =
        keysMetadata.all.keys.map { getStoredKey(it) }

    fun getPrivateKeyEntry(id: String): KeyStore.PrivateKeyEntry? =
        keyStore.getEntry(id, null) as? KeyStore.PrivateKeyEntry

    fun getPublicKeyDer(id: String): ByteArray? =
        getPrivateKeyEntry(id)?.certificate?.publicKey?.encoded

    fun getStoredKey(id: String): StoredKey {
        val raw = keysMetadata.getString(id, null)
            ?: throw IllegalArgumentException("Key '$id' not found")
        return json.decodeFromString(StoredKey.serializer(), raw)
    }

    fun deleteKey(id: String) {
        keyStore.deleteEntry(id)
        keysMetadata.edit().remove(id).apply()
    }

    fun generateKey(newKeySpec: NewKeySpec) {
        val id = UUID.randomUUID().toString()
        val isSymmetric = newKeySpec.algorithm == KeyProperties.KEY_ALGORITHM_AES ||
                newKeySpec.algorithm == KeyProperties.KEY_ALGORITHM_HMAC_SHA256

        if (isSymmetric) {
            val keyGenerator = KeyGenerator.getInstance(newKeySpec.algorithm, "AndroidKeyStore")
            keyGenerator.init(newKeySpec.toAlgorithmParameterSpec(id))
            keyGenerator.generateKey()
        } else {
            val keyPairGenerator = KeyPairGenerator.getInstance(newKeySpec.algorithm, "AndroidKeyStore")
            keyPairGenerator.initialize(newKeySpec.toAlgorithmParameterSpec(id))
            keyPairGenerator.generateKeyPair()
        }

        val storedKey = StoredKey(
            id = id,
            name = newKeySpec.name,
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
        keysMetadata.edit().putString(storedKey.id, json.encodeToString(StoredKey.serializer(), storedKey)).apply()
    }

}
