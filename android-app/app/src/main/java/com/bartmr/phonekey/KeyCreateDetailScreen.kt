package com.bartmr.phonekey

import android.security.keystore.KeyProperties
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bartmr.phonekey.keystore.KeyInfo
import com.bartmr.phonekey.keystore.KeyStoreRepository
import com.bartmr.phonekey.keystore.purposesToDisplayNames
import com.bartmr.phonekey.keystore.toDisplayNames
import com.bartmr.phonekey.keystore.BLOCK_MODE_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.DIGEST_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.ENCRYPTION_PADDING_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.ORIGIN_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.PURPOSE_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.SIGNATURE_PADDING_DISPLAY_NAMES

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
                    ) {
                        Text("Delete key", color = MaterialTheme.colorScheme.error)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateKeyForm(
    repository: KeyStoreRepository,
    onKeyCreated: () -> Unit,
) {
    var alias by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf(KeyProperties.KEY_ALGORITHM_EC) }
    var selectedKeySize by remember { mutableStateOf(256) }
    var selectedPurposes by remember { mutableStateOf(KeyProperties.PURPOSE_SIGN) }
    var selectedDigests by remember { mutableStateOf(listOf(KeyProperties.DIGEST_SHA256)) }
    var selectedEncryptionPaddings by remember { mutableStateOf(emptyList<String>()) }
    var selectedSignaturePaddings by remember { mutableStateOf(emptyList<String>()) }
    var selectedBlockModes by remember { mutableStateOf(emptyList<String>()) }
    var userAuthRequired by remember { mutableStateOf(false) }
    var authValiditySeconds by remember { mutableStateOf("0") }
    var authType by remember { mutableStateOf(KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL) }
    var aliasError by remember { mutableStateOf<String?>(null) }

    val algorithms = listOf(
        KeyProperties.KEY_ALGORITHM_EC,
        KeyProperties.KEY_ALGORITHM_RSA,
        KeyProperties.KEY_ALGORITHM_AES,
        "HMAC",
    )

    val keySizes: List<Int> = when (selectedAlgorithm) {
        KeyProperties.KEY_ALGORITHM_RSA -> listOf(2048, 3072, 4096)
        KeyProperties.KEY_ALGORITHM_EC -> listOf(224, 256, 384, 521)
        KeyProperties.KEY_ALGORITHM_AES -> listOf(128, 192, 256)
        else -> listOf(128, 160, 224, 256, 384, 512)
    }

    val availablePurposes: List<Pair<Int, String>> = when (selectedAlgorithm) {
        KeyProperties.KEY_ALGORITHM_RSA -> PURPOSE_DISPLAY_NAMES.toList()
        KeyProperties.KEY_ALGORITHM_EC -> PURPOSE_DISPLAY_NAMES.filterKeys {
            it == KeyProperties.PURPOSE_SIGN || it == KeyProperties.PURPOSE_VERIFY
        }.toList()
        KeyProperties.KEY_ALGORITHM_AES -> PURPOSE_DISPLAY_NAMES.filterKeys {
            it == KeyProperties.PURPOSE_ENCRYPT || it == KeyProperties.PURPOSE_DECRYPT
        }.toList()
        else -> PURPOSE_DISPLAY_NAMES.filterKeys {
            it == KeyProperties.PURPOSE_SIGN
        }.toList()
    }

    val showPaddings = selectedAlgorithm == KeyProperties.KEY_ALGORITHM_RSA ||
            selectedAlgorithm == KeyProperties.KEY_ALGORITHM_AES
    val showSignaturePaddings = selectedAlgorithm == KeyProperties.KEY_ALGORITHM_RSA &&
            (selectedPurposes and (KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)) != 0
    val showDigests = selectedAlgorithm == KeyProperties.KEY_ALGORITHM_EC ||
            selectedAlgorithm == KeyProperties.KEY_ALGORITHM_RSA
    val showBlockModes = selectedAlgorithm == KeyProperties.KEY_ALGORITHM_AES
    val isSymmetric = selectedAlgorithm == KeyProperties.KEY_ALGORITHM_AES ||
            selectedAlgorithm == "HMAC"

    fun onAlgorithmChanged(newAlgorithm: String) {
        selectedAlgorithm = newAlgorithm

        val (defaultSize, defaultPurposes) = when (newAlgorithm) {
            KeyProperties.KEY_ALGORITHM_RSA -> 2048 to KeyProperties.PURPOSE_SIGN
            KeyProperties.KEY_ALGORITHM_EC -> 256 to KeyProperties.PURPOSE_SIGN
            KeyProperties.KEY_ALGORITHM_AES -> 256 to KeyProperties.PURPOSE_ENCRYPT
            else -> 256 to KeyProperties.PURPOSE_SIGN
        }
        selectedKeySize = defaultSize
        selectedPurposes = defaultPurposes
        selectedDigests = emptyList()
        selectedEncryptionPaddings = emptyList()
        selectedSignaturePaddings = emptyList()
        selectedBlockModes = emptyList()

        if (newAlgorithm == KeyProperties.KEY_ALGORITHM_EC || newAlgorithm == KeyProperties.KEY_ALGORITHM_RSA) {
            selectedDigests = selectedDigests + KeyProperties.DIGEST_SHA256
        }
    }

    Spacer(Modifier.height(16.dp))

    Text("Algorithm", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow {
        algorithms.forEachIndexed { index, algo ->
            SegmentedButton(
                selected = selectedAlgorithm == algo,
                onClick = { onAlgorithmChanged(algo) },
                shape = SegmentedButtonDefaults.itemShape(index, algorithms.size),
            ) {
                Text(algo)
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = alias,
        onValueChange = {
            alias = it
            aliasError = null
        },
        label = { Text("Alias") },
        isError = aliasError != null,
        supportingText = aliasError?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))

    DropdownSelector(
        label = "Key size",
        options = keySizes.map { Pair(it, "$it bits") },
        selectedValue = selectedKeySize,
        onValueSelected = { selectedKeySize = it },
    )

    Spacer(Modifier.height(16.dp))

    Text("Purposes", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        availablePurposes.forEach { (purposeValue, purposeName) ->
            if (purposeValue != availablePurposes.firstOrNull()?.first) {
                Spacer(Modifier.width(8.dp))
            }
            FilterChip(
                selected = (selectedPurposes and purposeValue) != 0,
                onClick = {
                    selectedPurposes = selectedPurposes xor purposeValue
                },
                label = { Text(purposeName) },
            )
        }
    }

    if (showDigests) {
        Spacer(Modifier.height(16.dp))
        Text("Digests", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        val availableDigests = if (selectedAlgorithm == KeyProperties.KEY_ALGORITHM_RSA) {
            DIGEST_DISPLAY_NAMES.toList()
        } else {
            DIGEST_DISPLAY_NAMES.filterKeys {
                it != KeyProperties.DIGEST_SHA1
            }.toList()
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            availableDigests.forEach { (digestValue, digestName) ->
                FilterChip(
                    selected = digestValue in selectedDigests,
                    onClick = {
                        selectedDigests = if (digestValue in selectedDigests) {
                            selectedDigests - digestValue
                        } else {
                            selectedDigests + digestValue
                        }
                    },
                    label = { Text(digestName) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }

    if (showSignaturePaddings) {
        Spacer(Modifier.height(16.dp))
        Text("Signature paddings", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SIGNATURE_PADDING_DISPLAY_NAMES.forEach { (paddingValue, paddingName) ->
                FilterChip(
                    selected = paddingValue in selectedSignaturePaddings,
                    onClick = {
                        selectedSignaturePaddings = if (paddingValue in selectedSignaturePaddings) {
                            selectedSignaturePaddings - paddingValue
                        } else {
                            selectedSignaturePaddings + paddingValue
                        }
                    },
                    label = { Text(paddingName) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }

    if (showBlockModes) {
        Spacer(Modifier.height(16.dp))
        Text("Block modes", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            BLOCK_MODE_DISPLAY_NAMES.forEach { (modeValue, modeName) ->
                FilterChip(
                    selected = modeValue in selectedBlockModes,
                    onClick = {
                        selectedBlockModes = if (modeValue in selectedBlockModes) {
                            selectedBlockModes - modeValue
                        } else {
                            selectedBlockModes + modeValue
                        }
                    },
                    label = { Text(modeName) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }

    if (showPaddings) {
        Spacer(Modifier.height(16.dp))
        Text("Encryption paddings", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        val availablePaddings = if (selectedAlgorithm == KeyProperties.KEY_ALGORITHM_AES) {
            ENCRYPTION_PADDING_DISPLAY_NAMES.filterKeys {
                it == KeyProperties.ENCRYPTION_PADDING_NONE || it == KeyProperties.ENCRYPTION_PADDING_PKCS7
            }.toList()
        } else {
            ENCRYPTION_PADDING_DISPLAY_NAMES.filterKeys {
                it != KeyProperties.ENCRYPTION_PADDING_PKCS7
            }.toList()
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            availablePaddings.forEach { (paddingValue, paddingName) ->
                FilterChip(
                    selected = paddingValue in selectedEncryptionPaddings,
                    onClick = {
                        selectedEncryptionPaddings = if (paddingValue in selectedEncryptionPaddings) {
                            selectedEncryptionPaddings - paddingValue
                        } else {
                            selectedEncryptionPaddings + paddingValue
                        }
                    },
                    label = { Text(paddingName) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    Text("User authentication", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Require authentication", modifier = Modifier.weight(1f))
        Switch(checked = userAuthRequired, onCheckedChange = { userAuthRequired = it })
    }

    if (userAuthRequired) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = authValiditySeconds,
            onValueChange = { authValiditySeconds = it },
            label = { Text("Timeout (seconds, 0 for every use)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Text("Authentication type", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = authType == KeyProperties.AUTH_BIOMETRIC_STRONG,
                onClick = { authType = KeyProperties.AUTH_BIOMETRIC_STRONG },
            )
            Text("Biometric only", modifier = Modifier.padding(start = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = authType == KeyProperties.AUTH_DEVICE_CREDENTIAL,
                onClick = { authType = KeyProperties.AUTH_DEVICE_CREDENTIAL },
            )
            Text("Device credential only", modifier = Modifier.padding(start = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = authType == (KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL),
                onClick = {
                    authType = KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                },
            )
            Text("Biometric or device credential", modifier = Modifier.padding(start = 4.dp))
        }
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = {
            val trimmedAlias = alias.trim()
            if (trimmedAlias.isEmpty()) {
                aliasError = "Alias must not be empty"
                return@Button
            }
            if (repository.keyExists(trimmedAlias)) {
                aliasError = "Alias already exists"
                return@Button
            }
            if (selectedPurposes == 0) {
                return@Button
            }

            val info = KeyInfo(
                alias = trimmedAlias,
                algorithm = selectedAlgorithm,
                keySize = selectedKeySize,
                purposes = selectedPurposes,
                digests = selectedDigests.toList(),
                encryptionPaddings = selectedEncryptionPaddings.toList(),
                signaturePaddings = selectedSignaturePaddings.toList(),
                blockModes = selectedBlockModes.toList(),
                userAuthenticationRequired = userAuthRequired,
                userAuthenticationValidityDurationSeconds = authValiditySeconds.toIntOrNull() ?: 0,
            )

            repository.generateKey(info)
            onKeyCreated()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Create key")
    }

    Spacer(Modifier.height(24.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    options: List<Pair<Int, String>>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        onValueSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
