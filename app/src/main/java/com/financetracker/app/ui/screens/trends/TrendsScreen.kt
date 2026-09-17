package com.financetracker.app.ui.screens.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.IncomeExpenseTrendChart
import com.financetracker.app.ui.components.NetTrendChart
import com.financetracker.app.ui.components.SummaryCard
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(viewModel: TrendsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val currencyCode by CurrencySettings.currencyCode.collectAsState()
    val hasData = state.trends.any { it.income != 0.0 || it.expense != 0.0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trends") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrendsWindow.entries.forEach { window ->
                        FilterChip(
                            selected = state.window == window,
                            onClick = { viewModel.selectWindow(window) },
                            label = { Text(window.label) }
                        )
                    }
                }
            }
            if (!hasData) {
                item { EmptyState(message = "No transactions in this period yet.") }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Income vs. expense", style = MaterialTheme.typography.titleMedium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                LegendDot(IncomeGreen)
                                Text(
                                    "Income",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 6.dp, end = 16.dp)
                                )
                                LegendDot(ExpenseRed)
                                Text(
                                    "Expense",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            IncomeExpenseTrendChart(
                                trends = state.trends,
                                formatValue = { Formatters.currency(it, currencyCode) }
                            )
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Net · income − expense", style = MaterialTheme.typography.titleMedium)
                            NetTrendChart(trends = state.trends, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryCard(
                            modifier = Modifier.weight(1f),
                            title = "Avg. income",
                            amount = Formatters.currency(state.avgIncome, currencyCode),
                            valueColor = IncomeGreen,
                            amountStyle = MaterialTheme.typography.titleMedium,
                            contentPadding = 12.dp
                        )
                        SummaryCard(
                            modifier = Modifier.weight(1f),
                            title = "Avg. expense",
                            amount = Formatters.currency(state.avgExpense, currencyCode),
                            valueColor = ExpenseRed,
                            amountStyle = MaterialTheme.typography.titleMedium,
                            contentPadding = 12.dp
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.bestMonth?.let { best ->
                            SummaryCard(
                                modifier = Modifier.weight(1f),
                                title = "Best month · ${best.monthLabel}",
                                amount = Formatters.currency(best.net, currencyCode),
                                valueColor = if (best.net >= 0) IncomeGreen else ExpenseRed,
                                amountStyle = MaterialTheme.typography.titleMedium,
                                contentPadding = 12.dp
                            )
                        }
                        state.worstMonth?.let { worst ->
                            SummaryCard(
                                modifier = Modifier.weight(1f),
                                title = "Worst month · ${worst.monthLabel}",
                                amount = Formatters.currency(worst.net, currencyCode),
                                valueColor = if (worst.net >= 0) IncomeGreen else ExpenseRed,
                                amountStyle = MaterialTheme.typography.titleMedium,
                                contentPadding = 12.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}
