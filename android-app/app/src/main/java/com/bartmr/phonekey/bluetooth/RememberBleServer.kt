package com.bartmr.phonekey.bluetooth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class ClientMessage {
    @Serializable
    @SerialName("sign")
    data class SignRequest(val data: String) : ClientMessage()

    @Serializable
    @SerialName("get-public-key")
    data object GetPublicKey : ClientMessage()
}

private val json = Json { ignoreUnknownKeys = true }

class BleServerState(
    val server: BleServer,
    val isBluetoothEnabled: Boolean,
)

@Composable
fun rememberBleServer(): BleServerState {
    val context = LocalContext.current
    val bleServer = remember { BleServer(context) }
    var bluetoothEnabled by remember { mutableStateOf(bleServer.isAdapterEnabled()) }

    DisposableEffect(bleServer) {
        bleServer.onAdapterStateChanged = { enabled ->
            bluetoothEnabled = enabled

            if (bluetoothEnabled) {
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (bluetoothEnabled) {
                        bleServer.startGattServer()
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    bleServer.stopGattServer()
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bleServer.stopGattServer()
        }
    }

    DisposableEffect(bleServer) {
        bleServer.onDataReceived = { data ->
            val text = String(data, Charsets.UTF_8)
            when (val message = json.decodeFromString<ClientMessage>(text)) {
                is ClientMessage.SignRequest -> {
                }

                is ClientMessage.GetPublicKey -> {
                }
            }
        }
        onDispose {
            bleServer.onDataReceived = null
        }
    }

    return BleServerState(server = bleServer, isBluetoothEnabled = bluetoothEnabled)
}
