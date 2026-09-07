package com.financetracker.app.ui.screens.dashboard

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.prefs.AiInsightsCache
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.BudgetProgressRow
import com.financetracker.app.ui.components.CategoryBreakdownList
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.PeriodSelectorChip
import com.financetracker.app.ui.components.SummaryCard
import com.financetracker.app.ui.components.TransactionRow
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenAskAi: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val currencyCode by CurrencySettings.currencyCode.collectAsState()
    val isFiltered = state.selection.type != null || state.selection.categoryId != null
    val insight by AiInsightsCache.insight.collectAsState()
    val isGeneratingInsights by viewModel.isGeneratingInsights.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finance Tracker") },
                actions = {
                    if (ClaudeService.isConfigured) {
                        IconButton(onClick = onOpenAskAi) {
                            Icon(Icons.Filled.Chat, contentDescription = "Ask your finances")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Net Balance",
                    amount = Formatters.currency(state.netBalance, currencyCode),
                    selected = !isFiltered,
                    onClick = viewModel::clearSelection
                )
            }
            val budgetStatus = state.budgetStatus
            if (budgetStatus.overallBudget != null || budgetStatus.categoryStatuses.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Budget · This month", style = MaterialTheme.typography.titleMedium)
                            budgetStatus.overallBudget?.let { overallBudget ->
                                BudgetProgressRow(
                                    label = "Overall",
                                    spent = budgetStatus.overallSpent,
                                    budget = overallBudget,
                                    currencyCode = currencyCode,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            budgetStatus.categoryStatuses.forEach { catStatus ->
                                BudgetProgressRow(
                                    label = catStatus.categoryName,
                                    spent = catStatus.spent,
                                    budget = catStatus.budget,
                                    currencyCode = currencyCode,
                                    colorHex = catStatus.colorHex
                                )
                            }
                        }
                    }
                }
            }
            if (ClaudeService.isConfigured) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                    Text(
                                        text = "AI Insights",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                if (isGeneratingInsights) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = viewModel::generateInsights) {
                                        Text(if (insight == null) "Generate" else "Regenerate")
                                    }
                                }
                            }
                            if (insight != null) {
                                Text(
                                    text = insight ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            item {
                PeriodSelectorChip(
                    option = state.periodOption,
                    customRange = state.customRange,
                    onOptionSelected = viewModel::selectPeriod,
                    onCustomRangeSelected = viewModel::selectCustomRange
                )
            }
            item {
                val remaining = state.periodIncome - state.periodExpense
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Income",
                        amount = Formatters.currency(state.periodIncome, currencyCode),
                        valueColor = IncomeGreen,
                        selected = state.selection.type == com.financetracker.app.data.db.entity.TransactionType.INCOME,
                        onClick = viewModel::selectIncome,
                        amountStyle = MaterialTheme.typography.titleMedium,
                        contentPadding = 12.dp
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Expenses",
                        amount = Formatters.currency(state.periodExpense, currencyCode),
                        valueColor = ExpenseRed,
                        selected = state.selection.type == com.financetracker.app.data.db.entity.TransactionType.EXPENSE &&
                            state.selection.categoryId == null,
                        onClick = viewModel::selectExpense,
                        amountStyle = MaterialTheme.typography.titleMedium,
                        contentPadding = 12.dp
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Remaining",
                        amount = Formatters.currency(remaining, currencyCode),
                        valueColor = if (remaining >= 0) IncomeGreen else ExpenseRed,
                        amountStyle = MaterialTheme.typography.titleMedium,
                        contentPadding = 12.dp
                    )
                }
            }
            item {
                Text(text = "Spending by category", style = MaterialTheme.typography.titleMedium)
            }
            item {
                if (state.categoryBreakdown.isEmpty()) {
                    EmptyState(message = "No expenses recorded for this period.")
                } else {
                    Card {
                        CategoryBreakdownList(
                            categories = state.categoryBreakdown,
                            currencyCode = currencyCode,
                            selectedCategoryId = state.selection.categoryId,
                            onCategoryClick = { spend ->
                                viewModel.selectCategory(spend.categoryId, spend.categoryName)
                            },
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
                        text = state.selection.label?.let { "Transactions · $it" } ?: "Transactions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isFiltered) {
                        AssistChip(
                            onClick = viewModel::clearSelection,
                            label = { Text("Clear") },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) }
                        )
                    }
                }
            }
            if (state.transactions.isEmpty()) {
                item { EmptyState(message = "No transactions yet. Add one or import a spreadsheet.") }
            } else {
                items(state.transactions, key = { it.id }) { tx ->
                    Card { TransactionRow(transaction = tx, onClick = {}, onLongClick = {}, modifier = Modifier.padding(horizontal = 12.dp)) }
                }
            }
        }
    }
}
