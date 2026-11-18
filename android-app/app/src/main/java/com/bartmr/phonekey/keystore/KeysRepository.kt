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

    fun getAllKeyInfos(): List<KeyInfo> =
        keysMetadata.all.keys.map { getKeyInfo(it) }

    fun keyExists(alias: String): Boolean =
        keysMetadata.contains(alias)

    fun getPrivateKeyEntry(alias: String): KeyStore.PrivateKeyEntry? =
        keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry

    fun getPublicKeyDer(alias: String): ByteArray? =
        getPrivateKeyEntry(alias)?.certificate?.publicKey?.encoded

    fun getKeyInfo(alias: String): KeyInfo {
        val raw = keysMetadata.getString(alias, null)
            ?: throw IllegalArgumentException("Key '$alias' not found")
        return json.decodeFromString(KeyInfo.serializer(), raw)
    }

    fun deleteKey(alias: String) {
        keyStore.deleteEntry(alias)
        keysMetadata.edit().remove(alias).apply()
    }

    fun generateKey(spec: CreateKeySpec) {
        val isSymmetric = spec.algorithm == KeyProperties.KEY_ALGORITHM_AES ||
                spec.algorithm == KeyProperties.KEY_ALGORITHM_HMAC_SHA256

        if (isSymmetric) {
            val keyGenerator = KeyGenerator.getInstance(spec.algorithm, "AndroidKeyStore")
            keyGenerator.init(spec.toAlgorithmParameterSpec())
            keyGenerator.generateKey()
        } else {
            val keyPairGenerator = KeyPairGenerator.getInstance(spec.algorithm, "AndroidKeyStore")
            keyPairGenerator.initialize(spec.toAlgorithmParameterSpec())
            keyPairGenerator.generateKeyPair()
        }

        val info = KeyInfo(
            alias = spec.alias,
            algorithm = spec.algorithm,
            keySize = spec.keySize,
            purposes = spec.purposes,
            digests = spec.digests,
            encryptionPaddings = spec.encryptionPaddings,
            signaturePaddings = spec.signaturePaddings,
            blockModes = spec.blockModes,
            userAuthenticationRequired = spec.userAuthenticationRequired,
            userAuthenticationValidityDurationSeconds = spec.userAuthenticationValidityDurationSeconds,
        )
        keysMetadata.edit().putString(info.alias, json.encodeToString(KeyInfo.serializer(), info)).apply()
    }

}
