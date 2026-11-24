package com.bartmr.phonekey.keys

import kotlinx.serialization.Serializable

@Serializable
data class StoredKey(
    val id: String,
    val name: String,
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
