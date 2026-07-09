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
import com.bartmr.phonekey.core.ssh.SignResult
import com.bartmr.phonekey.core.ssh.Ssh
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
        val data: String,
    ) : ClientMessage()

}

@Serializable
data class IdentityResponse(val alias: String, val publicKeyBase64: String)

sealed class CommandState {
    data object RequestingIdentities : CommandState()
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

    val ssh = remember { Ssh(activity) }
    val currentCommand = remember { AtomicReference<CommandState?>(null) }

    DisposableEffect(bleServer) {
        bleServer.onDataReceived = handler@{ device, data ->
            val text = String(data, Charsets.UTF_8)
            val message = json.decodeFromString<ClientMessage>(text)

            val commandState: CommandState? = when (message) {
                is ClientMessage.RequestIdentities -> CommandState.RequestingIdentities
                is ClientMessage.SshSign -> CommandState.Signing(message.keyAlias)
                is ClientMessage.Echo -> null
            }

            if (commandState != null && !currentCommand.compareAndSet(null, commandState)) {
                val busy = json.encodeToString(ErrorResponse.serializer(), ErrorResponse("busy"))
                bleServer.sendToClient(device, busy.toByteArray(Charsets.UTF_8))
                return@handler
            }

            when (message) {
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
                    bleServer.sendToClient(device, response.toByteArray(Charsets.UTF_8))
                    currentCommand.set(null)
                }
                is ClientMessage.SshSign -> {
                    val keyInfo = keyStoreRepository.getKeyInfo(message.keyAlias)
                    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                    val entry = ks.getEntry(message.keyAlias, null) as? KeyStore.PrivateKeyEntry
                        ?: throw IllegalStateException("Key entry not found for alias: ${message.keyAlias}")
                    val dataToSign = Base64.decode(message.data, Base64.DEFAULT)
                    coroutineScope.launch {
                        when (val result = ssh.sign(keyInfo, entry, dataToSign)) {
                            is SignResult.Success ->
                                bleServer.sendToClient(device, result.rawSignature)
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
