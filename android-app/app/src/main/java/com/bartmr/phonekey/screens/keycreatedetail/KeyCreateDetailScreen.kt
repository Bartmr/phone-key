package com.bartmr.phonekey.screens.keycreatedetail

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bartmr.phonekey.core.keystore.KeyInfo
import com.bartmr.phonekey.core.keystore.KeyStoreRepository
import com.bartmr.phonekey.core.keystore.purposesToDisplayNames
import com.bartmr.phonekey.core.keystore.toDisplayNames
import com.bartmr.phonekey.core.keystore.BLOCK_MODE_DISPLAY_NAMES
import com.bartmr.phonekey.core.keystore.DIGEST_DISPLAY_NAMES
import com.bartmr.phonekey.core.keystore.ENCRYPTION_PADDING_DISPLAY_NAMES
import com.bartmr.phonekey.core.keystore.SIGNATURE_PADDING_DISPLAY_NAMES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyCreateDetailScreen(
    repository: KeyStoreRepository,
    navController: NavController,
    alias: String?,
) {
    val isCreateMode = alias == null
    val context = LocalContext.current

    var keyInfo by remember { mutableStateOf<KeyInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(alias) {
        if (!isCreateMode) {
            keyInfo = repository.getKeyInfo(alias!!)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isCreateMode) "Create key" else alias ?: "Key details") },
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
            if (isCreateMode) {
                CreateKeyForm(
                    repository = repository,
                    onKeyCreated = {
                        Toast.makeText(context, "Key created", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                )
            } else {
                keyInfo?.let { info ->
                    KeyDetailView(info = info)
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
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete key") },
            text = { Text("Permanently delete \"$alias\" from the Android Keystore?") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteKey(alias!!)
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
private fun KeyDetailView(info: KeyInfo) {
    Spacer(Modifier.height(16.dp))

    DetailRow("Alias", info.alias)
    DetailRow("Algorithm", info.algorithm)
    DetailRow("Key size", "${info.keySize} bits")
    DetailRow("Purposes", info.purposes.purposesToDisplayNames().joinToString(", "))

    if (info.digests.isNotEmpty()) {
        DetailRow("Digests", info.digests.toDisplayNames(DIGEST_DISPLAY_NAMES).joinToString(", "))
    }
    if (info.encryptionPaddings.isNotEmpty()) {
        DetailRow(
            "Encryption paddings",
            info.encryptionPaddings.toDisplayNames(ENCRYPTION_PADDING_DISPLAY_NAMES).joinToString(", "),
        )
    }
    if (info.signaturePaddings.isNotEmpty()) {
        DetailRow(
            "Signature paddings",
            info.signaturePaddings.toDisplayNames(SIGNATURE_PADDING_DISPLAY_NAMES).joinToString(", "),
        )
    }
    if (info.blockModes.isNotEmpty()) {
        DetailRow("Block modes", info.blockModes.toDisplayNames(BLOCK_MODE_DISPLAY_NAMES).joinToString(", "))
    }

    DetailRow("User auth required", if (info.userAuthenticationRequired) "Yes" else "No")
    if (info.userAuthenticationRequired) {
        val timeout = info.userAuthenticationValidityDurationSeconds
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
