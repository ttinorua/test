package com.financetracker.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.CategorySpend
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.currentMonthRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val netBalance: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    val recentTransactions: List<TransactionWithDetails> = emptyList()
)

class DashboardViewModel(private val repository: FinanceRepository) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = run {
        val (monthStart, monthEnd) = currentMonthRange()
        combine(
            repository.observeNetBalance(),
            repository.observeIncomeBetween(monthStart, monthEnd),
            repository.observeExpenseBetween(monthStart, monthEnd),
            repository.observeExpenseByCategoryBetween(monthStart, monthEnd),
            repository.observeRecentTransactions(5)
        ) { balance, income, expense, categories, recent ->
            DashboardUiState(
                netBalance = balance,
                monthlyIncome = income,
                monthlyExpense = expense,
                categoryBreakdown = categories,
                recentTransactions = recent
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
    }
}
