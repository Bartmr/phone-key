package com.bartmr.phonekey

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bartmr.phonekey.keystore.KeysRepository
import com.bartmr.phonekey.settings.AppSettings
import com.bartmr.phonekey.ui.theme.PhoneKeyTheme
import com.bartmr.phonekey.ui.screens.keylist.KeyListScreen
import com.bartmr.phonekey.ui.screens.keycreate.KeyCreateScreen
import com.bartmr.phonekey.ui.screens.keydetail.KeyDetailScreen
import com.bartmr.phonekey.ui.screens.settings.SettingsScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as PhoneKeyApplication
        val repository = app.repository
        val settings = app.settings
        val httpServer = app.httpServer

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
}

@Composable
fun AppNavHost(
    repository: KeysRepository,
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
        composable("key_create") {
            KeyCreateScreen(
                repository = repository,
                navController = navController,
            )
        }
        composable(
            route = "key_detail?id={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                }
            ),
        ) { backStackEntry ->
            val id = requireNotNull(backStackEntry.arguments?.getString("id")) {
                "key_detail route is missing the required 'id' argument"
            }
            KeyDetailScreen(
                repository = repository,
                navController = navController,
                id = id,
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