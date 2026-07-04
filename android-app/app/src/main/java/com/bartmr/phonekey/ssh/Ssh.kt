package com.bartmr.phonekey.ssh

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.crypto.util.PublicKeyFactory
import org.bouncycastle.util.BigIntegers
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import kotlin.coroutines.resume

sealed class SignResult {
    data class Success(val rawSignature: ByteArray) : SignResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            return rawSignature.contentEquals(other.rawSignature)
        }

        override fun hashCode(): Int {
            return rawSignature.contentHashCode()
        }
    }

    data class Error(val code: Int, val message: String) : SignResult()
}

class Ssh(private val activity: FragmentActivity) {

    fun getPublicKey(key: KeyStore.PrivateKeyEntry): String {
        val publicKey = key.certificate.publicKey
        require(publicKey is ECPublicKey) {
            "Expected ECPublicKey, got ${publicKey::class.simpleName}"
        }

        return toSshFormat(publicKey)
    }

    suspend fun sign(key: KeyStore.PrivateKeyEntry, data: ByteArray): SignResult {
        val privateKey = key.privateKey

        val signature = Signature.getInstance("SHA256withECDSA")
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
                            ?: throw IllegalStateException("CryptoObject signature is null after successful authentication")

                        authenticatedSignature.update(data)
                        val derSignature = authenticatedSignature.sign()
                        continuation.resume(
                            SignResult.Success(derToRaw(derSignature))
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
                .setTitle("Sign with SSH key")
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

    private fun toSshFormat(publicKey: ECPublicKey): String {
        val keyParams = PublicKeyFactory.createKey(publicKey.encoded)
        val encoded = OpenSSHPublicKeyUtil.encodePublicKey(keyParams)
        return String(encoded)
    }

    private fun derToRaw(der: ByteArray): ByteArray {
        val obj = ASN1InputStream(der).use { it.readObject() }
        require(obj is ASN1Sequence) {
            "Expected ASN1Sequence, got ${obj::class.simpleName}"
        }

        val rObj = obj.getObjectAt(0)
        require(rObj is ASN1Integer) {
            "Expected ASN1Integer for r, got ${rObj::class.simpleName}"
        }
        val sObj = obj.getObjectAt(1)
        require(sObj is ASN1Integer) {
            "Expected ASN1Integer for s, got ${sObj::class.simpleName}"
        }

        return BigIntegers.asUnsignedByteArray(32, rObj.positiveValue) +
                BigIntegers.asUnsignedByteArray(32, sObj.positiveValue)
    }
}
