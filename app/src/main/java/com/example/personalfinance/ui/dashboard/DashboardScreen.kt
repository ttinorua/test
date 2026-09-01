package com.example.personalfinance.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personalfinance.ui.common.TransactionRow
import com.example.personalfinance.ui.common.formatAmount
import com.example.personalfinance.ui.common.formatMonth
import com.example.personalfinance.ui.theme.ExpenseRed
import com.example.personalfinance.ui.theme.IncomeGreen

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onTransactionClick: (Long) -> Unit,
    onSeeAllClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            BalanceHeader(
                balance = state.balance,
                monthIncome = state.monthIncome,
                monthExpense = state.monthExpense
            )
        }

        if (state.expenseByCategory.isNotEmpty()) {
            item {
                SpendingBreakdownCard(state = state)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent transactions", style = MaterialTheme.typography.titleLarge)
                Text(
                    "See all",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onSeeAllClick)
                        .padding(4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.recentTransactions.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "No transactions yet. Tap + to add your first one.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.recentTransactions, key = { it.transaction.id }) { item ->
                TransactionRow(
                    item = item,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTransactionClick(item.transaction.id) }
                )
            }
        }
    }
}

@Composable
private fun BalanceHeader(balance: Double, monthIncome: Double, monthExpense: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp)
    ) {
        Text(
            "Total balance",
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            formatAmount(balance),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryPill(label = "Income this month", amount = monthIncome, color = IncomeGreen)
            SummaryPill(label = "Expense this month", amount = monthExpense, color = ExpenseRed)
        }
    }
}

@Composable
private fun SummaryPill(label: String, amount: Double, color: Color) {
    Column {
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            formatAmount(amount),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun SpendingBreakdownCard(state: DashboardUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Spending by category — ${formatMonth(System.currentTimeMillis())}",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = state.expenseByCategory,
                    modifier = Modifier.size(120.dp)
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    state.expenseByCategory.take(5).forEach { slice ->
                        LegendRow(
                            color = Color(slice.category.color),
                            label = slice.category.name,
                            amount = slice.amount
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, amount: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(formatAmount(amount), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DonutChart(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.22f
        var startAngle = -90f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        slices.forEach { slice ->
            val sweep = slice.fraction * 360f
            drawArc(
                color = Color(slice.category.color),
                startAngle = startAngle,
                sweepAngle = sweep.coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}
