package com.bartmr.phonekey.ui.screens.keycreate

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.security.keystore.KeyProperties
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.bartmr.phonekey.keystore.BLOCK_MODE_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.DIGEST_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.ENCRYPTION_PADDING_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.NewKeySpec
import com.bartmr.phonekey.keystore.KeysRepository
import com.bartmr.phonekey.keystore.PURPOSE_DISPLAY_NAMES
import com.bartmr.phonekey.keystore.SIGNATURE_PADDING_DISPLAY_NAMES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyCreateScreen(
    repository: KeysRepository,
    navController: NavController,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create key") },
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
            CreateKeyForm(
                repository = repository,
                onKeyCreated = {
                    Toast.makeText(context, "Key created", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateKeyForm(
    repository: KeysRepository,
    onKeyCreated: () -> Unit,
) {
    val context = LocalContext.current
    val keyguardManager = remember {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }
    var hasSecureLockScreen by remember { mutableStateOf(keyguardManager.isDeviceSecure) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSecureLockScreen = keyguardManager.isDeviceSecure
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    var name by remember { mutableStateOf("") }
    var nameTouched by remember { mutableStateOf(false) }
    var selectedAlgorithm by remember { mutableStateOf(KeyProperties.KEY_ALGORITHM_EC) }
    var selectedKeySize by remember { mutableStateOf(256) }
    var selectedPurposes by remember { mutableStateOf(KeyProperties.PURPOSE_SIGN) }
    var selectedDigests by remember { mutableStateOf(listOf(KeyProperties.DIGEST_SHA256)) }
    var selectedEncryptionPaddings by remember { mutableStateOf(emptyList<String>()) }
    var selectedSignaturePaddings by remember { mutableStateOf(emptyList<String>()) }
    var selectedBlockModes by remember { mutableStateOf(emptyList<String>()) }
    var userAuthRequired by remember(hasSecureLockScreen) { mutableStateOf(hasSecureLockScreen) }
    var authValiditySeconds by remember { mutableStateOf("0") }
    val authValidityError = if (!userAuthRequired) {
        null
    } else {
        val seconds = authValiditySeconds.toLongOrNull()
        when {
            authValiditySeconds.isEmpty() -> "Enter a number"
            seconds == null -> "Enter a valid number"
            seconds > Int.MAX_VALUE -> "Value is too large"
            else -> null
        }
    }
    val nameError = if (name.trim().isEmpty()) "Name must not be empty" else null
    val visibleNameError = nameError.takeIf { nameTouched }

    val algorithmLabels = mapOf(
        KeyProperties.KEY_ALGORITHM_EC to "EC",
        KeyProperties.KEY_ALGORITHM_RSA to "RSA",
        KeyProperties.KEY_ALGORITHM_AES to "AES",
        KeyProperties.KEY_ALGORITHM_HMAC_SHA256 to "HMAC",
    )
    val algorithms = algorithmLabels.keys.toList()

    val keySizes: List<Int> = when (selectedAlgorithm) {
        KeyProperties.KEY_ALGORITHM_RSA -> listOf(2048, 3072, 4096)
        KeyProperties.KEY_ALGORITHM_EC -> listOf(256, 384, 521)
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

    val requiresBlockMode = selectedAlgorithm == KeyProperties.KEY_ALGORITHM_AES
    val requiresEncryptionPadding = selectedAlgorithm == KeyProperties.KEY_ALGORITHM_AES ||
            (selectedAlgorithm == KeyProperties.KEY_ALGORITHM_RSA &&
                    (selectedPurposes and (KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)) != 0)
    val requiresSignaturePadding = selectedAlgorithm == KeyProperties.KEY_ALGORITHM_RSA &&
            (selectedPurposes and (KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)) != 0
    val specError = when {
        selectedPurposes == 0 -> "Select at least one purpose"
        requiresBlockMode && selectedBlockModes.isEmpty() -> "Select at least one block mode"
        requiresEncryptionPadding && selectedEncryptionPaddings.isEmpty() ->
            "Select at least one encryption padding"
        requiresSignaturePadding && selectedSignaturePaddings.isEmpty() ->
            "Select at least one signature padding"
        else -> null
    }

    Spacer(Modifier.height(16.dp))

    Text("Algorithm", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow {
        algorithms.forEachIndexed { index, algo ->
            SegmentedButton(
                selected = selectedAlgorithm == algo,
                onClick = {
                    selectedAlgorithm = algo

                    selectedKeySize = when (algo) {
                        KeyProperties.KEY_ALGORITHM_RSA -> 2048
                        KeyProperties.KEY_ALGORITHM_EC -> 256
                        KeyProperties.KEY_ALGORITHM_AES -> 256
                        else -> 256
                    }
                    selectedPurposes = when (algo) {
                        KeyProperties.KEY_ALGORITHM_AES -> KeyProperties.PURPOSE_ENCRYPT
                        else -> KeyProperties.PURPOSE_SIGN
                    }
                    selectedDigests = when (algo) {
                        KeyProperties.KEY_ALGORITHM_EC,
                        KeyProperties.KEY_ALGORITHM_RSA -> listOf(KeyProperties.DIGEST_SHA256)
                        else -> emptyList()
                    }
                    selectedEncryptionPaddings = when (algo) {
                        KeyProperties.KEY_ALGORITHM_RSA ->
                            listOf(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        KeyProperties.KEY_ALGORITHM_AES ->
                            listOf(KeyProperties.ENCRYPTION_PADDING_NONE)
                        else -> emptyList()
                    }
                    selectedSignaturePaddings = when (algo) {
                        KeyProperties.KEY_ALGORITHM_RSA ->
                            listOf(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        else -> emptyList()
                    }
                    selectedBlockModes = when (algo) {
                        KeyProperties.KEY_ALGORITHM_AES -> listOf(KeyProperties.BLOCK_MODE_GCM)
                        else -> emptyList()
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index, algorithms.size),
            ) {
                Text(algorithmLabels[algo] ?: algo)
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            nameTouched = true
        },
        label = { Text("Name") },
        isError = visibleNameError != null,
        supportingText = visibleNameError?.let { { Text(it) } },
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
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        availablePurposes.forEach { (purposeValue, purposeName) ->
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            }
        }
    }

    if (showSignaturePaddings) {
        Spacer(Modifier.height(16.dp))
        Text("Signature paddings", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            }
        }
    }

    if (showBlockModes) {
        Spacer(Modifier.height(16.dp))
        Text("Block modes", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                it == KeyProperties.ENCRYPTION_PADDING_RSA_OAEP ||
                        it == KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1
            }.toList()
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        Switch(
            checked = userAuthRequired,
            onCheckedChange = { userAuthRequired = it },
            enabled = hasSecureLockScreen,
        )
    }

    if (!hasSecureLockScreen) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Set up a secure lock screen (PIN, pattern, or password) to require authentication.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (userAuthRequired) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = authValiditySeconds,
            onValueChange = { input ->
                if (input.length <= 10 && input.all(Char::isDigit)) {
                    authValiditySeconds = input
                }
            },
            label = { Text("Timeout (seconds, 0 for every use)") },
            isError = authValidityError != null,
            supportingText = authValidityError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (specError != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            specError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(24.dp))

    Button(
        enabled = nameError == null && authValidityError == null && specError == null,
        onClick = {
            val newKeySpec = NewKeySpec(
                name = name.trim(),
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

            repository.generateKey(newKeySpec)
            onKeyCreated()

        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Create key")
    }

    Spacer(Modifier.height(24.dp))
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
