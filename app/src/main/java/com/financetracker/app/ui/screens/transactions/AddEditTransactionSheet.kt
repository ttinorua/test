package com.financetracker.app.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.util.Formatters
import com.financetracker.app.util.todayUtcMidnight
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    existing: TransactionWithDetails?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (amount: Double, type: TransactionType, accountId: Long, categoryId: Long?, date: Long, note: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currencyCode by CurrencySettings.currencyCode.collectAsState()

    var type by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var selectedAccountId by remember { mutableStateOf(existing?.accountId ?: accounts.firstOrNull()?.id) }
    var selectedCategoryId by remember { mutableStateOf(existing?.categoryId) }
    var dateMillis by remember { mutableStateOf(existing?.date ?: todayUtcMidnight()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var isSuggesting by remember { mutableStateOf(false) }
    var suggestError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val categoriesForType = categories.filter { it.type == type }

    fun requestAiSuggestion() {
        if (isSuggesting || note.isBlank()) return
        isSuggesting = true
        suggestError = null
        coroutineScope.launch {
            val categoryList = categories.joinToString("\n") { "${it.mainCategory}|${it.name}|${it.type}" }
            val systemPrompt =
                "You categorize personal finance transactions. Here are the user's existing " +
                    "categories as MainCategory|Subcategory|Type (Type is INCOME or EXPENSE):\n" +
                    categoryList +
                    "\n\nGiven a transaction description, reply with ONLY the best matching " +
                    "MainCategory|Subcategory from the list above, exactly as written, on a single " +
                    "line. Do not invent new categories. If nothing fits well, reply with " +
                    "Uncategorized|Uncategorized."
            ClaudeService.ask(systemPrompt, note, maxTokens = 60L)
                .onSuccess { reply ->
                    val parts = reply.trim().lines().first().split("|").map { it.trim() }
                    val match = if (parts.size == 2) {
                        categories.firstOrNull {
                            it.mainCategory.equals(parts[0], ignoreCase = true) &&
                                it.name.equals(parts[1], ignoreCase = true)
                        }
                    } else null
                    if (match != null) {
                        type = match.type
                        selectedCategoryId = match.id
                    } else {
                        suggestError = "Couldn't match a category. Try picking one manually."
                    }
                }
                .onFailure { suggestError = "AI suggestion failed: ${it.message}" }
            isSuggesting = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existing == null) "Add Transaction" else "Edit Transaction",
                style = MaterialTheme.typography.titleLarge
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TransactionType.EXPENSE,
                    onClick = {
                        type = TransactionType.EXPENSE
                        selectedCategoryId = null
                    },
                    label = { Text("Expense") }
                )
                FilterChip(
                    selected = type == TransactionType.INCOME,
                    onClick = {
                        type = TransactionType.INCOME
                        selectedCategoryId = null
                    },
                    label = { Text("Income") }
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    amountError = null
                },
                label = { Text("Amount") },
                leadingIcon = { Text(Formatters.currencySymbol(currencyCode)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            AccountDropdown(
                accounts = accounts,
                selectedId = selectedAccountId,
                onSelected = { selectedAccountId = it }
            )

            CategoryDropdown(
                categories = categoriesForType,
                selectedId = selectedCategoryId,
                onSelected = { selectedCategoryId = it }
            )

            OutlinedTextField(
                value = Formatters.date(dateMillis),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) { Text("Change") }
                }
            )

            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                    suggestError = null
                },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (ClaudeService.isConfigured && note.isNotBlank()) {
                OutlinedButton(
                    onClick = { requestAiSuggestion() },
                    enabled = !isSuggesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSuggesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = if (isSuggesting) "Thinking..." else "Suggest category with AI",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (suggestError != null) {
                    Text(
                        text = suggestError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = {
                    val amount = amountText.replace(",", "").toDoubleOrNull()
                    val accountId = selectedAccountId
                    when {
                        amount == null || amount <= 0.0 -> amountError = "Enter a valid amount"
                        accountId == null -> amountError = "Add an account first"
                        else -> onSave(amount, type, accountId, selectedCategoryId, dateMillis, note.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (existing == null) "Add" else "Save")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(accounts: List<Account>, selectedId: Long?, onSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = accounts.firstOrNull { it.id == selectedId }?.name ?: "Select account"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Account") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(categories: List<Category>, selectedId: Long?, onSelected: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    val selectedLabel = selected?.let { "${it.mainCategory} • ${it.name}" } ?: "Uncategorized"
    val sorted = categories.sortedWith(compareBy({ it.mainCategory }, { it.name }))

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Uncategorized") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            sorted.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${category.mainCategory} • ${category.name}") },
                    onClick = {
                        onSelected(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
