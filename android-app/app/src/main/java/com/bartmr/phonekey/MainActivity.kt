package com.bartmr.phonekey

import android.Manifest
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bartmr.phonekey.bluetooth.BluetoothModule
import com.bartmr.phonekey.ssh.SshModule
import com.bartmr.phonekey.ssh.SignResult
import com.bartmr.phonekey.ui.theme.PhoneKeyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SshModule.activity = this
        val bluetoothModule = BluetoothModule(this)

        enableEdgeToEdge()
        setContent {
            PhoneKeyTheme {
                MainScreen(bluetoothModule)
            }
        }
    }
}

data class ReceivedMessage(
    val id: Int,
    val text: String?,
    val length: Int,
)

private fun tryDecodeText(bytes: ByteArray): String? {
    return try {
        String(bytes, Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(bluetoothModule: BluetoothModule) {
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionsRequested by remember { mutableStateOf(false) }
    var serverStarted by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ReceivedMessage>() }
    var nextId by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SshModule.initializeKey()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsRequested = true
        permissionsGranted = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        ).all { results[it] == true }
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    bluetoothModule.startGattServer()
                    serverStarted = true
                }

                Lifecycle.Event.ON_PAUSE -> {
                    bluetoothModule.stopGattServer()
                    serverStarted = false
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bluetoothModule.stopGattServer()
        }
    }

    DisposableEffect(bluetoothModule) {
        bluetoothModule.onDataReceived = { data ->
            val text = tryDecodeText(data)

            messages.add(
                ReceivedMessage(
                    id = nextId,
                    text = text,
                    length = data.size,
                )
            )
            nextId++

            if (text != null) {
                try {
                    val json = JSONObject(text)
                    when (json.getString("type")) {
                        "sign" -> {
                            val dataBase64 = json.getString("data")
                            val dataBytes = Base64.decode(dataBase64, Base64.DEFAULT)
                            coroutineScope.launch {
                                val result = withContext(Dispatchers.Main) {
                                    SshModule.sign(dataBytes)
                                }
                                when (result) {
                                    is SignResult.Success -> {
                                        bluetoothModule.sendToClient(result.rawSignature)
                                    }

                                    is SignResult.Error -> {
                                        messages.add(
                                            ReceivedMessage(
                                                id = nextId,
                                                text = "Sign error: ${result.message}",
                                                length = 0,
                                            )
                                        )
                                        nextId++
                                    }
                                }
                            }
                        }

                        "get-public-key" -> {
                            coroutineScope.launch {
                                val publicKey = withContext(Dispatchers.IO) {
                                    SshModule.getPublicKey()
                                }
                                bluetoothModule.sendToClient(publicKey.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Silently drop malformed messages
                }
            }
        }
        onDispose {
            bluetoothModule.onDataReceived = null
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
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        )
                    )
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
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                state = listState,
            ) {
                items(messages.toList()) { message ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = "#${message.id} (${message.length} bytes)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (message.text != null) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}