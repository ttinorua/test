package com.financetracker.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.groupKeyOf
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GroupTransactionsUiState(
    val groupLabel: String = "",
    val transactions: List<TransactionWithDetails> = emptyList()
)

/** Shows every transaction in [key]'s [groupBy] bucket for the period the Spending tab was on. */
class GroupTransactionsViewModel(
    repository: FinanceRepository,
    groupBy: GroupByOption,
    key: String,
    periodOption: PeriodOption,
    customRange: Pair<Long, Long>?
) : ViewModel() {

    val uiState: StateFlow<GroupTransactionsUiState> = combine(
        repository.observeTransactions(),
        BudgetSettings.shiftSalaryToNextMonth
    ) { transactions, shiftSalary ->
        val (from, to) = periodRange(periodOption, customRange)
        val filtered = transactions.filter {
            val effectiveDate =
                effectiveReportingDate(it.date, it.type, it.mainCategoryName, it.categoryName, shiftSalary)
            effectiveDate >= from && effectiveDate < to && groupKeyOf(it, groupBy) == key
        }.sortedByDescending { it.date }

        GroupTransactionsUiState(groupLabel = key, transactions = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupTransactionsUiState(groupLabel = key))
}
