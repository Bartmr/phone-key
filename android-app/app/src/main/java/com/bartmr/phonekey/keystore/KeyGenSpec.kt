package com.bartmr.phonekey.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

import kotlinx.serialization.Serializable

@Serializable
data class KeyGenSpec(
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
    val authType: Int,
)

fun KeyGenSpec.toAndroidSpec(): KeyGenParameterSpec {
    val builder = KeyGenParameterSpec.Builder(alias, purposes)
        .setKeySize(keySize)

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
                authType,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(
                userAuthenticationValidityDurationSeconds,
            )
        }
    }

    return builder.build()
}
