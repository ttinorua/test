package com.financetracker.app.ui.screens.overview

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.IncomeExpenseBarChart
import com.financetracker.app.ui.components.PeriodSelectorChip
import com.financetracker.app.ui.components.TransactionRow
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters
import com.financetracker.app.util.GroupByOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOverviewScreen(viewModel: CategoryOverviewViewModel) {
    val state by viewModel.uiState.collectAsState()
    val currencyCode by CurrencySettings.currencyCode.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spending Overview") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodSelectorChip(
                    option = state.periodOption,
                    customRange = state.customRange,
                    onOptionSelected = viewModel::selectPeriod,
                    onCustomRangeSelected = viewModel::selectCustomRange
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupByOption.entries.forEach { option ->
                        FilterChip(
                            selected = state.groupBy == option,
                            onClick = { viewModel.selectGroupBy(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Income: ${Formatters.currency(state.totalIncome, currencyCode)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IncomeGreen
                    )
                    Text(
                        "Expense: ${Formatters.currency(state.totalExpense, currencyCode)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseRed
                    )
                }
            }
            item {
                if (state.entries.isEmpty()) {
                    EmptyState(message = "No transactions in this period.", icon = Icons.Filled.PieChart)
                } else {
                    Card {
                        IncomeExpenseBarChart(
                            entries = state.entries,
                            selectedKey = state.selectedKey,
                            onSelect = viewModel::toggleSelection,
                            formatValue = { Formatters.currency(it, currencyCode) },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (state.selectedKey != null) {
                            "Transactions · ${state.selectedKey}"
                        } else {
                            "Transactions (${state.transactions.size})"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (state.selectedKey != null) {
                        AssistChip(
                            onClick = { viewModel.toggleSelection(state.selectedKey) },
                            label = { Text("Clear") },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) }
                        )
                    }
                }
            }
            if (state.transactions.isEmpty()) {
                item { EmptyState(message = "No transactions to show.") }
            } else {
                items(state.transactions, key = { it.id }) { tx ->
                    Card(modifier = Modifier.padding(vertical = 2.dp)) {
                        TransactionRow(
                            transaction = tx,
                            onClick = {},
                            onLongClick = {},
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
