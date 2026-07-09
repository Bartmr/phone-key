package com.bartmr.phonekey.core.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import java.security.spec.AlgorithmParameterSpec

@Serializable
data class KeyInfo(
    val alias: String,
    val algorithm: String,
    val keySize: Int,
    val purposes: Int,
    val digests: List<String>,
    val encryptionPaddings: List<String>,
    val signaturePaddings: List<String>,
    val blockModes: List<String>,
    val userAuthenticationRequired: Boolean,
    val userAuthenticationValidityDurationSeconds: Int,
)

@Suppress("WrongConstant", "DEPRECATION")
fun KeyInfo.toAlgorithmParameterSpec(): AlgorithmParameterSpec {
    val builder = KeyGenParameterSpec.Builder(alias, purposes)
        .setKeySize(keySize)

    if (userAuthenticationRequired) {
        builder.setUserAuthenticationRequired(true)
    }

    if (digests.isNotEmpty()) {
        builder.setDigests(*digests.toTypedArray())
    }
    if (encryptionPaddings.isNotEmpty()) {
        builder.setEncryptionPaddings(*encryptionPaddings.toTypedArray())
    }
    if (signaturePaddings.isNotEmpty()) {
        builder.setSignaturePaddings(*signaturePaddings.toTypedArray())
    }
    if (blockModes.isNotEmpty()) {
        builder.setBlockModes(*blockModes.toTypedArray())
    }

    if (userAuthenticationRequired) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                userAuthenticationValidityDurationSeconds,
                KeyProperties.AUTH_BIOMETRIC_STRONG
                    or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            val timeout = if (userAuthenticationValidityDurationSeconds == 0)
                -1 else userAuthenticationValidityDurationSeconds
            builder.setUserAuthenticationValidityDurationSeconds(timeout)
        }
    }

    return builder.build()
}