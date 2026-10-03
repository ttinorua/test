package com.financetracker.app.ui.screens.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.enablebanking.EnableBankingCredentials
import com.financetracker.app.ui.components.SetupGuideDialog
import com.financetracker.app.ui.components.SetupGuides

private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** One line in Settings > Bank saying which Enable Banking registration is in use, with a button
 * to set up or change the user's own. */
@Composable
fun EnableBankingAppRow(ownApplicationId: String?, isConfigured: Boolean, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enable Banking app", style = MaterialTheme.typography.labelLarge)
            Text(
                when {
                    ownApplicationId != null -> "Your own (…${ownApplicationId.takeLast(4)})"
                    isConfigured -> "Built into the app"
                    else -> "Not set up — add your own to connect a bank"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isConfigured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = { showDialog = true }) { Text(if (ownApplicationId != null) "Change" else "Use my own") }
    }
    if (showDialog) {
        OwnEnableBankingDialog(hasOwn = ownApplicationId != null, onDismiss = { showDialog = false })
    }
}

@Composable
private fun OwnEnableBankingDialog(hasOwn: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var applicationId by remember { mutableStateOf("") }
    var pem by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }

    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = null
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.takeIf { it.size < 64_000 }
                ?.toString(Charsets.UTF_8)
        }.getOrNull()
        if (text == null || !text.contains("PRIVATE KEY")) {
            error = "That file isn't a private key (.pem)."
            return@rememberLauncherForActivityResult
        }
        pem = text
        fileName = name
        // The Control Panel names the key file after the Application ID.
        if (applicationId.isBlank()) name?.let { UUID_PATTERN.find(it)?.value }?.let { applicationId = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your own Enable Banking app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Register your own app at Enable Banking to connect your own bank accounts. " +
                        "Banks connected with another app need to be connected again afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = applicationId,
                    onValueChange = {
                        applicationId = it
                        error = null
                    },
                    label = { Text("Application ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { pickKey.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Text(fileName ?: "Choose private key (.pem)", modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { showGuide = true }) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                    Text("How to set up", modifier = Modifier.padding(start = 6.dp))
                }
                if (hasOwn) {
                    TextButton(onClick = {
                        EnableBankingCredentials.removeOwn()
                        onDismiss()
                    }) { Text("Stop using my own app") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val key = pem ?: return@TextButton
                    EnableBankingCredentials.saveOwn(applicationId, key)
                        .onSuccess { onDismiss() }
                        .onFailure { error = it.message ?: "Couldn't save." }
                },
                enabled = applicationId.isNotBlank() && pem != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showGuide) {
        SetupGuideDialog(guides = listOf(SetupGuides.ENABLE_BANKING), initial = SetupGuides.ENABLE_BANKING, onDismiss = { showGuide = false })
    }
}
