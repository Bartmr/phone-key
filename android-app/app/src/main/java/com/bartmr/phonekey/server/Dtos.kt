package com.bartmr.phonekey.server

import kotlinx.serialization.Serializable

@Serializable
data class ListKeysResponse(
    val keys: List<KeyEntryResponse>,
)

@Serializable
data class KeyEntryResponse(
    val id: String,
    val name: String,
    val algorithm: String,
    val keySize: Int,
    val purposes: Int,
    val digests: List<String>,
    val signaturePaddings: List<String>,
    val encryptionPaddings: List<String>,
    val blockModes: List<String>,
    val publicKeyBase64: String?,
)

@Serializable
data class ErrorResponse(
    val error: String,
)
