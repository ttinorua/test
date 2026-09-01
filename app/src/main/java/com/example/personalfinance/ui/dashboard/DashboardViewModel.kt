package com.example.personalfinance.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalfinance.data.Category
import com.example.personalfinance.data.FinanceRepository
import com.example.personalfinance.data.TransactionType
import com.example.personalfinance.data.TransactionWithCategory
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CategorySlice(
    val category: Category,
    val amount: Double,
    val fraction: Float
)

data class DashboardUiState(
    val balance: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthExpense: Double = 0.0,
    val expenseByCategory: List<CategorySlice> = emptyList(),
    val recentTransactions: List<TransactionWithCategory> = emptyList(),
    val isLoading: Boolean = true
)

class DashboardViewModel(private val repository: FinanceRepository) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = repository.transactionsWithCategory
        .map { transactions -> buildState(transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun buildState(transactions: List<TransactionWithCategory>): DashboardUiState {
        val balance = transactions.sumOf {
            if (it.transaction.type == TransactionType.INCOME) it.transaction.amount else -it.transaction.amount
        }

        val zone = ZoneId.systemDefault()
        val now = YearMonth.now(zone)
        val monthTransactions = transactions.filter {
            val date = Instant.ofEpochMilli(it.transaction.date).atZone(zone)
            YearMonth.from(date) == now
        }

        val monthIncome = monthTransactions
            .filter { it.transaction.type == TransactionType.INCOME }
            .sumOf { it.transaction.amount }
        val monthExpense = monthTransactions
            .filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amount }

        val expenseGroups = monthTransactions
            .filter { it.transaction.type == TransactionType.EXPENSE && it.category != null }
            .groupBy { it.category!! }
            .mapValues { (_, txns) -> txns.sumOf { it.transaction.amount } }
            .toList()
            .sortedByDescending { it.second }

        val expenseByCategory = expenseGroups.map { (category, amount) ->
            CategorySlice(
                category = category,
                amount = amount,
                fraction = if (monthExpense > 0) (amount / monthExpense).toFloat() else 0f
            )
        }

        return DashboardUiState(
            balance = balance,
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            expenseByCategory = expenseByCategory,
            recentTransactions = transactions.take(5),
            isLoading = false
        )
    }
}
