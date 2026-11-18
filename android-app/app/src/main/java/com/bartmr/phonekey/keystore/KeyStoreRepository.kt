package com.bartmr.phonekey.keystore

import android.content.Context
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import javax.crypto.KeyGenerator
import kotlinx.serialization.json.Json

class KeyStoreRepository(context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
    private val keysMetadata = context.getSharedPreferences("keys", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun listAliases(): List<String> =
        keyStore.aliases().asSequence().toList()

    fun keyExists(alias: String): Boolean =
        keyStore.containsAlias(alias)

    fun getPrivateKeyEntry(alias: String): KeyStore.PrivateKeyEntry? =
        keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry

    fun getPublicKeyDer(alias: String): ByteArray? =
        getPrivateKeyEntry(alias)?.certificate?.publicKey?.encoded

    fun getKeyInfo(alias: String): KeyInfo {
        val entry = keyStore.getEntry(alias, null)
            ?: throw IllegalArgumentException("Key '$alias' not found in Android Keystore")

        val storedMeta = loadMeta(alias)

        return when (entry) {
            is KeyStore.PrivateKeyEntry -> {
                val cert = entry.certificate
                val publicKey = cert.publicKey
                val algorithm = publicKey.algorithm
                val keySize = when (publicKey) {
                    is ECPublicKey -> publicKey.params.order.bitLength()
                    is RSAPublicKey -> publicKey.modulus.bitLength()
                    else -> 0
                }

                KeyInfo(
                    alias = alias,
                    algorithm = algorithm,
                    keySize = keySize,
                    purposes = storedMeta?.purposes ?: 0,
                    digests = storedMeta?.digests ?: emptyList(),
                    encryptionPaddings = storedMeta?.encryptionPaddings ?: emptyList(),
                    signaturePaddings = storedMeta?.signaturePaddings ?: emptyList(),
                    blockModes = storedMeta?.blockModes ?: emptyList(),
                    userAuthenticationRequired = storedMeta?.userAuthenticationRequired ?: false,
                    userAuthenticationValidityDurationSeconds = storedMeta?.userAuthenticationValidityDurationSeconds ?: 0,
                )
            }
            is KeyStore.SecretKeyEntry -> {
                val secretKey = entry.secretKey
                KeyInfo(
                    alias = alias,
                    algorithm = secretKey.algorithm,
                    keySize = storedMeta?.keySize ?: 0,
                    purposes = storedMeta?.purposes ?: 0,
                    digests = storedMeta?.digests ?: emptyList(),
                    encryptionPaddings = storedMeta?.encryptionPaddings ?: emptyList(),
                    signaturePaddings = storedMeta?.signaturePaddings ?: emptyList(),
                    blockModes = storedMeta?.blockModes ?: emptyList(),
                    userAuthenticationRequired = storedMeta?.userAuthenticationRequired ?: false,
                    userAuthenticationValidityDurationSeconds = storedMeta?.userAuthenticationValidityDurationSeconds ?: 0,
                )
            }
            else -> throw IllegalArgumentException(
                "Unsupported key entry type: ${entry::class.simpleName}"
            )
        }
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

    private fun loadMeta(alias: String): KeyInfo? {
        val raw = keysMetadata.getString(alias, null) ?: return null
        return json.decodeFromString(KeyInfo.serializer(), raw)
    }

}
