package com.bartmr.phonekey.screens.keylist

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
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bartmr.phonekey.core.keystore.KeyInfo
import com.bartmr.phonekey.core.keystore.KeyStoreRepository
import com.bartmr.phonekey.core.keystore.purposesToDisplayNames

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyListScreen(
    repository: KeyStoreRepository,
    navController: NavController,
) {
    val keys = remember { mutableStateListOf<KeyInfo>() }
    var deleteConfirmAlias by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val aliases = repository.listAliases()
        keys.clear()
        keys.addAll(aliases.map { repository.getKeyInfo(it) })
    }

    fun refreshKeys() {
        val aliases = repository.listAliases()
        keys.clear()
        keys.addAll(aliases.map { repository.getKeyInfo(it) })
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Phone Key") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("key_create_detail") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create key")
            }
        },
    ) { paddingValues ->
        if (keys.isEmpty()) {
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
                        "No keys in Keystore",
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
                items(keys.toList()) { keyInfo ->
                    KeyListItem(
                        keyInfo = keyInfo,
                        onClick = {
                            navController.navigate("key_create_detail?alias=${Uri.encode(keyInfo.alias)}")
                        },
                        onDelete = { deleteConfirmAlias = keyInfo.alias },
                    )
                }
            }
        }
    }

    if (deleteConfirmAlias != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmAlias = null },
            title = { Text("Delete key") },
            text = { Text("Permanently delete \"${deleteConfirmAlias}\" from the Android Keystore?") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteKey(deleteConfirmAlias!!)
                    deleteConfirmAlias = null
                    refreshKeys()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmAlias = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun KeyListItem(
    keyInfo: KeyInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val icon: ImageVector = Icons.Default.Lock

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
            Icon(icon, contentDescription = keyInfo.algorithm)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    keyInfo.alias,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${keyInfo.algorithm} — ${keyInfo.keySize} bits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    keyInfo.purposes.purposesToDisplayNames().joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${keyInfo.alias}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
