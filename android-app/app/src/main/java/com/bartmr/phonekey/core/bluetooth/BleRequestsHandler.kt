package com.bartmr.phonekey.core.bluetooth

import android.Manifest
import android.os.Build
import android.util.Base64
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.bartmr.phonekey.core.keystore.KeyStoreRepository
import com.bartmr.phonekey.core.keystore.KeystoreSigner
import com.bartmr.phonekey.core.keystore.SignResult
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicReference

@Serializable
sealed class ClientMessage {
    @Serializable
    @SerialName("list-keys")
    class ListKeys : ClientMessage()

    @Serializable
    @SerialName("sign")
    data class Sign(
        val keyAlias: String,
        val data: String,
        val algorithm: String? = null,
    ) : ClientMessage()

    @Serializable
    @SerialName("echo")
    data class Echo(
        val data: String,
    ) : ClientMessage()

}

@Serializable
data class KeyEntryResponse(
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
    val publicKeyBase64: String? = null,
)

sealed class CommandState {
    data object ListingKeys : CommandState()
    data class Signing(val keyAlias: String) : CommandState()
}

@Serializable
data class ErrorResponse(val error: String)

private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

class BleServerState(
    val isBluetoothEnabled: Boolean,
    val permissions: Array<String>,
    val permissionsLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    val permissionsGranted: Boolean,
    val permissionsRequested: Boolean,
)

@Composable
fun rememberBleRequestsHandler(
    keyStoreRepository: KeyStoreRepository,
    activity: FragmentActivity,
): BleServerState {


    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionsRequested by remember { mutableStateOf(false) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsRequested = true
        permissionsGranted = permissions.all { results[it] == true }
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(permissions)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val bleServer = remember { BleServer(context) }
    var bluetoothEnabled by remember { mutableStateOf(bleServer.isAdapterEnabled()) }

    DisposableEffect(bleServer) {
        bleServer.onAdapterStateChanged = { enabled ->
            bluetoothEnabled = enabled
        }
        bleServer.registerAdapterStateReceiver()
        onDispose {
            bleServer.unregisterAdapterStateReceiver()
            bleServer.onAdapterStateChanged = null
        }
    }

    DisposableEffect(bluetoothEnabled, permissionsGranted) {
        if (permissionsGranted) {
            if (bluetoothEnabled) {
                bleServer.startGattServer()
            } else {
                bleServer.stopGattServer()
            }
        }

        onDispose {  }
    }

    val signer = remember { KeystoreSigner() }
    val currentCommand = remember { AtomicReference<CommandState?>(null) }

    DisposableEffect(bleServer) {
        bleServer.onDataReceived = handler@{ device, data ->
            val text = String(data, Charsets.UTF_8)
            val message = json.decodeFromString<ClientMessage>(text)

            val commandState: CommandState? = when (message) {
                is ClientMessage.ListKeys -> CommandState.ListingKeys
                is ClientMessage.Sign -> CommandState.Signing(message.keyAlias)
                is ClientMessage.Echo -> null
            }

            if (commandState != null && !currentCommand.compareAndSet(null, commandState)) {
                val busy = json.encodeToString(ErrorResponse.serializer(), ErrorResponse("busy"))
                bleServer.sendToClient(device, busy.toByteArray(Charsets.UTF_8))
                return@handler
            }

            when (message) {
                is ClientMessage.ListKeys -> {
                    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                    val entries = keyStoreRepository.listAliases().mapNotNull { alias ->
                        val entry = ks.getEntry(alias, null) ?: return@mapNotNull null
                        val keyInfo = keyStoreRepository.getKeyInfo(alias)

                        val publicKeyBase64: String? = if (entry is KeyStore.PrivateKeyEntry) {
                            val publicKeyBytes = signer.getPublicKeyBytes(entry)
                            Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
                        } else {
                            null
                        }

                        KeyEntryResponse(
                            alias = keyInfo.alias,
                            algorithm = keyInfo.algorithm,
                            keySize = keyInfo.keySize,
                            purposes = keyInfo.purposes,
                            digests = keyInfo.digests,
                            signaturePaddings = keyInfo.signaturePaddings,
                            encryptionPaddings = keyInfo.encryptionPaddings,
                            blockModes = keyInfo.blockModes,
                            userAuthenticationRequired = keyInfo.userAuthenticationRequired,
                            userAuthenticationValidityDurationSeconds = keyInfo.userAuthenticationValidityDurationSeconds,
                            publicKeyBase64 = publicKeyBase64,
                        )
                    }
                    val response = json.encodeToString(
                        ListSerializer(KeyEntryResponse.serializer()),
                        entries,
                    )
                    bleServer.sendToClient(device, response.toByteArray(Charsets.UTF_8))
                    currentCommand.set(null)
                }
                is ClientMessage.Sign -> {
                    val keyInfo = keyStoreRepository.getKeyInfo(message.keyAlias)
                    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                    val entry = ks.getEntry(message.keyAlias, null) as? KeyStore.PrivateKeyEntry
                        ?: throw IllegalStateException("Key entry not found for alias: ${message.keyAlias}")
                    val dataToSign = Base64.decode(message.data, Base64.DEFAULT)
                    val algorithm = message.algorithm ?: KeystoreSigner.deriveAlgorithm(keyInfo)
                    coroutineScope.launch {
                        when (val result = signer.sign(entry.privateKey, dataToSign, algorithm, keyInfo, activity)) {
                            is SignResult.Success ->
                                bleServer.sendToClient(device, result.signature)
                            is SignResult.Error ->
                                bleServer.sendToClient(device, ByteArray(0))
                        }
                        currentCommand.set(null)
                    }
                }
                is ClientMessage.Echo -> {
                    bleServer.sendToClient(device, message.data.toByteArray(Charsets.UTF_8))
                }
            }
        }
        onDispose {
            bleServer.onDataReceived = null
        }
    }

    return BleServerState(
        isBluetoothEnabled = bluetoothEnabled,
        permissions = permissions,
        permissionsLauncher = permissionsLauncher,
        permissionsGranted = permissionsGranted,
        permissionsRequested = permissionsRequested,
    )
}
