package com.financetracker.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.CategorySpend
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MainCategoryGroup(
    val mainCategory: String,
    val total: Double,
    val subcategories: List<CategorySpend>
)

data class CategoryOverviewUiState(
    val periodOption: PeriodOption = PeriodOption.THIS_MONTH,
    val customRange: Pair<Long, Long>? = null,
    val groups: List<MainCategoryGroup> = emptyList(),
    val totalExpense: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryOverviewViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _periodOption = MutableStateFlow(PeriodOption.THIS_MONTH)
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)

    val uiState: StateFlow<CategoryOverviewUiState> = combine(_periodOption, _customRange) { option, range ->
        option to range
    }.flatMapLatest { (option, range) ->
        val (from, to) = periodRange(option, range)
        repository.observeExpenseByCategoryBetween(from, to).map { spends ->
            val groups = spends
                .groupBy { it.mainCategory }
                .map { (mainCategory, subs) ->
                    MainCategoryGroup(
                        mainCategory = mainCategory,
                        total = subs.sumOf { it.total },
                        subcategories = subs.sortedByDescending { it.total }
                    )
                }
                .sortedByDescending { it.total }
            CategoryOverviewUiState(
                periodOption = option,
                customRange = range,
                groups = groups,
                totalExpense = groups.sumOf { it.total }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryOverviewUiState())

    fun selectPeriod(option: PeriodOption) {
        _periodOption.value = option
    }

    fun selectCustomRange(start: Long, endExclusive: Long) {
        _customRange.value = start to endExclusive
        _periodOption.value = PeriodOption.CUSTOM
    }
}
