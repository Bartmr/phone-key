package com.bartmr.phonekey.core.keystore

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
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun listAliases(): List<String> =
        keyStore.aliases().asSequence().toList()

    fun keyExists(alias: String): Boolean =
        keyStore.containsAlias(alias)

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
        prefs.edit().remove(metaKey(alias)).apply()
    }

    fun generateKey(info: KeyInfo) {
        val isSymmetric = info.algorithm == KeyProperties.KEY_ALGORITHM_AES ||
                info.algorithm == KeyProperties.KEY_ALGORITHM_HMAC_SHA256

        if (isSymmetric) {
            val keyGenerator = KeyGenerator.getInstance(info.algorithm, "AndroidKeyStore")
            keyGenerator.init(info.toAlgorithmParameterSpec())
            keyGenerator.generateKey()
        } else {
            val keyPairGenerator = KeyPairGenerator.getInstance(info.algorithm, "AndroidKeyStore")
            keyPairGenerator.initialize(info.toAlgorithmParameterSpec())
            keyPairGenerator.generateKeyPair()
        }

        prefs.edit().putString(metaKey(info.alias), json.encodeToString(KeyInfo.serializer(), info)).apply()
    }

    private fun loadMeta(alias: String): KeyInfo? {
        val raw = prefs.getString(metaKey(alias), null) ?: return null
        return json.decodeFromString(KeyInfo.serializer(), raw)
    }

    private fun metaKey(alias: String) = "key_meta_$alias"

    companion object {
        private const val PREFS_NAME = "phonekey_key_metadata"
    }
}
