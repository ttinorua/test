package com.financetracker.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.ui.components.BarChartEntry
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption
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
    val totalExpense: Double = 0.0
)

private data class OverviewFilters(
    val periodOption: PeriodOption,
    val customRange: Pair<Long, Long>?,
    val groupBy: GroupByOption
)

class CategoryOverviewViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _periodOption = MutableStateFlow(PeriodOption.THIS_MONTH)
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    private val _groupBy = MutableStateFlow(GroupByOption.MAIN_CATEGORY)

    val uiState: StateFlow<CategoryOverviewUiState> = combine(
        repository.observeTransactions(),
        combine(_periodOption, _customRange, _groupBy) { periodOption, customRange, groupBy ->
            OverviewFilters(periodOption, customRange, groupBy)
        },
        BudgetSettings.shiftSalaryToNextMonth
    ) { transactions, filters, shiftSalary ->
        val (periodOption, customRange, groupBy) = filters
        val (from, to) = periodRange(periodOption, customRange)
        val inPeriod = transactions.filter {
            val effectiveDate =
                effectiveReportingDate(it.date, it.type, it.mainCategoryName, it.categoryName, shiftSalary)
            effectiveDate >= from && effectiveDate < to
        }

        val entries = inPeriod
            .groupBy { groupKeyOf(it, groupBy) }
            .map { (key, txs) ->
                BarChartEntry(
                    key = key,
                    label = key,
                    income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                    expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.income + it.expense }

        CategoryOverviewUiState(
            periodOption = periodOption,
            customRange = customRange,
            groupBy = groupBy,
            entries = entries,
            totalIncome = inPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            totalExpense = inPeriod.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
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
}
