package com.financetracker.app.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.PeriodSelectorChip
import com.financetracker.app.ui.components.TransactionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel) {
    val state by viewModel.uiState.collectAsState()

    var editingTransaction by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var searchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface {
                androidx.compose.foundation.layout.Column {
                    TopAppBar(
                        title = {
                            if (searchActive) {
                                OutlinedTextField(
                                    value = state.filter.searchQuery,
                                    onValueChange = viewModel::setSearchQuery,
                                    placeholder = { Text("Search note, category, amount") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                AccountFilterButton(
                                    accounts = state.allAccounts,
                                    selectedId = state.filter.selectedAccountId,
                                    onSelected = viewModel::selectAccount
                                )
                            }
                        },
                        actions = {
                            if (searchActive) {
                                IconButton(onClick = {
                                    searchActive = false
                                    viewModel.setSearchQuery("")
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                                }
                            } else {
                                IconButton(onClick = { searchActive = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search")
                                }
                            }
                        }
                    )
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        PeriodSelectorChip(
                            option = state.filter.periodOption,
                            customRange = state.filter.customRange,
                            onOptionSelected = viewModel::selectPeriod,
                            onCustomRangeSelected = viewModel::selectCustomRange
                        )
                    }
                }
            }
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
        if (state.displayItems.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                message = "No transactions match this view. Tap + to add one, or import a spreadsheet.",
                icon = Icons.Filled.ReceiptLong
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(state.displayItems) { item ->
                    when (item) {
                        is TransactionListItem.MonthHeader -> Text(
                            text = item.label,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                        is TransactionListItem.DateHeader -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.balanceLabel != null) {
                                Text(
                                    text = "Balance: ${item.balanceLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is TransactionListItem.Row -> Surface(shape = MaterialTheme.shapes.medium) {
                            TransactionRow(
                                transaction = item.tx,
                                runningBalance = item.runningBalance,
                                onClick = {
                                    editingTransaction = item.tx
                                    showAddEditDialog = true
                                },
                                onLongClick = { deleteTarget = item.tx },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditTransactionSheet(
            existing = editingTransaction,
            accounts = state.allAccounts,
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

@Composable
private fun AccountFilterButton(accounts: List<Account>, selectedId: Long?, onSelected: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = accounts.firstOrNull { it.id == selectedId }?.name ?: "All accounts"

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All accounts") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
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
