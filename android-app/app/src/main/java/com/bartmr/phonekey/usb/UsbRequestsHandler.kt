package com.bartmr.phonekey.usb

import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.bartmr.phonekey.keystore.KeyStoreRepository
import com.bartmr.phonekey.ssh.SignResult
import com.bartmr.phonekey.ssh.Ssh
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore

@Serializable
sealed class ClientMessage {
    @Serializable
    @SerialName("ssh-request-identities")
    class RequestIdentities : ClientMessage()

    @Serializable
    @SerialName("ssh-sign")
    data class SshSign(
        val keyAlias: String,
        val data: String,
    ) : ClientMessage()

    @Serializable
    @SerialName("echo")
    data class Echo(
        val payload: String,
    ) : ClientMessage()
}

@Serializable
data class IdentityResponse(val alias: String, val publicKeyBase64: String)

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun rememberUsbRequestsHandler(
    keyStoreRepository: KeyStoreRepository,
    activity: FragmentActivity,
): UsbAccessoryManager {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val usbManager = remember { UsbAccessoryManager(context) }
    val ssh = remember { Ssh(activity) }

    DisposableEffect(usbManager) {
        usbManager.register()
        onDispose { usbManager.unregister() }
    }

    LaunchedEffect(usbManager) {
        usbManager.handleIntent(activity.intent)
    }

    DisposableEffect(usbManager) {
        usbManager.onDataReceived = { data ->
            val text = String(data, Charsets.UTF_8)
            when (val message = json.decodeFromString<ClientMessage>(text)) {
                is ClientMessage.Echo -> {
                    usbManager.sendToClient(message.payload.toByteArray(Charsets.UTF_8))
                }
                is ClientMessage.RequestIdentities -> {
                    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                    val identities = keyStoreRepository.listAliases().mapNotNull { alias ->
                        val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                            ?: return@mapNotNull null
                        val publicKey = ssh.getPublicKey(entry)
                        val publicKeyBase64 = Base64.encodeToString(
                            publicKey.toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP,
                        )
                        IdentityResponse(alias, publicKeyBase64)
                    }
                    val response = json.encodeToString(
                        ListSerializer(IdentityResponse.serializer()),
                        identities,
                    )
                    usbManager.sendToClient(response.toByteArray(Charsets.UTF_8))
                }
                is ClientMessage.SshSign -> {
                    val keyInfo = keyStoreRepository.getKeyInfo(message.keyAlias)
                    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                    val entry = ks.getEntry(message.keyAlias, null) as? KeyStore.PrivateKeyEntry
                    if (entry == null) {
                        usbManager.sendToClient(ByteArray(0))
                    } else {
                        val dataToSign = Base64.decode(message.data, Base64.DEFAULT)
                        coroutineScope.launch {
                            when (val result = ssh.sign(keyInfo, entry, dataToSign)) {
                                is SignResult.Success ->
                                    usbManager.sendToClient(result.rawSignature)
                                is SignResult.Error ->
                                    usbManager.sendToClient(ByteArray(0))
                            }
                        }
                    }
                }
            }
        }
        onDispose {
            usbManager.onDataReceived = null
        }
    }

    return usbManager
}
