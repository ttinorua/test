package com.financetracker.app.ui.screens.transactions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.TransactionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel) {
    val state by viewModel.uiState.collectAsState()

    var editingTransaction by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TransactionWithDetails?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Transactions") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTransaction = null
                showAddEditDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        if (state.transactions.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                message = "No transactions yet. Tap + to add one, or import a spreadsheet from the Import/Export tab.",
                icon = Icons.Filled.ReceiptLong
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(state.transactions, key = { it.id }) { tx ->
                    Card(modifier = Modifier.padding(vertical = 4.dp)) {
                        TransactionRow(
                            transaction = tx,
                            onClick = {
                                editingTransaction = tx
                                showAddEditDialog = true
                            },
                            onLongClick = { deleteTarget = tx },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditTransactionSheet(
            existing = editingTransaction,
            accounts = state.accounts,
            categories = state.categories,
            onDismiss = { showAddEditDialog = false },
            onSave = { amount, type, accountId, categoryId, date, note ->
                val current = editingTransaction
                if (current == null) {
                    viewModel.addTransaction(amount, type, accountId, categoryId, date, note)
                } else {
                    viewModel.updateTransaction(current.id, amount, type, accountId, categoryId, date, note)
                }
                showAddEditDialog = false
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete transaction?") },
            text = { Text("This will permanently remove \"${target.note.ifBlank { target.categoryName ?: "this transaction" }}\".") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(target)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}
