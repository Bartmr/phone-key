package com.bartmr.phonekey

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.bartmr.phonekey.keylist.KeyListScreen
import com.bartmr.phonekey.keycreatedetail.KeyCreateDetailScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            PhoneKeyTheme {
                AppNavHost()
                // BluetoothTestScreen()
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val repository = remember { KeyStoreRepository(context) }

    val bleRequestsHandler = rememberBleRequestsHandler(
        keyStoreRepository = repository,
        activity = context as FragmentActivity,
    )
    
    if (bleRequestsHandler.permissionsRequested && !bleRequestsHandler.permissionsGranted) {
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
                    bleRequestsHandler.permissionsLauncher.launch(bleRequestsHandler.permissions)
                },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Grant Permissions")
            }
        }
        return@AppNavHost
    }

    if (!bleRequestsHandler.isBluetoothEnabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Bluetooth is turned off. Turn it on to use Phone Key.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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