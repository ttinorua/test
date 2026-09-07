package com.financetracker.app.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.prefs.SUPPORTED_CURRENCIES
import com.financetracker.app.ui.components.CategoryColorDot
import com.financetracker.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val currencyCode by CurrencySettings.currencyCode.collectAsState()
    val shiftSalaryToNextMonth by BudgetSettings.shiftSalaryToNextMonth.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

    var showAddAccount by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var deleteAccountTarget by remember { mutableStateOf<Account?>(null) }
    var deleteCategoryTarget by remember { mutableStateOf<Category?>(null) }
    var collapsedMains by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Accounts & Categories") }) },
        floatingActionButton = {
            if (tabIndex != 2) {
                FloatingActionButton(onClick = {
                    if (tabIndex == 0) showAddAccount = true else showAddCategory = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Accounts") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Categories") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("General") })
            }

            when (tabIndex) {
                0 -> LazyColumn(
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
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        accountUi.account.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        Formatters.currency(accountUi.balance, currencyCode),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { deleteAccountTarget = accountUi.account }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete account")
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val grouped = state.categories.groupBy { it.mainCategory }.toSortedMap()
                    val allExpanded = collapsedMains.isEmpty()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item(key = "expand_all_button") {
                            TextButton(
                                onClick = {
                                    collapsedMains = if (allExpanded) grouped.keys.toSet() else emptySet()
                                },
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (allExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                    contentDescription = null
                                )
                                Text(
                                    text = if (allExpanded) "Collapse all" else "Expand all",
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        grouped.forEach { (mainCategory, subcategories) ->
                            val isExpanded = mainCategory !in collapsedMains
                            item(key = "header_$mainCategory") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            collapsedMains = if (isExpanded) {
                                                collapsedMains + mainCategory
                                            } else {
                                                collapsedMains - mainCategory
                                            }
                                        }
                                        .padding(top = 12.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$mainCategory (${subcategories.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                            if (isExpanded) {
                                items(subcategories, key = { it.id }) { category ->
                                Card(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CategoryColorDot(category.colorHex, modifier = Modifier.size(12.dp))
                                            Text(
                                                category.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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

                else -> Column(modifier = Modifier.padding(16.dp)) {
                    Text("Display currency", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Amounts are shown in this currency. This doesn't convert existing values.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
                    )
                    CurrencyDropdown(
                        selected = currencyCode,
                        onSelected = { CurrencySettings.setCurrencyCode(it) }
                    )

                    Text(
                        "Shift salary to next month",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                    Text(
                        "Income categorized as Income • \"Pay, benefits and pension\" (a salary " +
                            "paid at month-end) counts toward next month's totals instead — the " +
                            "transaction keeps its real payment date, only dashboard and " +
                            "spending-overview totals shift.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = shiftSalaryToNextMonth,
                            onCheckedChange = { BudgetSettings.setShiftSalaryToNextMonth(it) }
                        )
                        Text(
                            text = if (shiftSalaryToNextMonth) "On" else "Off",
                            modifier = Modifier.padding(start = 8.dp)
                        )
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
            onConfirm = { mainCategory, name, type ->
                viewModel.addCategory(mainCategory, name, type)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SUPPORTED_CURRENCIES.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    }
                )
            }
        }
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
private fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, String, TransactionType) -> Unit) {
    var mainCategory by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = mainCategory,
                    onValueChange = { mainCategory = it },
                    label = { Text("Main category (e.g. Food)") },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Subcategory name") })
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
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(mainCategory.trim().ifBlank { "Uncategorized" }, name.trim(), type)
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
