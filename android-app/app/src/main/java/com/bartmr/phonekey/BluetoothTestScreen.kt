package com.bartmr.phonekey

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bartmr.phonekey.bluetooth.BleServer


@Composable
fun BluetoothTestScreen() {
    val context = LocalContext.current
    val bleServer = remember { BleServer(context) }
    var permissionsGranted by remember { mutableStateOf(false) }


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
        if (permissions.all { results[it] == true }) {
            permissionsGranted = true
        }
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(permissions)
    }

    DisposableEffect( permissionsGranted) {
        if (permissionsGranted) {
            bleServer.startGattServer()
        }

        onDispose {  }
    }

    DisposableEffect(bleServer) {
        val payload = "The quick brown fox jumps over the lazy dog. ".repeat(100).toByteArray(Charsets.UTF_8)

        bleServer.onDataReceived = { device, data ->
            Log.i("BluetoothTestScreen", "Received ${data.size} bytes from ${device.address}: ${String(data, Charsets.UTF_8)}")
            bleServer.sendToClient(device, payload)
        }
        onDispose {
            bleServer.onDataReceived = null
        }
    }

    Box(Modifier.fillMaxSize())

}