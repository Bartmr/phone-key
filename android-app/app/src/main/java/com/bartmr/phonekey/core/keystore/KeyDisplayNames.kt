package com.bartmr.phonekey.core.keystore

import android.os.Build
import android.security.keystore.KeyProperties

val PURPOSE_DISPLAY_NAMES = buildMap {
    put(KeyProperties.PURPOSE_ENCRYPT, "Encrypt")
    put(KeyProperties.PURPOSE_DECRYPT, "Decrypt")
    put(KeyProperties.PURPOSE_SIGN, "Sign")
    put(KeyProperties.PURPOSE_VERIFY, "Verify")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        put(KeyProperties.PURPOSE_WRAP_KEY, "Wrap Key")
    }
}

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

val ORIGIN_DISPLAY_NAMES = buildMap {
    put(KeyProperties.ORIGIN_GENERATED, "Generated")
    put(KeyProperties.ORIGIN_IMPORTED, "Imported")
    put(KeyProperties.ORIGIN_UNKNOWN, "Unknown")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        put(KeyProperties.ORIGIN_SECURELY_IMPORTED, "Securely Imported")
    }
}

fun Int.purposesToDisplayNames(): List<String> =
    PURPOSE_DISPLAY_NAMES.filterKeys { (this and it) != 0 }.values.toList()

fun List<String>.toDisplayNames(map: Map<String, String>): List<String> =
    mapNotNull { map[it] }