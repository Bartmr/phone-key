package com.bartmr.phonekey

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bartmr.phonekey.bluetooth.Bluetooth
import com.bartmr.phonekey.ssh.Ssh
import com.bartmr.phonekey.ssh.SignResult
import com.bartmr.phonekey.ui.theme.PhoneKeyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ssh = Ssh(this)
        val bluetooth = Bluetooth(this)

        enableEdgeToEdge()
        setContent {
            PhoneKeyTheme {
                MainScreen(bluetooth, ssh)
            }
        }
    }
}

@Serializable
sealed class ClientMessage {
    @Serializable
    @SerialName("sign")
    data class SignRequest(val data: String) : ClientMessage()

    @Serializable
    @SerialName("get-public-key")
    data object GetPublicKey : ClientMessage()
}

val json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(bluetooth: Bluetooth, ssh: Ssh) {
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionsRequested by remember { mutableStateOf(false) }
    var serverStarted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ssh.initializeKey()
    }

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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    bluetooth.startGattServer()
                    serverStarted = true
                }

                Lifecycle.Event.ON_PAUSE -> {
                    bluetooth.stopGattServer()
                    serverStarted = false
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bluetooth.stopGattServer()
        }
    }

    DisposableEffect(bluetooth) {
        bluetooth.onDataReceived = { data ->
            val text = String(data, Charsets.UTF_8)

            when (val message = json.decodeFromString<ClientMessage>(text)) {
                is ClientMessage.SignRequest -> {
                    val dataBytes = Base64.decode(message.data, Base64.DEFAULT)
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.Main) {
                            ssh.sign(dataBytes)
                        }
                        when (result) {
                            is SignResult.Success -> {
                                bluetooth.sendToClient(result.rawSignature)
                            }

                            is SignResult.Error -> throw AssertionError("Could not sign data")
                        }
                    }
                }

                is ClientMessage.GetPublicKey -> {
                    coroutineScope.launch {
                        val publicKey = withContext(Dispatchers.IO) {
                            ssh.getPublicKey()
                        }
                        bluetooth.sendToClient(publicKey.toByteArray(Charsets.UTF_8))
                    }
                }
            }

        }
        onDispose {
            bluetooth.onDataReceived = null
        }
    }

    if (permissionsRequested && !permissionsGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Bluetooth permissions are required to encrypt and decrypt data.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Button(
                onClick = {
                    permissionsLauncher.launch(permissions)
                },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Grant Permissions")
            }
        }
    } else if (!serverStarted) {
        // Show nothing while waiting for the server to start
        return
    } else {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Phone Key") })
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {}
        }
    }
}