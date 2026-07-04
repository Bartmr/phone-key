package com.bartmr.phonekey.keystore

import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import javax.crypto.KeyGenerator

class KeyStoreRepository {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    fun listAliases(): List<String> =
        keyStore.aliases().asSequence().toList()

    fun keyExists(alias: String): Boolean =
        keyStore.containsAlias(alias)

    fun getKeyInfo(alias: String): KeyInfo {
        val entry = keyStore.getEntry(alias, null)
            ?: throw IllegalArgumentException("Key '$alias' not found in Android Keystore")

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
                val keystoreInfo = entry.privateKey as android.security.keystore.KeyInfo

                KeyInfo(
                    alias = alias,
                    algorithm = algorithm,
                    keySize = keySize,
                    purposes = keystoreInfo.purposes,
                    digests = keystoreInfo.digests.toList(),
                    encryptionPaddings = keystoreInfo.encryptionPaddings.toList(),
                    signaturePaddings = keystoreInfo.signaturePaddings.toList(),
                    blockModes = keystoreInfo.blockModes.toList(),
                    userAuthenticationRequired = keystoreInfo.isUserAuthenticationRequired,
                    userAuthenticationValidityDurationSeconds =
                        keystoreInfo.userAuthenticationValidityDurationSeconds.let {
                            if (it == -1) 0 else it
                        },
                )
            }
            is KeyStore.SecretKeyEntry -> {
                val keystoreInfo = entry.secretKey as android.security.keystore.KeyInfo

                KeyInfo(
                    alias = alias,
                    algorithm = entry.secretKey.algorithm,
                    keySize = keystoreInfo.keySize,
                    purposes = keystoreInfo.purposes,
                    digests = keystoreInfo.digests.toList(),
                    encryptionPaddings = keystoreInfo.encryptionPaddings.toList(),
                    signaturePaddings = keystoreInfo.signaturePaddings.toList(),
                    blockModes = keystoreInfo.blockModes.toList(),
                    userAuthenticationRequired = keystoreInfo.isUserAuthenticationRequired,
                    userAuthenticationValidityDurationSeconds =
                        keystoreInfo.userAuthenticationValidityDurationSeconds.let {
                            if (it == -1) 0 else it
                        },
                )
            }
            else -> throw IllegalArgumentException(
                "Unsupported key entry type: ${entry::class.simpleName}"
            )
        }
    }

    fun deleteKey(alias: String) {
        keyStore.deleteEntry(alias)
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
    }
}
