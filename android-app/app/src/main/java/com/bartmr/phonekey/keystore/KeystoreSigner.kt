package com.bartmr.phonekey.keystore

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
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

    data object Denied : SignResult()

    data object KeyInvalidated : SignResult()

    data class Error(val code: Int, val message: String) : SignResult()
}

class KeystoreSigner(
    private val userPresence: UserPresence,
) {

    fun getPublicKeyBytes(entry: KeyStore.PrivateKeyEntry): ByteArray {
        return entry.certificate.publicKey.encoded
    }

    suspend fun sign(
        privateKey: PrivateKey,
        data: ByteArray,
        algorithm: String,
        storedKey: StoredKey,
        activity: FragmentActivity,
    ): SignResult {
        if (!storedKey.userAuthenticationRequired) {
            if (!userPresence.requestApproval(activity, storedKey)) {
                return SignResult.Denied
            }

            val signature = Signature.getInstance(algorithm)
            signature.initSign(privateKey)
            signature.update(data)
            return SignResult.Success(signature.sign())
        }

        val signature = Signature.getInstance(algorithm)
        try {
            signature.initSign(privateKey)
        } catch (_: KeyPermanentlyInvalidatedException) {
            return SignResult.KeyInvalidated
        }
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

                        val signatureBytes = try {
                            authenticatedSignature.update(data)
                            authenticatedSignature.sign()
                        } catch (_: KeyPermanentlyInvalidatedException) {
                            continuation.resume(SignResult.KeyInvalidated)
                            return
                        }

                        continuation.resume(SignResult.Success(signatureBytes))
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
                .setTitle("Sign with key \"${storedKey.name}\"")
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
        fun deriveAlgorithm(storedKey: StoredKey): String {
            return when (storedKey.algorithm) {
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256 -> storedKey.algorithm
                KeyProperties.KEY_ALGORITHM_EC, KeyProperties.KEY_ALGORITHM_RSA -> {
                    val digest = storedKey.digests.firstOrNull() ?: KeyProperties.DIGEST_SHA256
                    val digestSuffix = digest.replace("-", "")
                    val keyAlgorithm = when (storedKey.algorithm) {
                        KeyProperties.KEY_ALGORITHM_EC -> "ECDSA"
                        else -> "RSA"
                    }
                    "${digestSuffix}with$keyAlgorithm"
                }
                else -> storedKey.algorithm
            }
        }
    }
}
