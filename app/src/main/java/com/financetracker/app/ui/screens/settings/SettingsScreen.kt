package com.financetracker.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.ui.components.CategoryColorDot
import com.financetracker.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

    var showAddAccount by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var deleteAccountTarget by remember { mutableStateOf<Account?>(null) }
    var deleteCategoryTarget by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Accounts & Categories") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (tabIndex == 0) showAddAccount = true else showAddCategory = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Accounts") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Categories") })
            }

            if (tabIndex == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.accounts, key = { it.account.id }) { accountUi ->
                        Card(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(accountUi.account.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        Formatters.currency(accountUi.balance),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { deleteAccountTarget = accountUi.account }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete account")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.categories, key = { it.id }) { category ->
                        Card(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryColorDot(category.colorHex, modifier = Modifier.size(12.dp))
                                    Text(
                                        category.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (category.type == TransactionType.INCOME) "Income" else "Expense",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = { deleteCategoryTarget = category }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete category")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onConfirm = { name, balance ->
                viewModel.addAccount(name, balance)
                showAddAccount = false
            }
        )
    }

    if (showAddCategory) {
        AddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onConfirm = { name, type ->
                viewModel.addCategory(name, type)
                showAddCategory = false
            }
        )
    }

    deleteAccountTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteAccountTarget = null },
            title = { Text("Delete \"${account.name}\"?") },
            text = { Text("Its transactions will also be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(account)
                    deleteAccountTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteAccountTarget = null }) { Text("Cancel") } }
        )
    }

    deleteCategoryTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteCategoryTarget = null },
            title = { Text("Delete \"${category.name}\"?") },
            text = { Text("Transactions using it will become uncategorized.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(category)
                    deleteCategoryTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCategoryTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Account") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Starting balance") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val balance = balanceText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) onConfirm(name.trim(), balance)
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, TransactionType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == TransactionType.EXPENSE,
                        onClick = { type = TransactionType.EXPENSE },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = type == TransactionType.INCOME,
                        onClick = { type = TransactionType.INCOME },
                        label = { Text("Income") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), type) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
