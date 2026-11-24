package com.bartmr.phonekey.main.keydetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bartmr.phonekey.keystore.StoredKey
import com.bartmr.phonekey.keystore.KeysRepository
import com.bartmr.phonekey.keystore.purposesToDisplayNames
import com.bartmr.phonekey.keystore.toDisplayNames
import com.bartmr.phonekey.keystore.BLOCK_MODE_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.DIGEST_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.ENCRYPTION_PADDING_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.SIGNATURE_PADDING_DISPLAY_NAMES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyDetailScreen(
    repository: KeysRepository,
    navController: NavController,
    id: String,
) {
    var storedKey by remember { mutableStateOf<StoredKey?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        storedKey = repository.getStoredKey(id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(storedKey?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            storedKey?.let { key ->
                KeyDetailView(storedKey = key)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainerColor = ButtonDefaults.buttonColors().disabledContainerColor,
                        disabledContentColor = ButtonDefaults.buttonColors().disabledContentColor,
                    ),
                ) {
                    Text("Delete key")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete key") },
            text = { Text("Permanently delete \"${storedKey?.name}\" from the Android Keystore?") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteKey(id)
                    showDeleteDialog = false
                    navController.popBackStack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun KeyDetailView(storedKey: StoredKey) {
    Spacer(Modifier.height(16.dp))

    DetailRow("ID", storedKey.id)
    DetailRow("Name", storedKey.name)
    DetailRow("Algorithm", storedKey.algorithm)
    DetailRow("Key size", "${storedKey.keySize} bits")
    DetailRow("Purposes", storedKey.purposes.purposesToDisplayNames().joinToString(", "))

    if (storedKey.digests.isNotEmpty()) {
        DetailRow("Digests", storedKey.digests.toDisplayNames(DIGEST_DISPLAY_NAMES).joinToString(", "))
    }
    if (storedKey.encryptionPaddings.isNotEmpty()) {
        DetailRow(
            "Encryption paddings",
            storedKey.encryptionPaddings.toDisplayNames(ENCRYPTION_PADDING_DISPLAY_NAMES).joinToString(", "),
        )
    }
    if (storedKey.signaturePaddings.isNotEmpty()) {
        DetailRow(
            "Signature paddings",
            storedKey.signaturePaddings.toDisplayNames(SIGNATURE_PADDING_DISPLAY_NAMES).joinToString(", "),
        )
    }
    if (storedKey.blockModes.isNotEmpty()) {
        DetailRow("Block modes", storedKey.blockModes.toDisplayNames(BLOCK_MODE_DISPLAY_NAMES).joinToString(", "))
    }

    DetailRow("User auth required", if (storedKey.userAuthenticationRequired) "Yes" else "No")
    if (storedKey.userAuthenticationRequired) {
        val timeout = storedKey.userAuthenticationValidityDurationSeconds
        val timeoutDisplay = if (timeout == 0) "Every use" else "${timeout}s"
        DetailRow("Auth timeout", timeoutDisplay)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(180.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
