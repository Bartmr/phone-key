package com.bartmr.phonekey.keystore

import android.security.keystore.KeyProperties

val PURPOSE_DISPLAY_NAMES = mapOf(
    KeyProperties.PURPOSE_ENCRYPT to "Encrypt",
    KeyProperties.PURPOSE_DECRYPT to "Decrypt",
    KeyProperties.PURPOSE_SIGN to "Sign",
    KeyProperties.PURPOSE_VERIFY to "Verify",
    KeyProperties.PURPOSE_WRAP_KEY to "Wrap Key",
)

val DIGEST_DISPLAY_NAMES = mapOf(
    KeyProperties.DIGEST_SHA256 to "SHA-256",
    KeyProperties.DIGEST_SHA384 to "SHA-384",
    KeyProperties.DIGEST_SHA512 to "SHA-512",
    KeyProperties.DIGEST_SHA1 to "SHA-1",
)

val ENCRYPTION_PADDING_DISPLAY_NAMES = mapOf(
    KeyProperties.ENCRYPTION_PADDING_RSA_OAEP to "OAEP",
    KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1 to "PKCS#1",
    KeyProperties.ENCRYPTION_PADDING_NONE to "None",
    KeyProperties.ENCRYPTION_PADDING_PKCS7 to "PKCS#7",
)

val SIGNATURE_PADDING_DISPLAY_NAMES = mapOf(
    KeyProperties.SIGNATURE_PADDING_RSA_PKCS1 to "PKCS#1",
    KeyProperties.SIGNATURE_PADDING_RSA_PSS to "PSS",
)

val BLOCK_MODE_DISPLAY_NAMES = mapOf(
    KeyProperties.BLOCK_MODE_CBC to "CBC",
    KeyProperties.BLOCK_MODE_CTR to "CTR",
    KeyProperties.BLOCK_MODE_ECB to "ECB",
    KeyProperties.BLOCK_MODE_GCM to "GCM",
)

val ORIGIN_DISPLAY_NAMES = mapOf(
    KeyProperties.ORIGIN_GENERATED to "Generated",
    KeyProperties.ORIGIN_IMPORTED to "Imported",
    KeyProperties.ORIGIN_UNKNOWN to "Unknown",
    KeyProperties.ORIGIN_SECURELY_IMPORTED to "Securely Imported",
)

data class KeyInfo(
    val alias: String,
    val algorithm: String,
    val keySize: Int,
    val purposes: Int,
    val digests: List<String>,
    val encryptionPaddings: List<String>,
    val signaturePaddings: List<String>,
    val blockModes: List<String>,
    val isInsideSecureHardware: Boolean,
    val origin: Int,
    val userAuthenticationRequired: Boolean,
    val userAuthenticationValidityDurationSeconds: Int,
    val isSymmetric: Boolean,
)

fun Int.purposesToDisplayNames(): List<String> =
    PURPOSE_DISPLAY_NAMES.filterKeys { (this and it) != 0 }.values.toList()

fun List<String>.toDisplayNames(map: Map<String, String>): List<String> =
    mapNotNull { map[it] }
