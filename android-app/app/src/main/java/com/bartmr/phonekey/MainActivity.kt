package com.bartmr.phonekey

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bartmr.phonekey.keystore.KeyStoreRepository
import com.bartmr.phonekey.server.PhoneKeyServer
import com.bartmr.phonekey.settings.AppSettings
import com.bartmr.phonekey.ui.theme.PhoneKeyTheme
import com.bartmr.phonekey.screens.keylist.KeyListScreen
import com.bartmr.phonekey.screens.keycreatedetail.KeyCreateDetailScreen
import com.bartmr.phonekey.screens.settings.SettingsScreen

class MainActivity : AppCompatActivity() {
    private lateinit var repository: KeyStoreRepository
    private lateinit var settings: AppSettings
    private lateinit var httpServer: PhoneKeyServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = KeyStoreRepository(this)
        settings = AppSettings(this)
        httpServer = PhoneKeyServer(this, repository, settings)

        enableEdgeToEdge()
        setContent {
            PhoneKeyTheme {
                AppNavHost(
                    repository = repository,
                    settings = settings,
                    onPortChanged = { httpServer.restart() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            httpServer.start()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start HTTP server", e)
        }
    }

    override fun onStop() {
        try {
            httpServer.stop()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to stop HTTP server", e)
        }
        super.onStop()
    }
}

@Composable
fun AppNavHost(
    repository: KeyStoreRepository,
    settings: AppSettings,
    onPortChanged: () -> Boolean,
) {
    val navController = rememberNavController()

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
        composable("settings") {
            SettingsScreen(
                settings = settings,
                navController = navController,
                onPortChanged = onPortChanged,
            )
        }
    }
}