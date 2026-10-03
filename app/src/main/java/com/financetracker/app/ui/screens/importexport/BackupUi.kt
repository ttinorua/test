package com.financetracker.app.ui.screens.importexport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.backup.AutoBackupState
import com.financetracker.app.data.backup.BackupDestination
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters

@Composable
fun FullBackupCard(isWorking: Boolean, onCreate: () -> Unit, onRestore: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Saves everything in the app — accounts, transactions, categories, budgets, Fixe " +
                    "categories, learned rules and settings — to one file. Keep it somewhere safe " +
                    "(e.g. Google Drive). After reinstalling, restore it instead of syncing everything again.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "With a password the file is encrypted and also keeps your bank connections, so " +
                    "syncing simply carries on after a restore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isWorking) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Working…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCreate, enabled = !isWorking, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Back up", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onRestore, enabled = !isWorking, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Restore", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Asks for the password to protect a new backup with (both fields empty means no password). */
@Composable
fun CreateBackupDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
    title: String = "Protect the backup"
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val mismatch = password != repeat
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose a password to encrypt the backup and include your bank connections. " +
                        "You'll need it to restore — it can't be recovered if forgotten.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Leave both empty for an unencrypted backup without bank connections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordField(value = password, onValueChange = { password = it }, label = "Password")
                PasswordField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = "Repeat password",
                    error = if (mismatch && repeat.isNotEmpty()) "Passwords don't match" else null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = !mismatch) {
                Text(if (password.isEmpty()) "Back up without password" else "Choose where to save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun UnlockBackupDialog(wrongPassword: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This backup is encrypted. Enter the password it was saved with.")
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    error = if (wrongPassword) "Wrong password" else null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = password.isNotEmpty()) {
                Text("Unlock")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmRestoreDialog(pending: PendingRestore, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace everything with this backup?") },
        text = {
            Text(
                "Backup from ${Formatters.fullDate(pending.createdAt)}: ${pending.transactionCount} " +
                    "transactions in ${pending.accountCount} accounts" +
                    (if (pending.includesBankConnections) ", with bank connections" else "") +
                    ".\n\nEverything currently in the app is replaced by the backup. The app restarts " +
                    "when it's done."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RestoreCompleteDialog(onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IncomeGreen) },
        title = { Text("Restore complete") },
        text = { Text("The app will now restart with your restored data.") },
        confirmButton = { TextButton(onClick = onRestart) { Text("Restart") } }
    )
}

/** Where the weekly backup should go. OneDrive is offered only when the app is set up for it. */
@Composable
fun ChooseBackupDestinationDialog(
    oneDriveAvailable: Boolean,
    onDismiss: () -> Unit,
    onChoose: (BackupDestination) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where should the backup go?") },
        text = {
            Column {
                BackupDestination.entries.forEach { destination ->
                    val enabled = destination != BackupDestination.ONEDRIVE || oneDriveAvailable
                    ListItem(
                        headlineContent = { Text(destination.label) },
                        supportingContent = {
                            Text(
                                when (destination) {
                                    BackupDestination.ONEDRIVE ->
                                        if (enabled) "Sign in with Microsoft. Saved in OneDrive > Apps." else "Not set up in this version of the app yet."
                                    BackupDestination.GOOGLE_DRIVE -> "Choose your Google account. Saved in My Drive."
                                    BackupDestination.PHONE -> "Pick a folder, e.g. Documents. Survives reinstalling the app, not losing the phone."
                                }
                            )
                        },
                        leadingContent = {
                            Icon(
                                when (destination) {
                                    BackupDestination.PHONE -> Icons.Filled.PhoneAndroid
                                    else -> Icons.Filled.Cloud
                                },
                                contentDescription = null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable(enabled = enabled) { onChoose(destination) }
                            .alpha(if (enabled) 1f else 0.4f)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AutoBackupCard(
    state: AutoBackupState,
    onToggle: (Boolean) -> Unit,
    onBackUpNow: () -> Unit,
    onChangeLocation: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Automatic weekly backup", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.enabled) {
                            "On" + if (state.passwordProtected) " · encrypted, with bank connections" else " · not encrypted"
                        } else {
                            "Off"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = onToggle)
            }
            if (!state.enabled) {
                Text(
                    "Once a week the app saves a fresh backup to OneDrive, Google Drive or this " +
                        "phone — you choose when you turn this on. It replaces the previous one, so " +
                        "it always holds your latest data; OneDrive and Google Drive also keep " +
                        "earlier versions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.locationLabel?.let {
                    Text("Saving to: $it", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Last backup: " + (state.lastSuccessAt?.let { Formatters.syncTimestamp(it) } ?: "not yet"),
                    style = MaterialTheme.typography.bodyMedium
                )
                state.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBackUpNow, modifier = Modifier.weight(1f)) { Text("Back up now") }
                    OutlinedButton(onClick = onChangeLocation, modifier = Modifier.weight(1f)) { Text("Change location") }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}
