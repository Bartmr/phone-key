package com.bartmr.phonekey

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.unit.dp
import com.bartmr.phonekey.keystore.KeyStoreRepository
import com.bartmr.phonekey.bluetooth.rememberBleRequestsHandler
import com.bartmr.phonekey.ui.theme.PhoneKeyTheme

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

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val repository = remember { KeyStoreRepository() }

    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionsRequested by remember { mutableStateOf(false) }

    val bleServerState = rememberBleRequestsHandler()
    val bluetoothEnabled = bleServerState.isBluetoothEnabled

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
        return@AppNavHost
    }

    if (!bluetoothEnabled) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Bluetooth is turned off. Turn it on to use Phone Key.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Open Bluetooth Settings")
            }
        }
        return@AppNavHost
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
            route = "key_create_detail?alias={alias}",
            arguments = listOf(
                navArgument("alias") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
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