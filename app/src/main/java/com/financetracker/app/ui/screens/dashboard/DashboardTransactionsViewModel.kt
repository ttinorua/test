package com.financetracker.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardTransactionsUiState(
    val label: String = "",
    val transactions: List<TransactionWithDetails> = emptyList()
)

/**
 * Shows every transaction in the dashboard's current period matching [type] and/or
 * [categoryId] (either or both null means "no filter on that field").
 */
class DashboardTransactionsViewModel(
    repository: FinanceRepository,
    type: TransactionType?,
    categoryId: Long?,
    label: String,
    periodOption: PeriodOption,
    customRange: Pair<Long, Long>?
) : ViewModel() {

    val uiState: StateFlow<DashboardTransactionsUiState> = combine(
        repository.observeTransactions(),
        BudgetSettings.shiftSalaryToNextMonth
    ) { transactions, shiftSalary ->
        val (from, to) = periodRange(periodOption, customRange)
        val filtered = transactions.filter { tx ->
            val effectiveDate =
                effectiveReportingDate(tx.date, tx.type, tx.mainCategoryName, tx.categoryName, shiftSalary)
            val inPeriod = effectiveDate >= from && effectiveDate < to
            val matchesType = type == null || tx.type == type
            val matchesCategory = categoryId == null || tx.categoryId == categoryId
            inPeriod && matchesType && matchesCategory
        }.sortedByDescending { it.date }

        DashboardTransactionsUiState(label = label, transactions = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardTransactionsUiState(label = label))
}
