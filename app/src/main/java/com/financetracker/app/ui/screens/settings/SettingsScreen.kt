package com.financetracker.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.bank.Bank
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.BudgetLimits
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.prefs.LinkedBankAccount
import com.financetracker.app.data.prefs.SUPPORTED_CURRENCIES
import com.financetracker.app.ui.components.AccountSelectorChip
import com.financetracker.app.ui.components.CategoryColorDot
import com.financetracker.app.ui.screens.importexport.ImportExportScreen
import com.financetracker.app.ui.screens.importexport.ImportExportViewModel
import com.financetracker.app.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    enableBankingViewModel: EnableBankingViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val currencyCode by CurrencySettings.currencyCode.collectAsState()
    val shiftSalaryToNextMonth by BudgetSettings.shiftSalaryToNextMonth.collectAsState()
    val excludeTransfersFromSpending by BudgetSettings.excludeTransfersFromSpending.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

    var showAddAccount by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var deleteAccountTarget by remember { mutableStateOf<Account?>(null) }
    var editAccountTarget by remember { mutableStateOf<Account?>(null) }
    var deleteCategoryTarget by remember { mutableStateOf<Category?>(null) }
    var collapsedMains by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        floatingActionButton = {
            if (tabIndex == 0 || tabIndex == 1) {
                FloatingActionButton(onClick = {
                    if (tabIndex == 0) showAddAccount = true else showAddCategory = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 12.dp) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Accounts", maxLines = 1) }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Categories", maxLines = 1) }
                )
                Tab(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    text = { Text("Budgets", maxLines = 1) }
                )
                Tab(
                    selected = tabIndex == 3,
                    onClick = { tabIndex = 3 },
                    text = { Text("Import/Export", maxLines = 1) }
                )
                Tab(
                    selected = tabIndex == 4,
                    onClick = { tabIndex = 4 },
                    text = { Text("General", maxLines = 1) }
                )
                Tab(
                    selected = tabIndex == 5,
                    onClick = { tabIndex = 5 },
                    text = { Text("Bank", maxLines = 1) }
                )
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
                                IconButton(onClick = { editAccountTarget = accountUi.account }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename account")
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

                2 -> BudgetsTab(
                    categories = state.categories.filter { it.type == TransactionType.EXPENSE },
                    accounts = state.accounts.map { it.account },
                    currencyCode = currencyCode
                )

                3 -> ImportExportScreen(importExportViewModel)

                5 -> BankTab(enableBankingViewModel)

                else -> Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
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

                    Text(
                        "Exclude transfers from spending",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                    Text(
                        "Expenses categorized as Other • \"Other (Transfer)\" (moving money to " +
                            "another of your own accounts, e.g. savings) are left out of income/" +
                            "expense totals, budgets, and the spending breakdown, since they " +
                            "aren't real spending. The account register still shows every " +
                            "transaction as normal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = excludeTransfersFromSpending,
                            onCheckedChange = { BudgetSettings.setExcludeTransfersFromSpending(it) }
                        )
                        Text(
                            text = if (excludeTransfersFromSpending) "On" else "Off",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Text(
                        "AI categorization",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                    Text(
                        "One-time cleanup for transactions with no real category (mainly bank " +
                            "sync history, since it carries no category data at all). Uses your " +
                            "Anthropic API key and can take a while for a large history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
                    )

                    val isCategorizing by viewModel.isCategorizing.collectAsState()
                    val categorizationProgress by viewModel.categorizationProgress.collectAsState()
                    val categorizationMessage by viewModel.categorizationMessage.collectAsState()

                    categorizationMessage?.let { message ->
                        Card(modifier = Modifier.padding(bottom = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.dismissCategorizationMessage() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    if (isCategorizing) {
                        val progress = categorizationProgress
                        if (progress != null && progress.total > 0) {
                            LinearProgressIndicator(
                                progress = { progress.done.toFloat() / progress.total },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            )
                            Text(
                                "${progress.done} of ${progress.total}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { viewModel.categorizeWithAi() }, enabled = !isCategorizing) {
                            Text(if (isCategorizing) "Categorizing…" else "Categorize with AI")
                        }
                        if (isCategorizing) {
                            TextButton(
                                onClick = { viewModel.cancelCategorization() },
                                modifier = Modifier.padding(start = 8.dp)
                            ) { Text("Cancel") }
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
            onConfirm = { mainCategory, name, type ->
                viewModel.addCategory(mainCategory, name, type)
                showAddCategory = false
            }
        )
    }

    editAccountTarget?.let { account ->
        RenameAccountDialog(
            currentName = account.name,
            onDismiss = { editAccountTarget = null },
            onConfirm = { newName ->
                viewModel.updateAccount(account.copy(name = newName))
                editAccountTarget = null
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
private fun BudgetsTab(categories: List<Category>, accounts: List<Account>, currencyCode: String) {
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    val overallBudgets by BudgetLimits.overallBudgets.collectAsState()
    val allCategoryBudgets by BudgetLimits.categoryBudgets.collectAsState()
    val overallBudget = overallBudgets[selectedAccountId]
    val categoryBudgets = remember(allCategoryBudgets, selectedAccountId) {
        allCategoryBudgets.filterKeys { it.second == selectedAccountId }.mapKeys { it.key.first }
    }
    var overallText by remember(selectedAccountId) {
        mutableStateOf(overallBudget?.let { Formatters.amount(it) } ?: "")
    }
    var pendingCategoryIds by remember(selectedAccountId) { mutableStateOf(setOf<Long>()) }

    val categoryById = categories.associateBy { it.id }
    val activeCategories = (categoryBudgets.keys + pendingCategoryIds)
        .mapNotNull { categoryById[it] }
        .distinctBy { it.id }
        .sortedWith(compareBy({ it.mainCategory }, { it.name }))
    val availableCategories = categories
        .filterNot { it.id in categoryBudgets.keys || it.id in pendingCategoryIds }
        .sortedWith(compareBy({ it.mainCategory }, { it.name }))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                "Budgets are set per account (or for \"All accounts\" combined).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            AccountSelectorChip(
                accounts = accounts,
                selectedAccountId = selectedAccountId,
                onAccountSelected = { selectedAccountId = it }
            )
        }
        item {
            Text(
                "Overall monthly budget",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "A single spending limit across all expenses combined, compared against " +
                    "this calendar month's spending so far.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
            )
            OutlinedTextField(
                value = overallText,
                onValueChange = { text ->
                    overallText = text
                    val amount = text.replace(",", "").toDoubleOrNull()
                    BudgetLimits.setOverallBudget(selectedAccountId, if (text.isBlank()) null else amount)
                },
                label = { Text("Amount") },
                placeholder = { Text("No limit set") },
                leadingIcon = { Text(Formatters.currencySymbol(currencyCode)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                "Category budgets",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                "Optional monthly limits per category, shown on the dashboard once set.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
            )
            AddCategoryBudgetSelector(
                availableCategories = availableCategories,
                onCategorySelected = { category -> pendingCategoryIds = pendingCategoryIds + category.id }
            )
        }
        if (activeCategories.isEmpty()) {
            item {
                Text(
                    "No category budgets set yet. Search above to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            items(activeCategories, key = { it.id }) { category ->
                CategoryBudgetRow(
                    category = category,
                    currentBudget = categoryBudgets[category.id],
                    currencyCode = currencyCode,
                    onBudgetChanged = { amount -> BudgetLimits.setCategoryBudget(category.id, selectedAccountId, amount) },
                    onRemove = {
                        BudgetLimits.setCategoryBudget(category.id, selectedAccountId, null)
                        pendingCategoryIds = pendingCategoryIds - category.id
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryBudgetSelector(
    availableCategories: List<Category>,
    onCategorySelected: (Category) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, availableCategories) {
        if (query.isBlank()) {
            availableCategories
        } else {
            availableCategories.filter {
                it.name.contains(query, ignoreCase = true) || it.mainCategory.contains(query, ignoreCase = true)
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(top = 8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text("Add a category budget") },
            placeholder = { Text("Search categories…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filtered.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryColorDot(category.colorHex, modifier = Modifier.size(12.dp))
                            Text(
                                text = "${category.mainCategory} • ${category.name}",
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    },
                    onClick = {
                        onCategorySelected(category)
                        query = ""
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(
    category: Category,
    currentBudget: Double?,
    currencyCode: String,
    onBudgetChanged: (Double?) -> Unit,
    onRemove: () -> Unit
) {
    var text by remember(category.id) { mutableStateOf(currentBudget?.let { Formatters.amount(it) } ?: "") }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryColorDot(category.colorHex, modifier = Modifier.size(12.dp))
                Text(
                    text = "${category.mainCategory} • ${category.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove budget", modifier = Modifier.size(18.dp))
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    val amount = input.replace(",", "").toDoubleOrNull()
                    onBudgetChanged(if (input.isBlank()) null else amount)
                },
                label = { Text("Amount") },
                placeholder = { Text("No limit set") },
                leadingIcon = { Text(Formatters.currencySymbol(currencyCode)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankDropdown(
    banks: List<Bank>,
    selected: Bank,
    onSelected: (Bank) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Bank") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            banks.forEach { bank ->
                DropdownMenuItem(
                    text = { Text(bank.displayName) },
                    onClick = {
                        onSelected(bank)
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

/** Renaming only ever changes the account's display name (its id stays the same), so its
 * transactions, budgets and Enable Banking link are all unaffected — nothing else in the app
 * keys off this name. */
@Composable
private fun RenameAccountDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename account") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != currentName
            ) { Text("Save") }
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

@Composable
private fun BankTab(viewModel: EnableBankingViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.authUrl) {
        state.authUrl?.let { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            viewModel.consumeAuthUrl()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Bank sync", style = MaterialTheme.typography.titleMedium)
        Text(
            "Link a bank account via Enable Banking (open banking / PSD2) to pull in " +
                "transactions automatically instead of exporting and importing a spreadsheet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp, top = 4.dp)
        )

        if (!state.isConfigured) {
            Text(
                "Not configured. Add ENABLE_BANKING_APPLICATION_ID and " +
                    "ENABLE_BANKING_PRIVATE_KEY_B64 to local.properties and rebuild.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            return@Column
        }

        state.statusMessage?.let { message ->
            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.dismissStatusMessage() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (!state.isConnected) {
            val selectedBank = state.availableBanks.firstOrNull { it.id == state.selectedBankId }
                ?: state.availableBanks.first()
            BankDropdown(
                banks = state.availableBanks,
                selected = selectedBank,
                onSelected = { bank -> viewModel.selectBank(bank.id) },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(onClick = { viewModel.connect() }, enabled = !state.isStartingAuth) {
                if (state.isStartingAuth) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Connect ${selectedBank.displayName}")
                }
            }
            return@Column
        }

        state.consentValidUntil?.let { validUntil ->
            Text(
                "Access valid until ${formatBankDate(validUntil)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            state.lastSyncedAt?.let { "Last synced ${formatBankDate(it)}" } ?: "Never synced",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, top = 2.dp)
        )

        Text(
            "Accounts to sync",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.linkedAccounts, key = { it.uid }) { account ->
                BankAccountRow(
                    account = account,
                    selected = account.uid in state.selectedAccountUids,
                    onToggle = { checked -> viewModel.setAccountSelected(account.uid, checked) }
                )
            }
        }

        if (state.isSyncing) {
            val progress = state.syncProgress
            when {
                progress != null && progress.total > 0 -> {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                    Text(
                        "${progress.done} of ${progress.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                // total == 0: still paginating through the bank's full history — this can be
                // the slowest, least visible part of a first sync, so show it's moving even
                // though there's no known denominator yet.
                progress != null && progress.total == 0 && progress.done > 0 -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                    Text(
                        "Fetching from bank… ${progress.done} transaction(s) so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                else -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { viewModel.syncNow() }, enabled = !state.isSyncing, modifier = Modifier.weight(1f)) {
                if (state.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Sync now")
                }
            }
            if (state.isSyncing) {
                TextButton(onClick = { viewModel.cancelSync() }) {
                    Text("Cancel")
                }
            }
            OutlinedButton(onClick = { showDisconnectConfirm = true }) {
                Text("Disconnect")
            }
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect Sydbank?") },
            text = { Text("You'll need to log in with MitID again to reconnect. Already-synced transactions are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disconnect()
                    showDisconnectConfirm = false
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BankAccountRow(account: LinkedBankAccount, selected: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!selected) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = onToggle)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                // Sydbank reuses the same product label (e.g. "Privatkonto") across more than
                // one account, so a suffix is included here too, not just in the underlying
                // local account name, or two accounts would look identical in this list.
                val suffix = account.iban?.takeLast(4) ?: account.uid.take(6)
                Text(
                    "${account.product ?: account.name} ••$suffix",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${account.iban ?: account.uid} • ${account.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatBankDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMillis))
