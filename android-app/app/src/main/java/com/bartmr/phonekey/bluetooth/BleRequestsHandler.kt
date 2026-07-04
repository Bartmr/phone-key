package com.bartmr.phonekey.bluetooth

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

}

@Serializable
data class IdentityResponse(val alias: String, val publicKey: String)

private val json = Json { ignoreUnknownKeys = true }

class BleServerState(
    val isBluetoothEnabled: Boolean,
    val permissions: Array<String>,
    val permissionsLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    val permissionsGranted: Boolean,
    val permissionsRequested: Boolean,
)

@Composable
fun rememberBleRequestsHandler(
    repository: KeyStoreRepository,
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

            if (bluetoothEnabled && permissionsGranted) {
                bleServer.startGattServer()
            } else {
                bleServer.stopGattServer()
            }
        }
        bleServer.registerAdapterStateReceiver()
        onDispose {
            bleServer.unregisterAdapterStateReceiver()
            bleServer.onAdapterStateChanged = null
        }
    }

    val ssh = remember { Ssh(activity) }

    DisposableEffect(bleServer) {
        bleServer.onDataReceived = { data ->
            val text = String(data, Charsets.UTF_8)
            when (val message = json.decodeFromString<ClientMessage>(text)) {
                is ClientMessage.RequestIdentities -> {
                    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                    val identities = repository.listAliases().mapNotNull { alias ->
                        val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                            ?: return@mapNotNull null
                        val publicKey = ssh.getPublicKey(entry)
                        IdentityResponse(alias, publicKey)
                    }
                    val response = json.encodeToString(
                        ListSerializer(IdentityResponse.serializer()),
                        identities,
                    )
                    bleServer.sendToClient(response.toByteArray(Charsets.UTF_8))
                }
                is ClientMessage.SshSign -> {
                    val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                    val entry = ks.getEntry(message.keyAlias, null) as? KeyStore.PrivateKeyEntry
                    if (entry == null) {
                        bleServer.sendToClient(ByteArray(0))
                    } else {
                        val dataToSign = Base64.decode(message.data, Base64.DEFAULT)
                        coroutineScope.launch {
                            when (val result = ssh.sign(entry, dataToSign)) {
                                is SignResult.Success ->
                                    bleServer.sendToClient(result.rawSignature)
                                is SignResult.Error ->
                                    bleServer.sendToClient(ByteArray(0))
                            }
                        }
                    }
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
