package com.financetracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.ui.screens.transactions.AddEditTransactionSheet

/**
 * A drill-down window: a title, an editable transaction list (tap to edit, long-press to
 * delete, FAB to add), and a close button back to the caller — the same editing affordances
 * as the main Transactions tab, available from any grouped/filtered view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsPopupScreen(
    title: String,
    transactions: List<TransactionWithDetails>,
    accounts: List<Account>,
    categories: List<Category>,
    onClose: () -> Unit,
    onAddTransaction: (
        amount: Double,
        type: TransactionType,
        accountId: Long,
        categoryId: Long?,
        date: Long,
        note: String
    ) -> Unit,
    onUpdateTransaction: (
        id: Long,
        amount: Double,
        type: TransactionType,
        accountId: Long,
        categoryId: Long?,
        date: Long,
        note: String
    ) -> Unit,
    onDeleteTransaction: (TransactionWithDetails) -> Unit
) {
    var editingTransaction by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TransactionWithDetails?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTransaction = null
                showAddEditDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                message = "No transactions in this period."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val count = transactions.size
                    Text(
                        text = "$count transaction${if (count == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(transactions, key = { it.id }) { tx ->
                    Card {
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
            accounts = accounts,
            categories = categories,
            onDismiss = { showAddEditDialog = false },
            onSave = { amount, type, accountId, categoryId, date, note ->
                val current = editingTransaction
                if (current == null) {
                    onAddTransaction(amount, type, accountId, categoryId, date, note)
                } else {
                    onUpdateTransaction(current.id, amount, type, accountId, categoryId, date, note)
                }
                showAddEditDialog = false
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete transaction?") },
            text = {
                Text(
                    "This will permanently remove \"${target.note.ifBlank { target.categoryName ?: "this transaction" }}\"."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTransaction(target)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
