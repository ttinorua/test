package com.financetracker.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financetracker.app.ui.components.CategoryBreakdownList
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.SummaryCard
import com.financetracker.app.ui.components.TransactionRow
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Finance Tracker") }) }
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
                    amount = Formatters.currency(state.netBalance)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "${Formatters.currentMonthLabel()} Income",
                        amount = Formatters.currency(state.monthlyIncome),
                        valueColor = IncomeGreen
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "${Formatters.currentMonthLabel()} Expenses",
                        amount = Formatters.currency(state.monthlyExpense),
                        valueColor = ExpenseRed
                    )
                }
            }
            item {
                Text(text = "Spending by category", style = MaterialTheme.typography.titleMedium)
            }
            item {
                if (state.categoryBreakdown.isEmpty()) {
                    EmptyState(message = "No expenses recorded this month yet.")
                } else {
                    Card {
                        CategoryBreakdownList(
                            categories = state.categoryBreakdown,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            item {
                Text(text = "Recent transactions", style = MaterialTheme.typography.titleMedium)
            }
            if (state.recentTransactions.isEmpty()) {
                item { EmptyState(message = "No transactions yet. Add one or import a spreadsheet.") }
            } else {
                items(state.recentTransactions, key = { it.id }) { tx ->
                    Card { TransactionRow(transaction = tx, onClick = {}, onLongClick = {}, modifier = Modifier.padding(horizontal = 12.dp)) }
                }
            }
        }
    }
}
