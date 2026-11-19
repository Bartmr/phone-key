package com.bartmr.phonekey.ui.screens.keylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bartmr.phonekey.keystore.StoredKey
import com.bartmr.phonekey.keystore.KeysRepository
import com.bartmr.phonekey.keystore.purposesToDisplayNames

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyListScreen(
    repository: KeysRepository,
    navController: NavController,
) {
    val storedKeys = remember { mutableStateListOf<StoredKey>() }
    var deleteConfirmKey by remember { mutableStateOf<StoredKey?>(null) }

    fun refreshStoredKeys() {
        storedKeys.clear()
        storedKeys.addAll(repository.getAllStoredKeys())
    }

    LaunchedEffect(Unit) { refreshStoredKeys() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Key") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("key_create") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create key")
            }
        },
    ) { paddingValues ->
        if (storedKeys.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.height(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No keys",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(storedKeys.toList()) { storedKey ->
                    KeyListItem(
                        storedKey = storedKey,
                        onClick = {
                            navController.navigate("key_detail?id=${Uri.encode(storedKey.id)}")
                        },
                        onDelete = { deleteConfirmKey = storedKey },
                    )
                }
            }
        }
    }

    deleteConfirmKey?.let { key ->
        AlertDialog(
            onDismissRequest = { deleteConfirmKey = null },
            title = { Text("Delete key") },
            text = { Text("Permanently delete \"${key.name}\" from the Android Keystore?") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteKey(key.id)
                    deleteConfirmKey = null
                    refreshStoredKeys()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmKey = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun KeyListItem(
    storedKey: StoredKey,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Lock, contentDescription = storedKey.algorithm)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    storedKey.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${storedKey.algorithm} — ${storedKey.keySize} bits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    storedKey.purposes.purposesToDisplayNames().joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${storedKey.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
