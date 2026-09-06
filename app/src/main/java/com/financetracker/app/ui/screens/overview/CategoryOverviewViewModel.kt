package com.financetracker.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.ui.components.BarChartEntry
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption
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
    val selectedKey: String? = null,
    val transactions: List<TransactionWithDetails> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0
)

class CategoryOverviewViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _periodOption = MutableStateFlow(PeriodOption.THIS_MONTH)
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    private val _groupBy = MutableStateFlow(GroupByOption.MAIN_CATEGORY)
    private val _selectedKey = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoryOverviewUiState> = combine(
        repository.observeTransactions(),
        _periodOption,
        _customRange,
        _groupBy,
        _selectedKey
    ) { transactions, periodOption, customRange, groupBy, selectedKey ->
        val (from, to) = periodRange(periodOption, customRange)
        val inPeriod = transactions.filter { it.date >= from && it.date < to }

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

        val filteredTransactions = (
            if (selectedKey != null) inPeriod.filter { groupKeyOf(it, groupBy) == selectedKey } else inPeriod
            ).sortedByDescending { it.date }

        CategoryOverviewUiState(
            periodOption = periodOption,
            customRange = customRange,
            groupBy = groupBy,
            entries = entries,
            selectedKey = selectedKey,
            transactions = filteredTransactions,
            totalIncome = inPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            totalExpense = inPeriod.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryOverviewUiState())

    private fun groupKeyOf(tx: TransactionWithDetails, groupBy: GroupByOption): String = when (groupBy) {
        GroupByOption.ACCOUNT -> tx.accountName
        GroupByOption.MAIN_CATEGORY -> tx.mainCategoryName ?: "Uncategorized"
        GroupByOption.CATEGORY -> tx.categoryName ?: "Uncategorized"
    }

    fun selectPeriod(option: PeriodOption) {
        _periodOption.value = option
        _selectedKey.value = null
    }

    fun selectCustomRange(start: Long, endExclusive: Long) {
        _customRange.value = start to endExclusive
        _periodOption.value = PeriodOption.CUSTOM
        _selectedKey.value = null
    }

    fun selectGroupBy(option: GroupByOption) {
        _groupBy.value = option
        _selectedKey.value = null
    }

    fun toggleSelection(key: String?) {
        _selectedKey.value = if (_selectedKey.value == key) null else key
    }
}
