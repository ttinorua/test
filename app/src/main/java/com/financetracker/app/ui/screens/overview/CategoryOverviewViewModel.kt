package com.financetracker.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.ui.components.BarChartEntry
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.countsTowardSpending
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.groupKeyOf
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CategoryOverviewUiState(
    val periodOption: PeriodOption = PeriodOption.THIS_MONTH,
    val customRange: Pair<Long, Long>? = null,
    val groupBy: GroupByOption = GroupByOption.MAIN_CATEGORY,
    val entries: List<BarChartEntry> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: Long? = null
)

private data class OverviewFilters(
    val periodOption: PeriodOption,
    val customRange: Pair<Long, Long>?,
    val groupBy: GroupByOption,
    val selectedAccountId: Long?
)

class CategoryOverviewViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _periodOption = MutableStateFlow(PeriodOption.THIS_MONTH)
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    private val _groupBy = MutableStateFlow(GroupByOption.MAIN_CATEGORY)
    private val _selectedAccountId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<CategoryOverviewUiState> = combine(
        repository.observeTransactions(),
        repository.observeAccounts(),
        combine(
            _periodOption,
            _customRange,
            _groupBy,
            _selectedAccountId
        ) { periodOption, customRange, groupBy, selectedAccountId ->
            OverviewFilters(periodOption, customRange, groupBy, selectedAccountId)
        },
        BudgetSettings.shiftSalaryToNextMonth,
        BudgetSettings.excludeTransfersFromSpending
    ) { allTransactions, accounts, filters, shiftSalary, excludeTransfers ->
        val (periodOption, customRange, groupBy, selectedAccountId) = filters
        val transactions = if (selectedAccountId != null) {
            allTransactions.filter { it.accountId == selectedAccountId }
        } else {
            allTransactions
        }
        val (from, to) = periodRange(periodOption, customRange)
        val inPeriod = transactions.filter {
            val effectiveDate =
                effectiveReportingDate(it.date, it.type, it.mainCategoryName, it.categoryName, shiftSalary)
            effectiveDate >= from && effectiveDate < to
        }

        fun countsAsExpense(tx: TransactionWithDetails) =
            tx.type == TransactionType.EXPENSE &&
                countsTowardSpending(tx.type, tx.mainCategoryName, tx.categoryName, excludeTransfers)

        val entries = inPeriod
            .groupBy { groupKeyOf(it, groupBy) }
            .map { (key, txs) ->
                BarChartEntry(
                    key = key,
                    label = key,
                    income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                    expense = txs.filter { countsAsExpense(it) }.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.income + it.expense }

        CategoryOverviewUiState(
            periodOption = periodOption,
            customRange = customRange,
            groupBy = groupBy,
            entries = entries,
            totalIncome = inPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            totalExpense = inPeriod.filter { countsAsExpense(it) }.sumOf { it.amount },
            accounts = accounts,
            selectedAccountId = selectedAccountId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryOverviewUiState())

    fun selectPeriod(option: PeriodOption) {
        _periodOption.value = option
    }

    fun selectCustomRange(start: Long, endExclusive: Long) {
        _customRange.value = start to endExclusive
        _periodOption.value = PeriodOption.CUSTOM
    }

    fun selectGroupBy(option: GroupByOption) {
        _groupBy.value = option
    }

    fun selectAccount(accountId: Long?) {
        _selectedAccountId.value = accountId
    }
}
