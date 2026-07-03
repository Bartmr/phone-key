package com.bartmr.phonekey

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bartmr.phonekey.keystore.KeyStoreRepository
import com.bartmr.phonekey.KeyListScreen
import com.bartmr.phonekey.bluetooth.Bluetooth
import com.bartmr.phonekey.ui.theme.PhoneKeyTheme
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            PhoneKeyTheme {
                AppNavHost()
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

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = remember { KeyStoreRepository(context) }

    val bluetooth = remember { Bluetooth(context) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionsRequested by remember { mutableStateOf(false) }
    var serverStarted by remember { mutableStateOf(false) }

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

                }

                is ClientMessage.GetPublicKey -> {

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
    }

    NavHost(
        navController = navController,
        startDestination = "key_list",
    ) {
        composable("key_list") {
            KeyListScreen(
                repository = repository,
                navController = navController,
            )
        }
        composable(
            route = "key_create_detail/{alias}",
            arguments = listOf(navArgument("alias") { type = NavType.StringType }),
        ) { backStackEntry ->
            val alias = backStackEntry.arguments?.getString("alias")
            KeyCreateDetailScreen(
                repository = repository,
                navController = navController,
                alias = alias,
            )
        }
    }
}