package com.financetracker.app.ui.screens.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters

private val SPREADSHEET_MIME_TYPES = arrayOf("*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(viewModel: ImportExportViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onFilePicked(uri, uri.lastPathSegment)
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.exportTransactions(uri, ExportFormat.CSV) }

    val exportXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri -> if (uri != null) viewModel.exportTransactions(uri, ExportFormat.XLSX) }

    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissExportMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Import / Export") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("Import from spreadsheet", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Upload an Excel (.xlsx / .xls) or CSV file. We'll auto-detect columns like " +
                                "Date, Description, Category and Amount (or Debit/Credit).",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        AccountSelector(
                            accounts = state.accounts,
                            selectedId = state.selectedAccountId,
                            onSelected = viewModel::selectAccount
                        )

                        Button(
                            onClick = { filePickerLauncher.launch(SPREADSHEET_MIME_TYPES) },
                            enabled = state.selectedAccountId != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Text(text = " Choose file", modifier = Modifier.padding(start = 4.dp))
                        }

                        if (state.accounts.isEmpty()) {
                            Text(
                                "Add an account first (Accounts & Categories tab).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (state.isParsing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text("Reading ${state.selectedFileName ?: "file"}...")
                    }
                }
            }

            if (state.parsedRows.isNotEmpty() || state.parseErrors.isNotEmpty()) {
                item {
                    ImportPreview(
                        fileName = state.selectedFileName,
                        rowCount = state.parsedRows.size,
                        errors = state.parseErrors,
                        isImporting = state.isImporting,
                        onConfirm = { viewModel.confirmImport() },
                        onCancel = { viewModel.cancelPreview() }
                    )
                }
                items(state.parsedRows.take(50), key = { it.rowNumber }) { row ->
                    Card(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(row.note.ifBlank { row.categoryName }, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${row.categoryName} · ${Formatters.date(row.date)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val income = row.type == com.financetracker.app.data.db.entity.TransactionType.INCOME
                            Text(
                                (if (income) "+" else "-") + Formatters.currency(row.amount),
                                color = if (income) IncomeGreen else ExpenseRed
                            )
                        }
                    }
                }
                if (state.parsedRows.size > 50) {
                    item {
                        Text(
                            "+ ${state.parsedRows.size - 50} more rows",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text("Export", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Back up or share all of your transactions as a spreadsheet.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { exportCsvLauncher.launch("finance_export.csv") },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isExporting
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null)
                                Text(" CSV", modifier = Modifier.padding(start = 4.dp))
                            }
                            OutlinedButton(
                                onClick = { exportXlsxLauncher.launch("finance_export.xlsx") },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isExporting
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null)
                                Text(" Excel", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showResult) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IncomeGreen) },
            title = { Text("Import complete") },
            text = { Text("Added ${state.importedCount} transactions.") },
            confirmButton = { TextButton(onClick = viewModel::dismissResult) { Text("OK") } }
        )
    }
}

@Composable
private fun ImportPreview(
    fileName: String?,
    rowCount: Int,
    errors: List<String>,
    isImporting: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preview: ${fileName ?: "spreadsheet"}", style = MaterialTheme.typography.titleMedium)
            Text("$rowCount transactions ready to import", style = MaterialTheme.typography.bodyMedium)

            if (errors.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFF9A825))
                    Text(
                        " ${errors.size} row(s) skipped",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                errors.take(5).forEach {
                    Text("• $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onConfirm, enabled = rowCount > 0 && !isImporting, modifier = Modifier.weight(1f)) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Import $rowCount")
                }
                OutlinedButton(onClick = onCancel, enabled = !isImporting, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    accounts: List<com.financetracker.app.data.db.entity.Account>,
    selectedId: Long?,
    onSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = accounts.firstOrNull { it.id == selectedId }?.name ?: "Select account"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Import into account") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelected(account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
