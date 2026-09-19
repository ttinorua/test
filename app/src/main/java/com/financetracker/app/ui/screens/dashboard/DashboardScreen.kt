package com.financetracker.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.ai.InsightCard
import com.financetracker.app.data.ai.InsightTone
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.AiInsightsCache
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.AccountSelectorChip
import com.financetracker.app.ui.components.BudgetProgressRow
import com.financetracker.app.ui.components.CategoryBreakdownList
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.PeriodSelectorChip
import com.financetracker.app.ui.components.SummaryCard
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters
import com.financetracker.app.util.PeriodOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenAskAi: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenTransactions: (
        type: TransactionType?,
        categoryId: Long?,
        label: String,
        periodOption: PeriodOption,
        customRange: Pair<Long, Long>?
    ) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val currencyCode by CurrencySettings.currencyCode.collectAsState()
    val insights by AiInsightsCache.insights.collectAsState()
    val isGeneratingInsights by viewModel.isGeneratingInsights.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finance Tracker") },
                actions = {
                    IconButton(onClick = onOpenTrends) {
                        Icon(Icons.Filled.ShowChart, contentDescription = "Trends")
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PeriodSelectorChip(
                        option = state.periodOption,
                        customRange = state.customRange,
                        onOptionSelected = viewModel::selectPeriod,
                        onCustomRangeSelected = viewModel::selectCustomRange
                    )
                    AccountSelectorChip(
                        accounts = state.accounts,
                        selectedAccountId = state.selectedAccountId,
                        onAccountSelected = viewModel::selectAccount
                    )
                }
            }
            item {
                SummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Net Balance",
                    amount = Formatters.currency(state.netBalance, currencyCode),
                    onClick = {
                        onOpenTransactions(null, null, "All Transactions", state.periodOption, state.customRange)
                    }
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
                        onClick = {
                            onOpenTransactions(
                                TransactionType.INCOME,
                                null,
                                "Income",
                                state.periodOption,
                                state.customRange
                            )
                        },
                        amountStyle = MaterialTheme.typography.titleSmall,
                        contentPadding = 12.dp
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Expenses",
                        amount = Formatters.currency(state.periodExpense, currencyCode),
                        valueColor = ExpenseRed,
                        onClick = {
                            onOpenTransactions(
                                TransactionType.EXPENSE,
                                null,
                                "Expenses",
                                state.periodOption,
                                state.customRange
                            )
                        },
                        amountStyle = MaterialTheme.typography.titleSmall,
                        contentPadding = 12.dp,
                        subtitle = state.anticipatedRecurringExpense.takeIf { it > 0 }?.let {
                            "incl. ${Formatters.amount(it)} upcoming"
                        }
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Remaining",
                        amount = Formatters.currency(remaining, currencyCode),
                        valueColor = if (remaining >= 0) IncomeGreen else ExpenseRed,
                        amountStyle = MaterialTheme.typography.titleSmall,
                        contentPadding = 12.dp
                    )
                }
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
                                    modifier = Modifier.padding(top = 8.dp),
                                    onClick = {
                                        onOpenTransactions(
                                            TransactionType.EXPENSE,
                                            null,
                                            "Overall Budget",
                                            PeriodOption.THIS_MONTH,
                                            null
                                        )
                                    }
                                )
                            }
                            budgetStatus.categoryStatuses.forEach { catStatus ->
                                BudgetProgressRow(
                                    label = catStatus.categoryName,
                                    spent = catStatus.spent,
                                    budget = catStatus.budget,
                                    currencyCode = currencyCode,
                                    colorHex = catStatus.colorHex,
                                    onClick = {
                                        onOpenTransactions(
                                            TransactionType.EXPENSE,
                                            catStatus.categoryId,
                                            catStatus.categoryName,
                                            PeriodOption.THIS_MONTH,
                                            null
                                        )
                                    }
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
                                    Row {
                                        TextButton(onClick = viewModel::generateInsights) {
                                            Text(if (insights.isEmpty()) "Generate" else "Regenerate")
                                        }
                                        if (insights.isNotEmpty()) {
                                            IconButton(onClick = viewModel::dismissInsights) {
                                                Icon(Icons.Filled.Close, contentDescription = "Dismiss insights")
                                            }
                                        }
                                    }
                                }
                            }
                            if (insights.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    insights.forEach { card -> InsightCardItem(card) }
                                }
                            }
                        }
                    }
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
                            onCategoryClick = { spend ->
                                onOpenTransactions(
                                    TransactionType.EXPENSE,
                                    spend.categoryId,
                                    spend.categoryName,
                                    state.periodOption,
                                    state.customRange
                                )
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A single AI-generated insight, styled as a compact stat card: a tone-colored accent bar, a
 * muted label, the headline figure in a large tone-colored weight, and one line of context. */
@Composable
private fun InsightCardItem(card: InsightCard) {
    val accentColor = when (card.tone) {
        InsightTone.POSITIVE -> IncomeGreen
        InsightTone.WARNING -> ExpenseRed
        InsightTone.NEUTRAL -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accentColor)
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = card.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = card.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = card.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
