package com.financetracker.app.ui.screens.overview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.AccountSelectorChip
import com.financetracker.app.ui.components.CategoryFilterChip
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.IncomeExpenseBarChart
import com.financetracker.app.ui.components.PeriodSelectorChip
import com.financetracker.app.ui.components.TrendBarChart
import com.financetracker.app.ui.components.TrendSeries
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.util.CategoryFilter
import com.financetracker.app.util.Formatters
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.TrendGranularity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOverviewScreen(
    viewModel: CategoryOverviewViewModel,
    onOpenTransactions: (TransactionsDrillDown) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val currencyCode by CurrencySettings.currencyCode.collectAsState()
    val unit = if (state.granularity == TrendGranularity.MONTH) "month" else "year"

    fun openTrendPoint(index: Int) {
        val point = state.trend.getOrNull(index) ?: return
        onOpenTransactions(
            TransactionsDrillDown(
                title = "${state.categoryFilter.takeIf { it != CategoryFilter.All }?.label?.let { "$it · " } ?: ""}${point.longLabel}",
                periodOption = PeriodOption.CUSTOM,
                customRange = point.from to point.to,
                accountId = state.selectedAccountId,
                type = TransactionType.EXPENSE,
                categoryFilter = state.categoryFilter
            )
        )
    }

    fun openEntry(key: String) {
        onOpenTransactions(
            TransactionsDrillDown(
                title = key,
                periodOption = state.periodOption,
                customRange = state.customRange,
                accountId = state.selectedAccountId,
                groupBy = state.groupBy,
                key = key,
                type = TransactionType.EXPENSE,
                categoryFilter = state.categoryFilter
            )
        )
    }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccountSelectorChip(
                        accounts = state.accounts,
                        selectedAccountId = state.selectedAccountId,
                        onAccountSelected = viewModel::selectAccount
                    )
                    CategoryFilterChip(
                        categories = state.categories,
                        selected = state.categoryFilter,
                        onSelected = viewModel::selectCategoryFilter
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Spending over time",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            GranularityToggle(state.granularity, viewModel::selectGranularity)
                        }
                        Text(
                            "Average ${Formatters.currency(state.averageExpense, currencyCode)} per $unit · tap a bar to see its transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        TrendBarChart(
                            labels = state.trend.map { it.label },
                            subLabels = state.trend.map { it.subLabel },
                            series = listOf(TrendSeries("Expense", ExpenseRed, state.trend.map { it.expense })),
                            onBarClick = { index, _ -> openTrendPoint(index) }
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    PeriodSelectorChip(
                        option = state.periodOption,
                        customRange = state.customRange,
                        onOptionSelected = viewModel::selectPeriod,
                        onCustomRangeSelected = viewModel::selectCustomRange
                    )
                }
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
                Text(
                    "Spent: ${Formatters.currency(state.totalExpense, currencyCode)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ExpenseRed
                )
            }
            if (state.entries.isEmpty()) {
                item {
                    EmptyState(message = "No spending in this period.", icon = Icons.Filled.PieChart)
                }
            } else {
                item {
                    Card {
                        IncomeExpenseBarChart(
                            entries = state.entries,
                            selectedKey = null,
                            onSelect = { key -> key?.let { openEntry(it) } },
                            formatValue = { Formatters.compact(it) },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(state.entries, key = { it.key }) { entry ->
                    Card(
                        onClick = { openEntry(entry.key) },
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

/** Monthly / Yearly switch for a trend chart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GranularityToggle(selected: TrendGranularity, onSelected: (TrendGranularity) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        TrendGranularity.entries.forEachIndexed { index, granularity ->
            SegmentedButton(
                selected = selected == granularity,
                onClick = { onSelected(granularity) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TrendGranularity.entries.size),
                icon = {}
            ) {
                Text(granularity.label)
            }
        }
    }
}
