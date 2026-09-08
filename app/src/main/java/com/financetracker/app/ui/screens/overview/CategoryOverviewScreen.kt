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
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.IncomeExpenseBarChart
import com.financetracker.app.ui.components.PeriodSelectorChip
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOverviewScreen(
    viewModel: CategoryOverviewViewModel,
    onEntryClick: (groupBy: GroupByOption, key: String, periodOption: PeriodOption, customRange: Pair<Long, Long>?) -> Unit
) {
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
            if (state.entries.isEmpty()) {
                item {
                    EmptyState(message = "No transactions in this period.", icon = Icons.Filled.PieChart)
                }
            } else {
                item {
                    Card {
                        IncomeExpenseBarChart(
                            entries = state.entries,
                            selectedKey = null,
                            onSelect = { key ->
                                key?.let { onEntryClick(state.groupBy, it, state.periodOption, state.customRange) }
                            },
                            formatValue = { Formatters.currency(it, currencyCode) },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                item {
                    Text(
                        text = "By ${state.groupBy.label.lowercase()}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(state.entries, key = { it.key }) { entry ->
                    Card(
                        onClick = { onEntryClick(state.groupBy, entry.key, state.periodOption, state.customRange) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                if (entry.income > 0) {
                                    Text(
                                        text = "+${Formatters.currency(entry.income, currencyCode)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = IncomeGreen,
                                        maxLines = 1
                                    )
                                }
                                if (entry.expense > 0) {
                                    Text(
                                        text = "-${Formatters.currency(entry.expense, currencyCode)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ExpenseRed,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
