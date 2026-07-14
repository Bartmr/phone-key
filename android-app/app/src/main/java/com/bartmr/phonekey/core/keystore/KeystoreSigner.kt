package com.bartmr.phonekey.core.keystore

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import kotlin.coroutines.resume

sealed class SignResult {
    data class Success(val signature: ByteArray) : SignResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            return signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int {
            return signature.contentHashCode()
        }
    }

    data class Error(val code: Int, val message: String) : SignResult()
}

class KeystoreSigner {

    fun getPublicKeyBytes(entry: KeyStore.PrivateKeyEntry): ByteArray {
        return entry.certificate.publicKey.encoded
    }

    suspend fun sign(
        privateKey: PrivateKey,
        data: ByteArray,
        algorithm: String,
        keyInfo: KeyInfo,
        activity: FragmentActivity,
    ): SignResult {
        if (!keyInfo.userAuthenticationRequired) {
            val signature = Signature.getInstance(algorithm)
            signature.initSign(privateKey)
            signature.update(data)
            return SignResult.Success(signature.sign())
        }

        val signature = Signature.getInstance(algorithm)
        signature.initSign(privateKey)
        val crypto = BiometricPrompt.CryptoObject(signature)

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        val authenticatedSignature = result.cryptoObject?.signature
                            ?: throw IllegalStateException(
                                "CryptoObject signature is null after successful authentication"
                            )

                        authenticatedSignature.update(data)
                        continuation.resume(
                            SignResult.Success(authenticatedSignature.sign())
                        )
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        continuation.resume(
                            SignResult.Error(errorCode, errString.toString())
                        )
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sign with key")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            continuation.invokeOnCancellation {
                biometricPrompt.cancelAuthentication()
            }

            biometricPrompt.authenticate(promptInfo, crypto)
        }
    }

    companion object {
        fun deriveAlgorithm(keyInfo: KeyInfo): String {
            val digest = keyInfo.digests.firstOrNull()
                ?: throw IllegalStateException(
                    "Key '${keyInfo.alias}' has no digests configured"
                )
            val digestSuffix = digest.replace("-", "")
            return "$digestSuffix${"with"}${keyInfo.algorithm}"
        }
    }
}
