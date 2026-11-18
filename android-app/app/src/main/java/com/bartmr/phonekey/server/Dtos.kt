package com.bartmr.phonekey.server

import kotlinx.serialization.Serializable

@Serializable
data class ListKeysResponse(
    val keys: List<KeyEntryResponse>,
)

@Serializable
data class KeyEntryResponse(
    val id: String,
    val alias: String,
    val algorithm: String,
    val keySize: Int,
    val purposes: Int,
    val digests: List<String>,
    val signaturePaddings: List<String>,
    val encryptionPaddings: List<String>,
    val blockModes: List<String>,
    val userAuthenticationRequired: Boolean,
    val userAuthenticationValidityDurationSeconds: Int,
    val publicKeyBase64: String?,
)

@Serializable
data class SignRequest(
    val keyId: String,
    val data: String,
)

@Serializable
data class SignResponse(
    val signature: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)
